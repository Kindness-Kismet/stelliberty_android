// Package overrides 按 PC 版的规则把 YAML / JavaScript 覆写依次套到订阅配置上。
package overrides

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/dop251/goja"
	"go.yaml.in/yaml/v3"
)

const (
	FormatYAML = "yaml"
	FormatJS   = "js"

	jsTimeout     = 2 * time.Second
	jsOutputLimit = 1024 * 1024
	// 展开别名时的节点上限，挡住「十亿笑声」式的别名炸弹；应用内校验也走这里，爆内存会带走整个应用。
	maxExpandedNodes = 1 << 20
)

// console 只兼容常见日志调用，不输出参数，避免把订阅内容写进日志。
const jsConsolePolyfill = `
globalThis.console = globalThis.console || {};
globalThis.console.log = globalThis.console.log || function() {};
globalThis.console.info = globalThis.console.info || function() {};
globalThis.console.warn = globalThis.console.warn || function() {};
globalThis.console.error = globalThis.console.error || function() {};
globalThis.console.debug = globalThis.console.debug || function() {};
`

type Spec struct {
	Name   string `json:"name"`
	Format string `json:"format"`
	Path   string `json:"path"`
}

// Transform 是运行时与应用内校验共用的订阅变换，顺序同 PC：先按清单套覆写，再处理链式代理，最后套规则覆写。
type Transform struct {
	Overrides             []Spec        `json:"overrides"`
	DisabledBuiltinChains []string      `json:"disabledBuiltinChainProxyNames"`
	CustomChains          []CustomChain `json:"customChainProxies"`
	Rules                 *RuleOverride `json:"ruleOverride"`
}

func LoadTransform(path string) (*Transform, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read transform: %w", err)
	}
	var transform Transform
	if err := json.Unmarshal(data, &transform); err != nil {
		return nil, fmt.Errorf("parse transform: %w", err)
	}
	return &transform, nil
}

func (t *Transform) Apply(config []byte) ([]byte, error) {
	config, err := Apply(config, t.Overrides)
	if err != nil {
		return nil, err
	}
	config, err = ApplyChains(config, t.DisabledBuiltinChains, t.CustomChains)
	if err != nil {
		return nil, err
	}
	return ApplyRules(config, t.Rules)
}

// Apply 按清单顺序套用覆写，前一个的输出是后一个的输入。
func Apply(config []byte, specs []Spec) ([]byte, error) {
	for _, spec := range specs {
		content, err := os.ReadFile(spec.Path)
		if err != nil {
			return nil, fmt.Errorf("override %s: %w", spec.Name, err)
		}
		switch spec.Format {
		case FormatYAML:
			config, err = ApplyYAML(config, content)
		case FormatJS:
			config, err = ApplyJS(config, string(content))
		default:
			err = fmt.Errorf("unknown format %q", spec.Format)
		}
		if err != nil {
			return nil, fmt.Errorf("override %s: %w", spec.Name, err)
		}
	}
	return config, nil
}

// === YAML ===

func ApplyYAML(base, override []byte) ([]byte, error) {
	doc, root, err := parseRoot(base)
	if err != nil {
		return nil, fmt.Errorf("parse base config: %w", err)
	}
	var overDoc yaml.Node
	if err := yaml.Unmarshal(override, &overDoc); err != nil {
		return nil, fmt.Errorf("parse override: %w", err)
	}
	// 空文件与 null 不改动配置：「新建」出来的空白覆写被选中时保持原样。
	if len(overDoc.Content) == 0 || isNull(overDoc.Content[0]) {
		return base, nil
	}
	// 两边都先展开别名，与 PC 解析成值后再合并一致；否则替换掉带锚点的节点后，别处的引用会悬空。
	budget := maxExpandedNodes
	target, err := expand(root, &budget)
	if err != nil {
		return nil, err
	}
	over, err := expand(overDoc.Content[0], &budget)
	if err != nil {
		return nil, err
	}
	if over.Kind != yaml.MappingNode {
		return nil, errors.New("override root must be a mapping")
	}
	if err := mergeMapping(target, over); err != nil {
		return nil, err
	}
	doc.Content[0] = target
	return encode(doc)
}

func parseRoot(data []byte) (*yaml.Node, *yaml.Node, error) {
	var doc yaml.Node
	if err := yaml.Unmarshal(data, &doc); err != nil {
		return nil, nil, err
	}
	if len(doc.Content) == 0 || doc.Content[0].Kind != yaml.MappingNode {
		return nil, nil, errors.New("root must be a mapping")
	}
	return &doc, doc.Content[0], nil
}

// 键的前后缀语义与 PC 相同，按此顺序判断：<key> 按字面写入，key! 强制替换，+key 数组前插，key+ 数组追加，其余深度合并。
func mergeMapping(target, over *yaml.Node) error {
	for i := 0; i+1 < len(over.Content); i += 2 {
		key, value := over.Content[i], over.Content[i+1]
		if key.Kind != yaml.ScalarNode || key.Tag != "!!str" {
			setValue(target, key, value)
			continue
		}
		name := key.Value
		switch {
		case len(name) >= 2 && strings.HasPrefix(name, "<") && strings.HasSuffix(name, ">"):
			setValue(target, strNode(name[1:len(name)-1]), value)
		case strings.HasSuffix(name, "!"):
			setValue(target, strNode(strings.TrimSuffix(name, "!")), value)
		case strings.HasPrefix(name, "+"):
			if err := concatInto(target, name[1:], value, true); err != nil {
				return err
			}
		case strings.HasSuffix(name, "+"):
			if err := concatInto(target, strings.TrimSuffix(name, "+"), value, false); err != nil {
				return err
			}
		default:
			index := findKey(target, name)
			if index < 0 {
				target.Content = append(target.Content, strNode(name), value)
				continue
			}
			merged, err := deepMerge(target.Content[index+1], value)
			if err != nil {
				return err
			}
			target.Content[index+1] = merged
		}
	}
	return nil
}

func deepMerge(base, over *yaml.Node) (*yaml.Node, error) {
	if base.Kind != yaml.MappingNode || over.Kind != yaml.MappingNode {
		return over, nil
	}
	if err := mergeMapping(base, over); err != nil {
		return nil, err
	}
	return base, nil
}

func concatInto(target *yaml.Node, name string, value *yaml.Node, prepend bool) error {
	index := findKey(target, name)
	var existing *yaml.Node
	if index >= 0 {
		existing = target.Content[index+1]
	}
	head, err := sequenceItems(existing)
	if err != nil {
		return err
	}
	tail, err := sequenceItems(value)
	if err != nil {
		return err
	}
	if prepend {
		head, tail = tail, head
	}
	combined := &yaml.Node{Kind: yaml.SequenceNode, Tag: "!!seq", Content: append(head, tail...)}
	if index >= 0 {
		target.Content[index+1] = combined
	} else {
		target.Content = append(target.Content, strNode(name), combined)
	}
	return nil
}

func sequenceItems(node *yaml.Node) ([]*yaml.Node, error) {
	if node == nil || isNull(node) {
		return nil, nil
	}
	if node.Kind != yaml.SequenceNode {
		return nil, fmt.Errorf("array append target must be a sequence or null, got %s", node.Tag)
	}
	return append([]*yaml.Node(nil), node.Content...), nil
}

func setValue(target, key, value *yaml.Node) {
	if key.Kind == yaml.ScalarNode {
		for i := 0; i+1 < len(target.Content); i += 2 {
			k := target.Content[i]
			if k.Kind == yaml.ScalarNode && k.Tag == key.Tag && k.Value == key.Value {
				target.Content[i+1] = value
				return
			}
		}
	}
	target.Content = append(target.Content, key, value)
}

func findKey(mapping *yaml.Node, name string) int {
	for i := 0; i+1 < len(mapping.Content); i += 2 {
		k := mapping.Content[i]
		if k.Kind == yaml.ScalarNode && k.Tag == "!!str" && k.Value == name {
			return i
		}
	}
	return -1
}

// expand 深拷贝并展开别名，去掉锚点名，结果可以挪进另一份文档而不留悬空引用。
func expand(node *yaml.Node, budget *int) (*yaml.Node, error) {
	return expandNode(node, budget, make(map[*yaml.Node]bool))
}

func expandNode(node *yaml.Node, budget *int, visiting map[*yaml.Node]bool) (*yaml.Node, error) {
	for node.Kind == yaml.AliasNode {
		node = node.Alias
	}
	if visiting[node] {
		return nil, errors.New("cyclic YAML alias")
	}
	visiting[node] = true
	defer delete(visiting, node)
	*budget--
	if *budget < 0 {
		return nil, errors.New("too many nodes after expanding aliases")
	}
	copied := *node
	copied.Anchor = ""
	if len(node.Content) > 0 {
		copied.Content = make([]*yaml.Node, len(node.Content))
		for i, child := range node.Content {
			c, err := expandNode(child, budget, visiting)
			if err != nil {
				return nil, err
			}
			copied.Content[i] = c
		}
	}
	return &copied, nil
}

func isNull(node *yaml.Node) bool {
	return node.Kind == yaml.ScalarNode && node.Tag == "!!null"
}

func strNode(value string) *yaml.Node {
	return &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: value}
}

func encode(doc *yaml.Node) ([]byte, error) {
	var buf bytes.Buffer
	enc := yaml.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(doc); err != nil {
		return nil, fmt.Errorf("serialize config: %w", err)
	}
	if err := enc.Close(); err != nil {
		return nil, fmt.Errorf("serialize config: %w", err)
	}
	return buf.Bytes(), nil
}

// === JavaScript ===

// 脚本拼接方式与 PC 相同：顶层的 let / const main 也能被调用，var proxies 预先声明供旧脚本使用。
func ApplyJS(base []byte, script string) ([]byte, error) {
	_, root, err := parseRoot(base)
	if err != nil {
		return nil, fmt.Errorf("parse base config: %w", err)
	}
	var input bytes.Buffer
	budget := maxExpandedNodes
	root, err = expand(root, &budget)
	if err != nil {
		return nil, fmt.Errorf("expand config aliases: %w", err)
	}
	budget = maxExpandedNodes
	if err := writeJSON(&input, root, &budget); err != nil {
		return nil, fmt.Errorf("convert config to JSON: %w", err)
	}

	vm := goja.New()
	timer := time.AfterFunc(jsTimeout, func() { vm.Interrupt("JS override timed out") })
	defer timer.Stop()
	if err := vm.Set("__stellibertyConfig", input.String()); err != nil {
		return nil, err
	}
	value, err := vm.RunString(jsConsolePolyfill + "\nvar proxies;\n" + script +
		"\n;JSON.stringify(main(JSON.parse(__stellibertyConfig))) || '';")
	if err != nil {
		return nil, fmt.Errorf("JS execution failed: %w", err)
	}
	output := value.String()
	if len(output) > jsOutputLimit {
		return nil, errors.New("JS override output is too large")
	}
	if strings.TrimSpace(output) == "" {
		return nil, errors.New("JS override returned an empty config")
	}

	dec := json.NewDecoder(strings.NewReader(output))
	dec.UseNumber()
	node, err := jsonToNode(dec)
	if err != nil {
		return nil, fmt.Errorf("decode JS result: %w", err)
	}
	if node.Kind != yaml.MappingNode {
		return nil, errors.New("JS override returned a non-object config")
	}
	return encode(&yaml.Node{Kind: yaml.DocumentNode, Content: []*yaml.Node{node}})
}

func writeJSON(buf *bytes.Buffer, node *yaml.Node, budget *int) error {
	for node.Kind == yaml.AliasNode {
		node = node.Alias
	}
	*budget--
	if *budget < 0 {
		return errors.New("too many nodes after expanding aliases")
	}
	switch node.Kind {
	case yaml.MappingNode:
		pairs, err := mappingPairs(node)
		if err != nil {
			return err
		}
		buf.WriteByte('{')
		for i, pair := range pairs {
			if i > 0 {
				buf.WriteByte(',')
			}
			writeJSONString(buf, pair[0].Value)
			buf.WriteByte(':')
			if err := writeJSON(buf, pair[1], budget); err != nil {
				return err
			}
		}
		buf.WriteByte('}')
	case yaml.SequenceNode:
		buf.WriteByte('[')
		for i, child := range node.Content {
			if i > 0 {
				buf.WriteByte(',')
			}
			if err := writeJSON(buf, child, budget); err != nil {
				return err
			}
		}
		buf.WriteByte(']')
	case yaml.ScalarNode:
		writeJSONScalar(buf, node)
	default:
		buf.WriteString("null")
	}
	return nil
}

// mappingPairs 按 YAML 语义处理合并键 <<：显式写出的键优先，被合并的键只补缺。
func mappingPairs(node *yaml.Node) ([][2]*yaml.Node, error) {
	var pairs [][2]*yaml.Node
	seen := map[string]bool{}
	var merged []*yaml.Node
	for i := 0; i+1 < len(node.Content); i += 2 {
		key, value := node.Content[i], node.Content[i+1]
		if key.Kind == yaml.ScalarNode && key.Tag == "!!merge" {
			merged = append(merged, value)
			continue
		}
		if key.Kind != yaml.ScalarNode {
			return nil, errors.New("mapping key must be a scalar")
		}
		if !seen[key.Value] {
			seen[key.Value] = true
			pairs = append(pairs, [2]*yaml.Node{key, value})
		}
	}
	for _, source := range merged {
		for source.Kind == yaml.AliasNode {
			source = source.Alias
		}
		sources := []*yaml.Node{source}
		if source.Kind == yaml.SequenceNode {
			sources = source.Content
		}
		for _, s := range sources {
			for s.Kind == yaml.AliasNode {
				s = s.Alias
			}
			if s.Kind != yaml.MappingNode {
				return nil, errors.New("merge key value must be a mapping")
			}
			inner, err := mappingPairs(s)
			if err != nil {
				return nil, err
			}
			for _, pair := range inner {
				if !seen[pair[0].Value] {
					seen[pair[0].Value] = true
					pairs = append(pairs, pair)
				}
			}
		}
	}
	return pairs, nil
}

// 只把规范写法的数字转成 JSON 数字；0123、0x1F 这类保留原文，否则前导零的密码之类会被改写。
func writeJSONScalar(buf *bytes.Buffer, node *yaml.Node) {
	switch node.Tag {
	case "!!null":
		buf.WriteString("null")
		return
	case "!!bool":
		if b, err := strconv.ParseBool(node.Value); err == nil {
			buf.WriteString(strconv.FormatBool(b))
			return
		}
	case "!!int":
		if n, err := strconv.ParseInt(node.Value, 10, 64); err == nil && strconv.FormatInt(n, 10) == node.Value {
			buf.WriteString(node.Value)
			return
		}
	case "!!float":
		if f, err := strconv.ParseFloat(node.Value, 64); err == nil && !isNonFinite(f) {
			buf.WriteString(strconv.FormatFloat(f, 'g', -1, 64))
			return
		}
	}
	writeJSONString(buf, node.Value)
}

func isNonFinite(f float64) bool {
	return f != f || f > 1.7976931348623157e308 || f < -1.7976931348623157e308
}

func writeJSONString(buf *bytes.Buffer, s string) {
	enc := json.NewEncoder(buf)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(s)
	// Encode 会追加换行。
	buf.Truncate(buf.Len() - 1)
}

func jsonToNode(dec *json.Decoder) (*yaml.Node, error) {
	token, err := dec.Token()
	if err != nil {
		return nil, err
	}
	switch t := token.(type) {
	case json.Delim:
		switch t {
		case '{':
			node := &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map"}
			for dec.More() {
				keyToken, err := dec.Token()
				if err != nil {
					return nil, err
				}
				key, _ := keyToken.(string)
				value, err := jsonToNode(dec)
				if err != nil {
					return nil, err
				}
				node.Content = append(node.Content, strNode(key), value)
			}
			if _, err := dec.Token(); err != nil {
				return nil, err
			}
			return node, nil
		case '[':
			node := &yaml.Node{Kind: yaml.SequenceNode, Tag: "!!seq"}
			for dec.More() {
				value, err := jsonToNode(dec)
				if err != nil {
					return nil, err
				}
				node.Content = append(node.Content, value)
			}
			if _, err := dec.Token(); err != nil {
				return nil, err
			}
			return node, nil
		}
	case string:
		return strNode(t), nil
	case json.Number:
		tag := "!!int"
		if strings.ContainsAny(t.String(), ".eE") {
			tag = "!!float"
		}
		return &yaml.Node{Kind: yaml.ScalarNode, Tag: tag, Value: t.String()}, nil
	case bool:
		return &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!bool", Value: strconv.FormatBool(t)}, nil
	case nil:
		return &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!null", Value: "null"}, nil
	}
	return nil, io.ErrUnexpectedEOF
}
