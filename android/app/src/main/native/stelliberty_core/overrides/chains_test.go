package overrides

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

const chainBaseConfig = `
proxies:
  - name: HK
    type: ss
    server: hk.example
  - name: TW
    type: ss
    server: tw.example
  - name: JP
    type: ss
    server: jp.example
  - name: JP via HK
    type: ss
    server: jp.example
    dialer-proxy: HK
proxy-groups:
  - name: GLOBAL
    type: select
    proxies: [HK, TW, JP]
rules: []
`

func chain(id, display, group string, names ...string) CustomChain {
	hops := make([]ChainHop, len(names))
	for i, name := range names {
		hops[i] = ChainHop{Kind: HopProxy, Name: name}
	}
	return CustomChain{ID: id, DisplayName: display, ProxyGroupName: group, Hops: hops, IsEnabled: true}
}

func mustChains(t *testing.T, config string, disabled []string, custom ...CustomChain) map[string]any {
	t.Helper()
	out, err := ApplyChains([]byte(config), disabled, custom)
	if err != nil {
		t.Fatalf("ApplyChains: %v", err)
	}
	return decode(t, out)
}

func proxyByName(t *testing.T, cfg map[string]any, name string) map[string]any {
	t.Helper()
	for _, item := range cfg["proxies"].([]any) {
		if proxy := item.(map[string]any); proxy["name"] == name {
			return proxy
		}
	}
	return nil
}

func groupMembers(t *testing.T, cfg map[string]any, name string) []any {
	t.Helper()
	for _, item := range cfg["proxy-groups"].([]any) {
		group := item.(map[string]any)
		if group["name"] != name {
			continue
		}
		members, _ := group["proxies"].([]any)
		return members
	}
	t.Fatalf("group %s not found", name)
	return nil
}

func TestDisabledBuiltinChainIsRemovedFromProxiesAndGroups(t *testing.T) {
	cfg := mustChains(t, `
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: JP, type: ss, server: jp.example}
  - {name: JP via HK, type: ss, server: jp.example, dialer-proxy: HK}
proxy-groups:
  - {name: GLOBAL, type: select, proxies: [HK, JP via HK, JP]}
rules: []
`, []string{"JP via HK"})
	if proxyByName(t, cfg, "JP via HK") != nil || proxyByName(t, cfg, "HK") == nil || proxyByName(t, cfg, "JP") == nil {
		t.Fatalf("unexpected proxies: %v", cfg["proxies"])
	}
	if got := groupMembers(t, cfg, "GLOBAL"); !reflect.DeepEqual(got, []any{"HK", "JP"}) {
		t.Fatalf("GLOBAL = %v", got)
	}
}

func TestEmptiedGroupFallsBackToEmptyFallback(t *testing.T) {
	cfg := mustChains(t, `
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: JP via HK, type: ss, server: jp.example, dialer-proxy: HK}
proxy-groups:
  - {name: LANDING, type: select, proxies: [JP via HK]}
  - {name: CUSTOM, type: select, empty-fallback: DIRECT, proxies: [JP via HK]}
  - {name: USE ONLY, type: select, use: [airport], proxies: [JP via HK]}
  - {name: INCLUDE ALL, type: select, include-all: true, proxies: [JP via HK]}
rules: []
`, []string{"JP via HK"})
	if got := groupMembers(t, cfg, "LANDING"); !reflect.DeepEqual(got, []any{"COMPATIBLE"}) {
		t.Fatalf("LANDING = %v", got)
	}
	if got := groupMembers(t, cfg, "CUSTOM"); !reflect.DeepEqual(got, []any{"DIRECT"}) {
		t.Fatalf("CUSTOM = %v", got)
	}
	if got := groupMembers(t, cfg, "USE ONLY"); len(got) != 0 {
		t.Fatalf("USE ONLY = %v", got)
	}
	if got := groupMembers(t, cfg, "INCLUDE ALL"); len(got) != 0 {
		t.Fatalf("INCLUDE ALL = %v", got)
	}
}

func TestCustomChainAddsInternalHopsAndJoinsGroup(t *testing.T) {
	cfg := mustChains(t, chainBaseConfig, nil, chain("chain-a", "JP via TW via HK", "GLOBAL", "HK", "TW", "JP"))
	internal := proxyByName(t, cfg, "__stelliberty_chain_chain-a_1")
	display := proxyByName(t, cfg, "JP via TW via HK")
	if internal == nil || display == nil {
		t.Fatalf("chain proxies missing: %v", cfg["proxies"])
	}
	if internal["dialer-proxy"] != "HK" || internal["server"] != "tw.example" {
		t.Fatalf("internal hop = %v", internal)
	}
	if display["dialer-proxy"] != "__stelliberty_chain_chain-a_1" || display["server"] != "jp.example" {
		t.Fatalf("display hop = %v", display)
	}
	if got := groupMembers(t, cfg, "GLOBAL"); !reflect.DeepEqual(got, []any{"HK", "TW", "JP", "JP via TW via HK"}) {
		t.Fatalf("GLOBAL = %v", got)
	}
	if proxyByName(t, cfg, "JP")["dialer-proxy"] != nil {
		t.Fatal("source proxy must stay untouched")
	}
}

func TestCustomChainOverridesLeafDialerProxy(t *testing.T) {
	cfg := mustChains(t, `
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: TW, type: ss, server: tw.example}
  - {name: JP, type: ss, server: jp.example, dialer-proxy: TW}
proxy-groups:
  - {name: GLOBAL, type: select, proxies: [HK, TW, JP]}
rules: []
`, nil, chain("chain-a", "JP via HK", "GLOBAL", "HK", "JP"))
	if got := proxyByName(t, cfg, "JP via HK")["dialer-proxy"]; got != "HK" {
		t.Fatalf("dialer-proxy = %v", got)
	}
}

func TestGroupFirstHop(t *testing.T) {
	group := chain("chain-g", "JP via GLOBAL", "GLOBAL", "GLOBAL", "JP")
	group.Hops[0].Kind = HopProxyGroup
	cfg := mustChains(t, chainBaseConfig, nil, group)
	if got := proxyByName(t, cfg, "JP via GLOBAL")["dialer-proxy"]; got != "GLOBAL" {
		t.Fatalf("dialer-proxy = %v", got)
	}

	late := chain("chain-late", "Late group", "GLOBAL", "HK", "GLOBAL")
	late.Hops[1].Kind = HopProxyGroup
	cfg = mustChains(t, chainBaseConfig, nil, late)
	if proxyByName(t, cfg, "Late group") != nil {
		t.Fatal("a proxy group after the first hop must invalidate the chain")
	}
}

func TestInvalidCustomChainsAreSkipped(t *testing.T) {
	disabled := chain("off", "Disabled chain", "GLOBAL", "HK", "TW")
	disabled.IsEnabled = false
	cfg := mustChains(t, chainBaseConfig, nil,
		chain("conflict-node", "JP", "GLOBAL", "HK", "TW"),
		chain("conflict-group", "GLOBAL", "GLOBAL", "HK", "TW"),
		chain("missing", "Missing chain", "GLOBAL", "HK", "Missing"),
		chain("single", "Single chain", "GLOBAL", "HK"),
		chain("no-group", "No group", "Missing group", "HK", "TW"),
		disabled,
	)
	for _, item := range cfg["proxies"].([]any) {
		name := item.(map[string]any)["name"].(string)
		switch name {
		case "HK", "TW", "JP", "JP via HK":
		default:
			t.Fatalf("unexpected proxy %q", name)
		}
	}
}

func TestDuplicateDisplayNameKeepsFirstChain(t *testing.T) {
	cfg := mustChains(t, chainBaseConfig, nil,
		chain("a", "Relay", "GLOBAL", "HK", "JP"),
		chain("b", "Relay", "GLOBAL", "TW", "JP"),
	)
	if got := proxyByName(t, cfg, "Relay")["dialer-proxy"]; got != "HK" {
		t.Fatalf("dialer-proxy = %v", got)
	}
}

func TestInternalNamesAvoidConflicts(t *testing.T) {
	cfg := mustChains(t, `
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: TW, type: ss, server: tw.example}
  - {name: JP, type: ss, server: jp.example}
  - {name: __stelliberty_chain_chain-a_1, type: ss, server: occupied.example}
proxy-groups:
  - {name: GLOBAL, type: select, proxies: [HK, TW, JP]}
rules: []
`, nil, chain("chain-a", "JP via TW via HK", "GLOBAL", "HK", "TW", "JP"))
	if proxyByName(t, cfg, "__stelliberty_chain_chain-a_1_2") == nil {
		t.Fatalf("expected suffixed internal name: %v", cfg["proxies"])
	}
}

func TestBrokenBuiltinChainsAreDisabledTransitively(t *testing.T) {
	cfg := mustChains(t, `
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: A, type: ss, server: a.example, dialer-proxy: Gone}
  - {name: B, type: ss, server: b.example, dialer-proxy: A}
  - {name: C, type: ss, server: c.example, dialer-proxy: DIRECT}
  - {name: D, type: ss, server: d.example, dialer-proxy: Relay}
proxy-groups:
  - {name: Relay, type: select, proxies: [HK]}
  - {name: GLOBAL, type: select, proxies: [A, B, C, D]}
rules: []
`, nil)
	if proxyByName(t, cfg, "A") != nil || proxyByName(t, cfg, "B") != nil {
		t.Fatalf("broken chains kept: %v", cfg["proxies"])
	}
	if proxyByName(t, cfg, "C") == nil || proxyByName(t, cfg, "D") == nil {
		t.Fatalf("valid chains removed: %v", cfg["proxies"])
	}
	if got := groupMembers(t, cfg, "GLOBAL"); !reflect.DeepEqual(got, []any{"C", "D"}) {
		t.Fatalf("GLOBAL = %v", got)
	}
}

func TestChainsKeepContentWhenNothingToDo(t *testing.T) {
	for _, config := range []string{chainBaseConfig, "proxies: ["} {
		out, err := ApplyChains([]byte(config), nil, nil)
		if err != nil || string(out) != config {
			t.Fatalf("content changed: %v\n%s", err, out)
		}
	}
	out, err := ApplyChains([]byte("proxies: ["), []string{"JP via HK"}, nil)
	if err != nil || string(out) != "proxies: [" {
		t.Fatalf("invalid YAML must pass through: %v", err)
	}
}

func TestChainsResolveAliases(t *testing.T) {
	cfg := mustChains(t, `
proxies:
  - &hk {name: HK, type: ss, server: hk.example}
  - {name: JP, type: ss, server: jp.example}
  - {name: JP via HK, type: ss, server: jp.example, dialer-proxy: HK}
proxy-groups:
  - {name: GLOBAL, type: select, proxies: &members [HK, JP via HK, JP]}
  - {name: BACKUP, type: select, proxies: *members}
rules: []
`, []string{"JP via HK"}, chain("chain-a", "JP via HK 2", "BACKUP", "HK", "JP"))
	if got := groupMembers(t, cfg, "GLOBAL"); !reflect.DeepEqual(got, []any{"HK", "JP"}) {
		t.Fatalf("GLOBAL = %v", got)
	}
	if got := groupMembers(t, cfg, "BACKUP"); !reflect.DeepEqual(got, []any{"HK", "JP", "JP via HK 2"}) {
		t.Fatalf("BACKUP = %v", got)
	}
}

func TestChainContextSeparatesBuiltinsAndCandidates(t *testing.T) {
	context, err := ChainContextOf([]byte(`
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: JP via HK, type: vmess, dialer-proxy: HK}
  - {name: JP via HK, type: vmess, dialer-proxy: HK}
  - {name: NoType}
proxy-groups:
  - {name: GLOBAL, type: select, proxies: [HK]}
`))
	if err != nil {
		t.Fatal(err)
	}
	want := &ChainContext{
		BuiltinNames: []string{"JP via HK"},
		ProxyGroups:  []ChainOption{{Kind: HopProxyGroup, Name: "GLOBAL", Type: "select"}},
		Candidates: []ChainOption{
			{Kind: HopProxy, Name: "HK", Type: "ss"},
			{Kind: HopProxyGroup, Name: "GLOBAL", Type: "select"},
		},
	}
	if !reflect.DeepEqual(context, want) {
		t.Fatalf("context = %+v", context)
	}
	empty, err := ChainContextOf([]byte("proxies: ["))
	if err != nil || len(empty.BuiltinNames)+len(empty.ProxyGroups)+len(empty.Candidates) != 0 {
		t.Fatalf("invalid YAML must give an empty context: %+v %v", empty, err)
	}
}

func TestChainCycleThroughGroup(t *testing.T) {
	acyclic := chainBaseConfig
	if HasChainCycle([]byte(acyclic)) {
		t.Fatal("unexpected cycle")
	}
	cyclic := `
proxies:
  - {name: HK, type: ss, server: hk.example}
  - {name: JP, type: ss, server: jp.example, dialer-proxy: Relay}
proxy-groups:
  - {name: Relay, type: select, proxies: [HK, JP]}
`
	if !HasChainCycle([]byte(cyclic)) {
		t.Fatal("cycle through a proxy group not detected")
	}
}

func TestTransformAppliesOverridesBeforeChains(t *testing.T) {
	dir := t.TempDir()
	override := filepath.Join(dir, "add.yaml")
	if err := os.WriteFile(override, []byte("proxies+:\n  - {name: KR, type: ss, server: kr.example}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	transform := &Transform{
		Overrides:    []Spec{{Name: "add", Format: FormatYAML, Path: override}},
		CustomChains: []CustomChain{chain("chain-kr", "KR via HK", "GLOBAL", "HK", "KR")},
	}
	out, err := transform.Apply([]byte(chainBaseConfig))
	if err != nil {
		t.Fatal(err)
	}
	if got := proxyByName(t, decode(t, out), "KR via HK"); got == nil || got["dialer-proxy"] != "HK" {
		t.Fatalf("chain over overridden proxy missing: %v", got)
	}
}
