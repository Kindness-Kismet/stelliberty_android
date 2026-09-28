package overrides

import (
	"reflect"
	"testing"
)

const ruleBaseConfig = `
proxies:
  - {name: HK, type: ss, server: hk.example}
proxy-groups:
  - {name: Proxy, type: select, proxies: [HK]}
rules:
  - DOMAIN-SUFFIX,example.com,Proxy
  - IP-CIDR,10.0.0.0/8,DIRECT,no-resolve
  - DOMAIN-SUFFIX,example.com,Proxy
  - MATCH,Proxy
`

func customRule(id, kind, payload, proxy string) EditableRule {
	return EditableRule{ID: id, Type: kind, Payload: payload, Proxy: proxy, IsEnabled: true}
}

func mustRules(t *testing.T, config string, set *RuleOverride) []any {
	t.Helper()
	out, err := ApplyRules([]byte(config), set)
	if err != nil {
		t.Fatalf("ApplyRules: %v", err)
	}
	rules, _ := decode(t, out)["rules"].([]any)
	return rules
}

func TestRuleKeyMatchesPC(t *testing.T) {
	if got, want := parseRuleKey("ip-cidr, 10.0.0.0/8 ,direct,no-resolve"), "IP-CIDR\x1f10.0.0.0/8\x1fDIRECT\x1fNO-RESOLVE"; got != want {
		t.Fatalf("key = %q, want %q", got, want)
	}
	if got, want := parseRuleKey("MATCH,Proxy"), "MATCH\x1f\x1fPROXY\x1f"; got != want {
		t.Fatalf("match key = %q, want %q", got, want)
	}
	rule := EditableRule{Type: "IP-CIDR", Payload: "10.0.0.0/8", Proxy: "DIRECT", Options: "no-resolve"}
	if got := parseRuleKey(rule.Render()); got != RuleKey(rule.Type, rule.Payload, rule.Proxy, rule.Options) {
		t.Fatalf("rendered key %q differs", got)
	}
}

func TestCustomRulesGoBeforeFirstMatch(t *testing.T) {
	rules := mustRules(t, ruleBaseConfig, &RuleOverride{CustomRules: []EditableRule{
		customRule("a", "DOMAIN", "a.example", "DIRECT"),
		{ID: "off", Type: "DOMAIN", Payload: "off.example", Proxy: "DIRECT"},
	}})
	want := []any{
		"DOMAIN-SUFFIX,example.com,Proxy",
		"IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
		"DOMAIN-SUFFIX,example.com,Proxy",
		"DOMAIN,a.example,DIRECT",
		"MATCH,Proxy",
	}
	if !reflect.DeepEqual(rules, want) {
		t.Fatalf("rules = %v", rules)
	}
}

func TestDisabledBuiltinRulesAreRemoved(t *testing.T) {
	rules := mustRules(t, ruleBaseConfig, &RuleOverride{
		DisabledBuiltinRuleKeys: []string{RuleKey("DOMAIN-SUFFIX", "example.com", "Proxy", "")},
	})
	want := []any{"IP-CIDR,10.0.0.0/8,DIRECT,no-resolve", "MATCH,Proxy"}
	if !reflect.DeepEqual(rules, want) {
		t.Fatalf("rules = %v", rules)
	}
}

func TestSavedOrderDedupesAndPlacesNewRules(t *testing.T) {
	match := builtinOrderPrefix + parseRuleKey("MATCH,Proxy")
	suffix := builtinOrderPrefix + parseRuleKey("DOMAIN-SUFFIX,example.com,Proxy")
	rules := mustRules(t, ruleBaseConfig, &RuleOverride{
		CustomRules: []EditableRule{
			customRule("top", "DOMAIN", "top.example", "Proxy"),
			customRule("new", "DOMAIN", "new.example", "DIRECT"),
		},
		RuleOrder: []string{customOrderPrefix + "top", suffix, match, "custom:gone"},
	})
	// 顺序里没有的订阅规则接在末尾，未排过序的新自定义规则插到首个 MATCH 前。
	want := []any{
		"DOMAIN,top.example,Proxy",
		"DOMAIN-SUFFIX,example.com,Proxy",
		"DOMAIN,new.example,DIRECT",
		"MATCH,Proxy",
		"IP-CIDR,10.0.0.0/8,DIRECT,no-resolve",
	}
	if !reflect.DeepEqual(rules, want) {
		t.Fatalf("rules = %v", rules)
	}
}

func TestCustomRulesWithMissingTargetAreSkipped(t *testing.T) {
	rules := mustRules(t, ruleBaseConfig, &RuleOverride{CustomRules: []EditableRule{
		customRule("gone", "DOMAIN", "gone.example", "Removed"),
		customRule("group", "DOMAIN", "group.example", "Proxy"),
		customRule("node", "DOMAIN", "node.example", "HK"),
		customRule("global", "DOMAIN", "global.example", "GLOBAL"),
	}})
	if len(rules) != 7 || rules[3] != "DOMAIN,group.example,Proxy" || rules[5] != "DOMAIN,global.example,GLOBAL" {
		t.Fatalf("rules = %v", rules)
	}
}

func TestRulesKeepContentWhenNothingToDo(t *testing.T) {
	out, err := ApplyRules([]byte(ruleBaseConfig), &RuleOverride{})
	if err != nil || string(out) != ruleBaseConfig {
		t.Fatalf("content changed: %v", err)
	}
}

func TestNonStringRulesAreRejected(t *testing.T) {
	_, err := ApplyRules([]byte("rules:\n  - {type: DOMAIN}\n"), &RuleOverride{RuleOrder: []string{"custom:a"}})
	if err == nil {
		t.Fatal("expected error")
	}
}

func TestRuleContextDedupesAndListsTargets(t *testing.T) {
	ctx, err := RuleContextOf([]byte(ruleBaseConfig + "  - DOMAIN-SUFFIX\n  - ',,'\n"))
	if err != nil {
		t.Fatal(err)
	}
	if len(ctx.Rules) != 3 {
		t.Fatalf("rules = %+v", ctx.Rules)
	}
	cidr := ctx.Rules[1]
	if cidr.Type != "IP-CIDR" || cidr.Payload != "10.0.0.0/8" || cidr.Proxy != "DIRECT" || cidr.Options != "no-resolve" ||
		cidr.Key != parseRuleKey("IP-CIDR,10.0.0.0/8,DIRECT,no-resolve") || cidr.MatchKey != "IP-CIDR\x1f10.0.0.0/8" {
		t.Fatalf("cidr = %+v", cidr)
	}
	if ctx.Rules[2].Type != "MATCH" || ctx.Rules[2].Proxy != "Proxy" {
		t.Fatalf("match = %+v", ctx.Rules[2])
	}
	if !reflect.DeepEqual(ctx.ProxyGroups, []string{"Proxy"}) {
		t.Fatalf("groups = %v", ctx.ProxyGroups)
	}
	want := append(append([]string(nil), ruleBuiltinTargets...), "HK", "Proxy")
	if !reflect.DeepEqual(ctx.Targets, want) {
		t.Fatalf("targets = %v", ctx.Targets)
	}
}

func TestTransformAppliesRulesAfterChains(t *testing.T) {
	transform := &Transform{
		CustomChains: []CustomChain{chain("chain-jp", "JP relay", "GLOBAL", "TW", "JP")},
		Rules: &RuleOverride{CustomRules: []EditableRule{
			customRule("r", "DOMAIN", "jp.example", "JP relay"),
		}},
	}
	out, err := transform.Apply([]byte(chainBaseConfig))
	if err != nil {
		t.Fatal(err)
	}
	rules, _ := decode(t, out)["rules"].([]any)
	if !reflect.DeepEqual(rules, []any{"DOMAIN,jp.example,JP relay"}) {
		t.Fatalf("rules = %v", rules)
	}
}
