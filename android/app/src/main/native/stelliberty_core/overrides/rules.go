package overrides

import (
	"errors"
	"strings"

	"go.yaml.in/yaml/v3"
)

// 键名与 PC 的 rules/rule_overrides.json 一致，Kotlin 侧直接序列化模型。
type EditableRule struct {
	ID        string `json:"Id"`
	Type      string `json:"Type"`
	Payload   string `json:"Payload"`
	Proxy     string `json:"Proxy"`
	Options   string `json:"Options"`
	IsEnabled bool   `json:"IsEnabled"`
}

type RuleOverride struct {
	CustomRules             []EditableRule `json:"CustomRules"`
	DisabledBuiltinRuleKeys []string       `json:"DisabledBuiltinRuleKeys"`
	RuleOrder               []string       `json:"RuleOrder"`
}

// Key 由运行时同一个函数算出，页面保存的禁用键因此总能对上。
type RuleItem struct {
	Type     string `json:"type"`
	Payload  string `json:"payload"`
	Proxy    string `json:"proxy"`
	Options  string `json:"options"`
	Key      string `json:"key"`
	MatchKey string `json:"matchKey"`
}

// RuleContext 是规则覆写页面的基线：套完覆写与链式代理、尚未套规则覆写的配置。
type RuleContext struct {
	Rules       []RuleItem `json:"rules"`
	ProxyGroups []string   `json:"proxyGroups"`
	Targets     []string   `json:"targets"`
}

// 与 PC RuleKey 的分隔符一致。
const ruleKeySeparator = "\x1f"

const (
	builtinOrderPrefix = "builtin:"
	customOrderPrefix  = "custom:"
)

// 内核认可的内置出站；自定义规则指向其余不存在的名字时整份配置会被拒绝。
var ruleBuiltinTargets = []string{"DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE", "GLOBAL"}

func (r EditableRule) Render() string {
	kind := strings.TrimSpace(r.Type)
	parts := []string{kind}
	if !strings.EqualFold(kind, "MATCH") {
		parts = append(parts, strings.TrimSpace(r.Payload))
	}
	parts = append(parts, strings.TrimSpace(r.Proxy))
	if !blank(r.Options) {
		parts = append(parts, strings.TrimSpace(r.Options))
	}
	return strings.Join(parts, ",")
}

// ApplyRules 按 PC 规则移除禁用的订阅规则、插入自定义规则并按保存的顺序重排。
// 出站目标已不存在的自定义规则跳过，否则内核会拒绝整份配置。
func ApplyRules(config []byte, set *RuleOverride) ([]byte, error) {
	if set == nil || (len(set.CustomRules) == 0 && len(set.DisabledBuiltinRuleKeys) == 0 && len(set.RuleOrder) == 0) {
		return config, nil
	}
	doc, root, err := parseRoot(config)
	if err != nil {
		return config, nil
	}
	budget := maxExpandedNodes
	root, err = expand(root, &budget)
	if err != nil {
		return nil, err
	}
	existing, err := ruleNodes(root)
	if err != nil {
		return nil, err
	}

	disabled := make(map[string]bool, len(set.DisabledBuiltinRuleKeys))
	for _, key := range set.DisabledBuiltinRuleKeys {
		disabled[key] = true
	}
	var builtin []orderedRule
	for _, node := range existing {
		key := parseRuleKey(node.Value)
		if disabled[key] {
			continue
		}
		builtin = append(builtin, orderedRule{id: builtinOrderPrefix + key, node: node})
	}
	targets := ruleTargets(root)
	var custom []orderedRule
	for _, rule := range set.CustomRules {
		if !rule.IsEnabled || !targets[strings.TrimSpace(rule.Proxy)] {
			continue
		}
		custom = append(custom, orderedRule{id: customOrderPrefix + rule.ID, node: strNode(rule.Render())})
	}

	ordered := mergeRuleOrder(builtin, custom, set.RuleOrder)
	nodes := make([]*yaml.Node, len(ordered))
	for i, rule := range ordered {
		nodes[i] = rule.node
	}
	setValue(root, strNode("rules"), sequenceNode(nodes))
	doc.Content[0] = root
	return encode(doc)
}

// RuleContextOf 列出可编辑的订阅规则（同键只留第一条，与运行时按键去重一致）、代理组与全部出站目标。
func RuleContextOf(config []byte) (*RuleContext, error) {
	result := &RuleContext{Rules: []RuleItem{}, ProxyGroups: []string{}, Targets: ruleBuiltinTargets}
	_, root, err := parseRoot(config)
	if err != nil {
		return result, nil
	}
	budget := maxExpandedNodes
	root, err = expand(root, &budget)
	if err != nil {
		return nil, err
	}
	if i := findKey(root, "rules"); i >= 0 && root.Content[i+1].Kind == yaml.SequenceNode {
		seen := make(map[string]bool)
		for _, node := range root.Content[i+1].Content {
			if node.Kind != yaml.ScalarNode {
				continue
			}
			item, ok := parseRuleItem(node.Value)
			if !ok || seen[item.Key] {
				continue
			}
			seen[item.Key] = true
			result.Rules = append(result.Rules, item)
		}
	}
	for _, group := range mappingItems(root, "proxy-groups") {
		if name := scalar(group, "name"); !blank(name) {
			result.ProxyGroups = append(result.ProxyGroups, name)
		}
	}
	result.Targets = ruleTargetNames(root)
	return result, nil
}

type orderedRule struct {
	id   string
	node *yaml.Node
}

func (r orderedRule) isMatch() bool {
	return strings.EqualFold(strings.TrimSpace(strings.SplitN(r.node.Value, ",", 2)[0]), "MATCH")
}

// 与 PC 相同：有保存的顺序时同 id 只留第一条；顺序里没有的订阅规则接在末尾，新自定义规则插到首个 MATCH 前。
func mergeRuleOrder(builtin, custom []orderedRule, order []string) []orderedRule {
	if len(order) == 0 {
		return insertBeforeMatch(builtin, custom)
	}
	combined := append(append([]orderedRule(nil), builtin...), custom...)
	pending := make(map[string]int, len(combined))
	for i, rule := range combined {
		if _, ok := pending[rule.id]; !ok {
			pending[rule.id] = i
		}
	}
	ordered := make([]orderedRule, 0, len(combined))
	for _, id := range order {
		if i, ok := pending[id]; ok {
			ordered = append(ordered, combined[i])
			delete(pending, id)
		}
	}
	var customs []orderedRule
	for i, rule := range combined {
		if j, ok := pending[rule.id]; !ok || j != i {
			continue
		}
		if strings.HasPrefix(rule.id, builtinOrderPrefix) {
			ordered = append(ordered, rule)
		} else {
			customs = append(customs, rule)
		}
	}
	return insertBeforeMatch(ordered, customs)
}

func insertBeforeMatch(rules, inserted []orderedRule) []orderedRule {
	at := len(rules)
	for i, rule := range rules {
		if rule.isMatch() {
			at = i
			break
		}
	}
	result := make([]orderedRule, 0, len(rules)+len(inserted))
	result = append(result, rules[:at]...)
	result = append(result, inserted...)
	return append(result, rules[at:]...)
}

func ruleNodes(root *yaml.Node) ([]*yaml.Node, error) {
	i := findKey(root, "rules")
	if i < 0 || isNull(root.Content[i+1]) {
		return nil, nil
	}
	node := root.Content[i+1]
	if node.Kind != yaml.SequenceNode {
		return nil, errors.New("rules must be a sequence")
	}
	for _, item := range node.Content {
		if item.Kind != yaml.ScalarNode {
			return nil, errors.New("rules must be a sequence of strings")
		}
	}
	return node.Content, nil
}

func ruleTargetNames(root *yaml.Node) []string {
	names := append([]string(nil), ruleBuiltinTargets...)
	seen := make(map[string]bool)
	for _, name := range names {
		seen[name] = true
	}
	for _, key := range []string{"proxies", "proxy-groups"} {
		for _, item := range mappingItems(root, key) {
			if name := scalar(item, "name"); !blank(name) && !seen[name] {
				seen[name] = true
				names = append(names, name)
			}
		}
	}
	return names
}

func ruleTargets(root *yaml.Node) map[string]bool {
	targets := make(map[string]bool)
	for _, name := range ruleTargetNames(root) {
		targets[name] = true
	}
	return targets
}

// 与 PC 页面的规则解析一致；只有 key 改用运行时的切法，保证禁用键与运行时对得上。
func parseRuleItem(rule string) (RuleItem, bool) {
	parts := strings.Split(rule, ",")
	item := RuleItem{Type: rule}
	if len(parts) >= 2 {
		item.Type = strings.TrimSpace(parts[0])
		if strings.EqualFold(item.Type, "MATCH") {
			item.Proxy = strings.TrimSpace(parts[1])
		} else {
			item.Payload = strings.TrimSpace(parts[1])
			if len(parts) >= 3 {
				item.Proxy = strings.TrimSpace(parts[2])
			}
			if len(parts) > 3 {
				options := make([]string, 0, len(parts)-3)
				for _, part := range parts[3:] {
					options = append(options, strings.TrimSpace(part))
				}
				item.Options = strings.Join(options, ",")
			}
		}
	}
	if blank(item.Type) || (!strings.EqualFold(item.Type, "MATCH") && blank(item.Payload)) {
		return item, false
	}
	item.Key = parseRuleKey(rule)
	item.MatchKey = RuleMatchKey(item.Type, item.Payload)
	return item, true
}

// 与 PC 运行时相同：按逗号直接切分，逻辑规则也不特殊处理，只要求页面与运行时切法一致。
func parseRuleKey(rule string) string {
	parts := strings.Split(rule, ",")
	if len(parts) < 2 {
		return RuleKey(parts[0], "", "", "")
	}
	kind := strings.TrimSpace(parts[0])
	if strings.EqualFold(kind, "MATCH") {
		return RuleKey(kind, "", parts[1], strings.Join(parts[2:], ","))
	}
	proxy, options := "", ""
	if len(parts) > 2 {
		proxy = parts[2]
	}
	if len(parts) > 3 {
		options = strings.Join(parts[3:], ",")
	}
	return RuleKey(kind, parts[1], proxy, options)
}

func RuleKey(kind, payload, proxy, options string) string {
	return strings.Join([]string{
		normalizeRulePart(kind), normalizeRulePart(payload), normalizeRulePart(proxy), normalizeRulePart(options),
	}, ruleKeySeparator)
}

// 查重只看类型与匹配内容，同 PC 的 RuleKey.CreateMatch。
func RuleMatchKey(kind, payload string) string {
	return normalizeRulePart(kind) + ruleKeySeparator + normalizeRulePart(payload)
}

func normalizeRulePart(value string) string {
	return strings.ToUpper(strings.Join(strings.Fields(value), " "))
}
