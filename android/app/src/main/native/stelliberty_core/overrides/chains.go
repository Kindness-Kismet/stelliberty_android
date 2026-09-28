package overrides

import (
	"strconv"
	"strings"
	"unicode"

	"go.yaml.in/yaml/v3"
)

// 与 PC 的 SubscriptionChainProxyHopKind 编号一致。
const (
	HopProxy      = 0
	HopProxyGroup = 1
)

// 键名与订阅列表里 CustomChainProxies 的 PC 格式一致，Kotlin 侧直接序列化模型。
type ChainHop struct {
	Kind int    `json:"Kind"`
	Name string `json:"Name"`
}

type CustomChain struct {
	ID             string     `json:"Id"`
	DisplayName    string     `json:"DisplayName"`
	ProxyGroupName string     `json:"ProxyGroupName"`
	Hops           []ChainHop `json:"Hops"`
	IsEnabled      bool       `json:"IsEnabled"`
}

type ChainOption struct {
	Kind int    `json:"kind"`
	Name string `json:"name"`
	Type string `json:"type"`
}

// ChainContext 是链式代理页面的候选项，取自套完覆写后的配置。
type ChainContext struct {
	BuiltinNames []string      `json:"builtinNames"`
	ProxyGroups  []ChainOption `json:"proxyGroups"`
	Candidates   []ChainOption `json:"candidates"`
}

// dialer-proxy 允许指向内置出站，这些名字不算悬空。
var builtinOutbounds = []string{"DIRECT", "REJECT", "REJECT-DROP", "PASS", "COMPATIBLE"}

// 与 mihomo 的 empty-fallback 默认值一致，作为空代理组占位。
const defaultEmptyFallback = "COMPATIBLE"

const internalChainPrefix = "__stelliberty_chain_"

// ApplyChains 按 PC 规则移除禁用的内置链并追加自定义链。上游失效的内置链按禁用处理、不成立的自定义链跳过，
// 否则内核会拒绝整份配置。
func ApplyChains(config []byte, disabled []string, custom []CustomChain) ([]byte, error) {
	doc, root, err := parseRoot(config)
	if err != nil {
		// 原样交给内核，由它报出配置本身的错误。
		return config, nil
	}
	budget := maxExpandedNodes
	root, err = expand(root, &budget)
	if err != nil {
		return nil, err
	}
	proxies := mappingItems(root, "proxies")
	groups := mappingItems(root, "proxy-groups")
	disabledSet := make(map[string]bool, len(disabled))
	for _, name := range disabled {
		disabledSet[name] = true
	}
	for _, name := range brokenBuiltinChains(proxies, groups, disabledSet) {
		disabledSet[name] = true
	}
	if len(disabledSet) == 0 && !anyEnabled(custom) {
		return config, nil
	}

	index := newChainIndex(proxies, groups, disabledSet)
	runtimeProxies := append([]*yaml.Node(nil), index.active...)
	var entries []groupEntry
	for _, chain := range custom {
		if !chain.IsEnabled {
			continue
		}
		hops := index.buildChain(chain)
		if len(hops) == 0 {
			continue
		}
		entries = append(entries, groupEntry{
			group:   strings.TrimSpace(chain.ProxyGroupName),
			display: strings.TrimSpace(chain.DisplayName),
		})
		runtimeProxies = append(runtimeProxies, hops...)
	}
	disabledBuiltin := make(map[string]bool)
	for _, proxy := range proxies {
		if isDisabledBuiltin(proxy, disabledSet) {
			disabledBuiltin[scalar(proxy, "name")] = true
		}
	}

	setValue(root, strNode("proxies"), sequenceNode(runtimeProxies))
	if i := findKey(root, "proxy-groups"); i >= 0 && root.Content[i+1].Kind == yaml.SequenceNode {
		setValue(root, strNode("proxy-groups"), buildProxyGroups(groups, disabledBuiltin, entries))
	}
	doc.Content[0] = root
	return encode(doc)
}

// ChainContextOf 列出内置链与可选跳点：带 dialer-proxy 的节点算内置链，不作为自定义链的跳点。
func ChainContextOf(config []byte) (*ChainContext, error) {
	result := &ChainContext{BuiltinNames: []string{}, ProxyGroups: []ChainOption{}, Candidates: []ChainOption{}}
	_, root, err := parseRoot(config)
	if err != nil {
		return result, nil
	}
	budget := maxExpandedNodes
	root, err = expand(root, &budget)
	if err != nil {
		return nil, err
	}
	seen := make(map[string]bool)
	for _, proxy := range mappingItems(root, "proxies") {
		name := scalar(proxy, "name")
		if blank(name) {
			continue
		}
		if !blank(scalar(proxy, "dialer-proxy")) {
			if !seen[name] {
				seen[name] = true
				result.BuiltinNames = append(result.BuiltinNames, name)
			}
			continue
		}
		if kind := scalar(proxy, "type"); !blank(kind) {
			result.Candidates = append(result.Candidates, ChainOption{Kind: HopProxy, Name: name, Type: kind})
		}
	}
	for _, group := range mappingItems(root, "proxy-groups") {
		name := scalar(group, "name")
		if blank(name) {
			continue
		}
		option := ChainOption{Kind: HopProxyGroup, Name: name, Type: scalar(group, "type")}
		result.ProxyGroups = append(result.ProxyGroups, option)
		result.Candidates = append(result.Candidates, option)
	}
	return result, nil
}

// HasChainCycle 检查节点 dialer-proxy 与代理组成员组成的引用图是否有环；经代理组绕回的环内核解析时发现不了。
func HasChainCycle(config []byte) bool {
	_, root, err := parseRoot(config)
	if err != nil {
		return false
	}
	budget := maxExpandedNodes
	root, err = expand(root, &budget)
	if err != nil {
		return false
	}
	edges := make(map[string]map[string]bool)
	indegree := make(map[string]int)
	addVertex := func(name string) {
		if _, ok := edges[name]; !ok {
			edges[name] = make(map[string]bool)
			indegree[name] = 0
		}
	}
	addEdge := func(from, to string) {
		if blank(from) || blank(to) {
			return
		}
		addVertex(from)
		addVertex(to)
		if !edges[from][to] {
			edges[from][to] = true
			indegree[to]++
		}
	}
	for _, proxy := range mappingItems(root, "proxies") {
		addEdge(scalar(proxy, "name"), scalar(proxy, "dialer-proxy"))
	}
	for _, group := range mappingItems(root, "proxy-groups") {
		name := scalar(group, "name")
		for _, member := range scalarItems(group, "proxies") {
			addEdge(name, member)
		}
	}
	var ready []string
	for name, degree := range indegree {
		if degree == 0 {
			ready = append(ready, name)
		}
	}
	visited := 0
	for len(ready) > 0 {
		name := ready[len(ready)-1]
		ready = ready[:len(ready)-1]
		visited++
		for target := range edges[name] {
			indegree[target]--
			if indegree[target] == 0 {
				ready = append(ready, target)
			}
		}
	}
	return visited != len(indegree)
}

func anyEnabled(custom []CustomChain) bool {
	for _, chain := range custom {
		if chain.IsEnabled {
			return true
		}
	}
	return false
}

// 已禁用节点不在可达集合内，指向它们的下游一并判定失效，直到没有新的失效节点。
func brokenBuiltinChains(proxies, groups []*yaml.Node, disabled map[string]bool) []string {
	type link struct{ name, dialer string }
	var links []link
	for _, proxy := range proxies {
		name, dialer := scalar(proxy, "name"), scalar(proxy, "dialer-proxy")
		if !blank(name) && !blank(dialer) {
			links = append(links, link{name, dialer})
		}
	}
	if len(links) == 0 {
		return nil
	}
	reachable := make(map[string]bool)
	for _, proxy := range proxies {
		if name := scalar(proxy, "name"); !blank(name) && !disabled[name] {
			reachable[name] = true
		}
	}
	for _, group := range groups {
		if name := scalar(group, "name"); !blank(name) {
			reachable[name] = true
		}
	}
	for _, name := range builtinOutbounds {
		reachable[name] = true
	}
	var broken []string
	for changed := true; changed; {
		changed = false
		for _, l := range links {
			if !reachable[l.name] || reachable[l.dialer] {
				continue
			}
			delete(reachable, l.name)
			broken = append(broken, l.name)
			changed = true
		}
	}
	return broken
}

type chainIndex struct {
	active     []*yaml.Node
	byName     map[string]*yaml.Node
	groupNames map[string]bool
	occupied   map[string]bool
}

func newChainIndex(proxies, groups []*yaml.Node, disabled map[string]bool) *chainIndex {
	index := &chainIndex{
		byName:     make(map[string]*yaml.Node),
		groupNames: make(map[string]bool),
		occupied:   make(map[string]bool),
	}
	for _, proxy := range proxies {
		if isDisabledBuiltin(proxy, disabled) {
			continue
		}
		index.active = append(index.active, proxy)
		name := scalar(proxy, "name")
		if blank(name) {
			continue
		}
		if _, ok := index.byName[name]; !ok {
			index.byName[name] = proxy
		}
		index.occupied[name] = true
	}
	for _, group := range groups {
		if name := scalar(group, "name"); !blank(name) {
			index.groupNames[name] = true
			index.occupied[name] = true
		}
	}
	return index
}

// buildChain 把跳点展开成一串节点副本：末跳用显示名，中间跳用内部名，各自的 dialer-proxy 指向上一跳。
// 代理组只能作为首跳；不成立时返回 nil，名字不会被占用。
func (index *chainIndex) buildChain(chain CustomChain) []*yaml.Node {
	var hops []ChainHop
	for _, hop := range chain.Hops {
		if name := strings.TrimSpace(hop.Name); name != "" {
			hops = append(hops, ChainHop{Kind: hop.Kind, Name: name})
		}
	}
	display := strings.TrimSpace(chain.DisplayName)
	group := strings.TrimSpace(chain.ProxyGroupName)
	if len(hops) < 2 || display == "" || group == "" || index.occupied[display] || !index.groupNames[group] {
		return nil
	}
	for _, hop := range hops[1:] {
		if hop.Kind != HopProxy || index.byName[hop.Name] == nil {
			return nil
		}
	}
	first := hops[0]
	if (first.Kind == HopProxyGroup && !index.groupNames[first.Name]) ||
		(first.Kind == HopProxy && index.byName[first.Name] == nil) {
		return nil
	}

	planned := make(map[string]bool, len(index.occupied)+len(hops))
	for name := range index.occupied {
		planned[name] = true
	}
	planned[display] = true
	names := make([]string, 0, len(hops)-1)
	for i := 1; i < len(hops); i++ {
		if i == len(hops)-1 {
			names = append(names, display)
		} else {
			names = append(names, reserveInternalName(chain, i, planned))
		}
	}
	for _, name := range names {
		index.occupied[name] = true
	}
	result := make([]*yaml.Node, 0, len(names))
	previous := first.Name
	for i, name := range names {
		proxy := cloneMapping(index.byName[hops[i+1].Name])
		setValue(proxy, strNode("name"), strNode(name))
		setValue(proxy, strNode("dialer-proxy"), strNode(previous))
		result = append(result, proxy)
		previous = name
	}
	return result
}

type groupEntry struct{ group, display string }

func buildProxyGroups(groups []*yaml.Node, disabledBuiltin map[string]bool, entries []groupEntry) *yaml.Node {
	result := make([]*yaml.Node, 0, len(groups))
	for _, group := range groups {
		clone := cloneMapping(group)
		name := scalar(group, "name")
		var members []*yaml.Node
		if i := findKey(group, "proxies"); i >= 0 && group.Content[i+1].Kind == yaml.SequenceNode {
			for _, member := range group.Content[i+1].Content {
				if member.Kind == yaml.ScalarNode && disabledBuiltin[member.Value] {
					continue
				}
				members = append(members, member)
			}
		}
		// 自定义链只挂到用户选定的代理组。
		for _, entry := range entries {
			if entry.group == name && !containsScalar(members, entry.display) {
				members = append(members, strNode(entry.display))
			}
		}
		// 移除禁用节点可能清空代理组，mihomo 会以 proxies missing 拒绝整份配置。
		if len(members) == 0 && !hasCoreProvidedMembers(group) {
			fallback := scalar(group, "empty-fallback")
			if blank(fallback) {
				fallback = defaultEmptyFallback
			}
			members = append(members, strNode(fallback))
		}
		if len(members) > 0 {
			setValue(clone, strNode("proxies"), sequenceNode(members))
		} else {
			removeKey(clone, "proxies")
		}
		result = append(result, clone)
	}
	return sequenceNode(result)
}

// use 与 include-all 系列由内核补齐成员，空 proxies 不会被拒绝。
func hasCoreProvidedMembers(group *yaml.Node) bool {
	if i := findKey(group, "use"); i >= 0 {
		if use := group.Content[i+1]; use.Kind == yaml.SequenceNode && len(use.Content) > 0 {
			return true
		}
	}
	for _, key := range []string{"include-all", "include-all-proxies", "include-all-providers"} {
		if strings.EqualFold(scalar(group, key), "true") {
			return true
		}
	}
	return false
}

func isDisabledBuiltin(proxy *yaml.Node, disabled map[string]bool) bool {
	return disabled[scalar(proxy, "name")] && !blank(scalar(proxy, "dialer-proxy"))
}

func reserveInternalName(chain CustomChain, index int, occupied map[string]bool) string {
	source := strings.TrimSpace(chain.ID)
	if source == "" {
		source = strings.TrimSpace(chain.DisplayName)
	}
	segment := strings.Map(func(r rune) rune {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '-' || r == '_' {
			return r
		}
		return '_'
	}, source)
	if segment == "" {
		segment = "custom"
	}
	stem := internalChainPrefix + segment + "_" + strconv.Itoa(index)
	name := stem
	for suffix := 2; occupied[name]; suffix++ {
		name = stem + "_" + strconv.Itoa(suffix)
	}
	occupied[name] = true
	return name
}

func mappingItems(root *yaml.Node, key string) []*yaml.Node {
	i := findKey(root, key)
	if i < 0 || root.Content[i+1].Kind != yaml.SequenceNode {
		return nil
	}
	var items []*yaml.Node
	for _, item := range root.Content[i+1].Content {
		if item.Kind == yaml.MappingNode {
			items = append(items, item)
		}
	}
	return items
}

func scalarItems(mapping *yaml.Node, key string) []string {
	i := findKey(mapping, key)
	if i < 0 || mapping.Content[i+1].Kind != yaml.SequenceNode {
		return nil
	}
	var items []string
	for _, item := range mapping.Content[i+1].Content {
		if item.Kind == yaml.ScalarNode && !isNull(item) {
			items = append(items, item.Value)
		}
	}
	return items
}

func scalar(mapping *yaml.Node, key string) string {
	i := findKey(mapping, key)
	if i < 0 {
		return ""
	}
	value := mapping.Content[i+1]
	if value.Kind != yaml.ScalarNode || isNull(value) {
		return ""
	}
	return value.Value
}

func containsScalar(nodes []*yaml.Node, value string) bool {
	for _, node := range nodes {
		if node.Kind == yaml.ScalarNode && node.Value == value {
			return true
		}
	}
	return false
}

func removeKey(mapping *yaml.Node, key string) {
	if i := findKey(mapping, key); i >= 0 {
		mapping.Content = append(mapping.Content[:i:i], mapping.Content[i+2:]...)
	}
}

func cloneMapping(node *yaml.Node) *yaml.Node {
	clone := *node
	clone.Content = append([]*yaml.Node(nil), node.Content...)
	return &clone
}

func sequenceNode(items []*yaml.Node) *yaml.Node {
	return &yaml.Node{Kind: yaml.SequenceNode, Tag: "!!seq", Content: items}
}

func blank(value string) bool {
	return strings.TrimSpace(value) == ""
}
