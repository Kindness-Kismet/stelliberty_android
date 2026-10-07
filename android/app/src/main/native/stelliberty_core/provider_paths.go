package main

import (
	"bytes"
	"fmt"
	"path/filepath"
	"sort"
	"strings"

	"github.com/metacubex/mihomo/common/utils"
	"github.com/metacubex/mihomo/config"
	"go.yaml.in/yaml/v3"
)

// http provider 的缓存统一放进工作目录的 providers/：导入预下载、应用内校验与运行时读写同一文件。
// cmfa 构建跳过了安全路径检查，配置里的绝对路径与 ../ 也要收进该目录，ROOT 内核才不会写到别处。
func providerCachePath(providersDir, kind string, provider map[string]any) string {
	if vehicle, _ := provider["type"].(string); vehicle != "http" {
		return ""
	}
	url, _ := provider["url"].(string)
	if url == "" {
		return ""
	}
	// 按根目录解析再去掉前导分隔符：保留相对目录结构，不同目录下的同名文件不会互相覆盖。
	name, _ := provider["path"].(string)
	name = strings.TrimPrefix(filepath.Join("/", name), "/")
	if name == "" {
		name = filepath.Join(kind, utils.MakeHash([]byte(url)).String())
	}
	return filepath.Join(providersDir, name)
}

func patchProvidersPath(cfg *config.RawConfig, providersDir string) {
	forEachProviders(cfg, func(_, _ int, _ string, provider map[string]any, kind string) {
		if path := providerCachePath(providersDir, kind, provider); path != "" {
			provider["path"] = path
		}
	})
}

func forEachProviders(cfg *config.RawConfig, fn func(index, total int, key string, provider map[string]any, kind string)) {
	total := len(cfg.ProxyProvider) + len(cfg.RuleProvider)
	idx := 0
	for k, v := range cfg.ProxyProvider {
		fn(idx, total, k, v, "proxies")
		idx++
	}
	for k, v := range cfg.RuleProvider {
		fn(idx, total, k, v, "rules")
		idx++
	}
}

// 运行时只能把配置文本交给内核，所以按解析后的值算路径（合并键与锚点已展开），再写回 YAML 节点。
// 节点里找不到的 provider（来自合并键）补成显式条目，显式键优先于合并进来的同名键。
func patchProviderPathsYAML(configBytes []byte, providersDir string) ([]byte, error) {
	rawCfg, err := config.UnmarshalRawConfig(configBytes)
	if err != nil {
		return nil, err
	}
	sections := map[string]map[string]map[string]any{
		"proxy-providers": rawCfg.ProxyProvider,
		"rule-providers":  rawCfg.RuleProvider,
	}
	pending := map[string][]string{}
	forEachProviders(rawCfg, func(_, _ int, name string, provider map[string]any, kind string) {
		if path := providerCachePath(providersDir, kind, provider); path != "" {
			provider["path"] = path
			section := "rule-providers"
			if kind == "proxies" {
				section = "proxy-providers"
			}
			pending[section] = append(pending[section], name)
		}
	})
	if len(pending) == 0 {
		return configBytes, nil
	}

	var doc yaml.Node
	if err := yaml.Unmarshal(configBytes, &doc); err != nil {
		return nil, err
	}
	root := doc.Content[0]
	for section, names := range pending {
		sort.Strings(names)
		if err := setProviderPaths(root, section, names, sections[section]); err != nil {
			return nil, fmt.Errorf("%s: %w", section, err)
		}
	}
	var buf bytes.Buffer
	enc := yaml.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(&doc); err != nil {
		return nil, err
	}
	if err := enc.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func setProviderPaths(root *yaml.Node, section string, names []string, providers map[string]map[string]any) error {
	index := findMappingKey(root, section)
	if index < 0 || resolveAlias(root.Content[index+1]).Kind != yaml.MappingNode {
		var node yaml.Node
		if err := node.Encode(providers); err != nil {
			return err
		}
		if index < 0 {
			root.Content = append(root.Content, stringNode(section), &node)
		} else {
			root.Content[index+1] = &node
		}
		return nil
	}
	mapping := resolveAlias(root.Content[index+1])
	for _, name := range names {
		path := providers[name]["path"].(string)
		i := findMappingKey(mapping, name)
		if i < 0 {
			var node yaml.Node
			if err := node.Encode(providers[name]); err != nil {
				return err
			}
			mapping.Content = append(mapping.Content, stringNode(name), &node)
			continue
		}
		value := mapping.Content[i+1]
		target := resolveAlias(value)
		if target.Kind != yaml.MappingNode {
			var node yaml.Node
			if err := node.Encode(providers[name]); err != nil {
				return err
			}
			mapping.Content[i+1] = &node
			continue
		}
		// 别名指向的节点与别处共用，各自需要不同路径，先复制一份；带锚点的原节点就地修改，别名才不会悬空。
		if value.Kind == yaml.AliasNode {
			copied := *target
			copied.Anchor = ""
			copied.Content = append([]*yaml.Node(nil), target.Content...)
			target = &copied
			mapping.Content[i+1] = target
		}
		if j := findMappingKey(target, "path"); j >= 0 {
			target.Content[j+1] = stringNode(path)
		} else {
			target.Content = append(target.Content, stringNode("path"), stringNode(path))
		}
	}
	return nil
}

func findMappingKey(mapping *yaml.Node, name string) int {
	for i := 0; i+1 < len(mapping.Content); i += 2 {
		if key := resolveAlias(mapping.Content[i]); key.Kind == yaml.ScalarNode && key.Value == name {
			return i
		}
	}
	return -1
}

func resolveAlias(node *yaml.Node) *yaml.Node {
	for node.Kind == yaml.AliasNode {
		node = node.Alias
	}
	return node
}

func stringNode(value string) *yaml.Node {
	return &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: value}
}
