package overrides

import (
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"go.yaml.in/yaml/v3"
)

const baseConfig = `
mixed-port: 7890
password: 0123456
dns:
  enable: true
  nameserver: [1.1.1.1]
rules:
  - MATCH,DIRECT
`

func decode(t *testing.T, data []byte) map[string]any {
	t.Helper()
	var out map[string]any
	if err := yaml.Unmarshal(data, &out); err != nil {
		t.Fatalf("decode output: %v\n%s", err, data)
	}
	return out
}

func mustYAML(t *testing.T, base, override string) map[string]any {
	t.Helper()
	out, err := ApplyYAML([]byte(base), []byte(override))
	if err != nil {
		t.Fatalf("ApplyYAML: %v", err)
	}
	return decode(t, out)
}

func TestYAMLDeepMergeKeepsSiblings(t *testing.T) {
	out := mustYAML(t, baseConfig, "dns:\n  ipv6: true\n")
	dns := out["dns"].(map[string]any)
	if dns["enable"] != true || dns["ipv6"] != true {
		t.Fatalf("dns = %v", dns)
	}
	if out["mixed-port"] != 7890 {
		t.Fatalf("mixed-port = %v", out["mixed-port"])
	}
}

func TestYAMLForceReplace(t *testing.T) {
	out := mustYAML(t, baseConfig, "dns!:\n  ipv6: true\n")
	if !reflect.DeepEqual(out["dns"], map[string]any{"ipv6": true}) {
		t.Fatalf("dns = %v", out["dns"])
	}
}

func TestYAMLLiteralKey(t *testing.T) {
	out := mustYAML(t, baseConfig, "<+rules>: [x]\n")
	if _, ok := out["+rules"]; !ok {
		t.Fatalf("literal key missing: %v", out)
	}
}

func TestYAMLPrependAndAppend(t *testing.T) {
	out := mustYAML(t, baseConfig, "+rules: ['DOMAIN,a.com,PROXY']\nrules+: ['DOMAIN,b.com,PROXY']\n")
	want := []any{"DOMAIN,a.com,PROXY", "MATCH,DIRECT", "DOMAIN,b.com,PROXY"}
	if !reflect.DeepEqual(out["rules"], want) {
		t.Fatalf("rules = %v", out["rules"])
	}
}

func TestYAMLAppendToMissingOrNull(t *testing.T) {
	out := mustYAML(t, "proxies:\nrules: []\n", "proxies+: [a]\n+missing: [b]\n")
	if !reflect.DeepEqual(out["proxies"], []any{"a"}) || !reflect.DeepEqual(out["missing"], []any{"b"}) {
		t.Fatalf("out = %v", out)
	}
}

func TestYAMLAppendToScalarFails(t *testing.T) {
	if _, err := ApplyYAML([]byte(baseConfig), []byte("mixed-port+: [1]\n")); err == nil {
		t.Fatal("expected error")
	}
}

func TestYAMLEmptyOverrideIsNoop(t *testing.T) {
	for _, over := range []string{"", "   \n", "null\n", "~\n"} {
		out, err := ApplyYAML([]byte(baseConfig), []byte(over))
		if err != nil || string(out) != baseConfig {
			t.Fatalf("override %q: err=%v", over, err)
		}
	}
}

func TestYAMLNonMappingOverrideFails(t *testing.T) {
	if _, err := ApplyYAML([]byte(baseConfig), []byte("- a\n")); err == nil {
		t.Fatal("expected error")
	}
}

func TestYAMLKeepsScalarText(t *testing.T) {
	out, err := ApplyYAML([]byte(baseConfig), []byte("log-level: info\n"))
	if err != nil {
		t.Fatal(err)
	}
	var cfg struct {
		Password string `yaml:"password"`
	}
	if err := yaml.Unmarshal(out, &cfg); err != nil || cfg.Password != "0123456" {
		t.Fatalf("password = %q, err=%v", cfg.Password, err)
	}
}

func TestYAMLReplacingAnchorKeepsAliasesValid(t *testing.T) {
	base := "dns: &d {enable: true}\nfallback-dns: *d\n"
	out := mustYAML(t, base, "dns!: {enable: false}\n")
	if !reflect.DeepEqual(out["fallback-dns"], map[string]any{"enable": true}) {
		t.Fatalf("fallback-dns = %v", out["fallback-dns"])
	}
	if !reflect.DeepEqual(out["dns"], map[string]any{"enable": false}) {
		t.Fatalf("dns = %v", out["dns"])
	}
}

func TestYAMLAliasBombRejected(t *testing.T) {
	var b strings.Builder
	b.WriteString("a0: &a0 [x, x, x, x, x, x, x, x, x, x]\n")
	for i := 1; i < 9; i++ {
		b.WriteString("a" + string(rune('0'+i)) + ": &a" + string(rune('0'+i)) + " [")
		for j := 0; j < 10; j++ {
			if j > 0 {
				b.WriteString(", ")
			}
			b.WriteString("*a" + string(rune('0'+i-1)))
		}
		b.WriteString("]\n")
	}
	if _, err := ApplyYAML([]byte(b.String()), []byte("x: 1\n")); err == nil {
		t.Fatal("expected error")
	}
}

func mustJS(t *testing.T, base, script string) map[string]any {
	t.Helper()
	out, err := ApplyJS([]byte(base), script)
	if err != nil {
		t.Fatalf("ApplyJS: %v", err)
	}
	return decode(t, out)
}

func TestJSMainFunction(t *testing.T) {
	out := mustJS(t, baseConfig, `function main(config) {
  config.rules.unshift("DOMAIN,a.com,PROXY");
  config["log-level"] = "debug";
  console.log(config);
  return config;
}`)
	if out["log-level"] != "debug" || len(out["rules"].([]any)) != 2 {
		t.Fatalf("out = %v", out)
	}
}

func TestJSConstArrowMain(t *testing.T) {
	out := mustJS(t, baseConfig, `const main = (config) => ({ ...config, mode: "global" });`)
	if out["mode"] != "global" {
		t.Fatalf("out = %v", out)
	}
}

func TestJSKeepsLeadingZeroStrings(t *testing.T) {
	out, err := ApplyJS([]byte(baseConfig), `function main(c) { return c; }`)
	if err != nil {
		t.Fatal(err)
	}
	var cfg struct {
		Password  string `yaml:"password"`
		MixedPort int    `yaml:"mixed-port"`
	}
	if err := yaml.Unmarshal(out, &cfg); err != nil || cfg.Password != "0123456" || cfg.MixedPort != 7890 {
		t.Fatalf("cfg = %+v, err=%v\n%s", cfg, err, out)
	}
}

func TestJSResolvesMergeKeys(t *testing.T) {
	base := "base: &b {type: select, url: x}\ngroup: {<<: *b, name: g, type: url-test}\n"
	out := mustJS(t, base, `function main(c) { return c; }`)
	want := map[string]any{"type": "url-test", "url": "x", "name": "g"}
	if !reflect.DeepEqual(out["group"], want) {
		t.Fatalf("group = %v", out["group"])
	}
}

func TestCyclicAliasesRejected(t *testing.T) {
	for _, base := range []string{"a: &a [*a]\n", "a: &a {<<: *a}\n"} {
		if _, err := ApplyYAML([]byte(base), []byte("x: 1\n")); err == nil {
			t.Fatal("expected YAML alias cycle error")
		}
		if _, err := ApplyJS([]byte(base), `function main(c) { return c; }`); err == nil {
			t.Fatal("expected JS input alias cycle error")
		}
	}
}

func TestJSOutputLimit(t *testing.T) {
	_, err := ApplyJS([]byte(baseConfig), `function main(c) { c.large = "x".repeat(1024 * 1024); return c; }`)
	if err == nil || !strings.Contains(err.Error(), "too large") {
		t.Fatalf("expected output limit error, got %v", err)
	}
}

func TestJSErrors(t *testing.T) {
	cases := map[string]string{
		"missing main": `var x = 1;`,
		"syntax":       `function main(c) { return c`,
		"throws":       `function main(c) { throw new Error("boom"); }`,
		"undefined":    `function main(c) {}`,
		"array":        `function main(c) { return [1]; }`,
	}
	for name, script := range cases {
		if _, err := ApplyJS([]byte(baseConfig), script); err == nil {
			t.Errorf("%s: expected error", name)
		}
	}
}

func TestJSTimeout(t *testing.T) {
	start := time.Now()
	_, err := ApplyJS([]byte(baseConfig), `function main(c) { for (;;) {} }`)
	if err == nil || time.Since(start) > 5*time.Second {
		t.Fatalf("err=%v elapsed=%v", err, time.Since(start))
	}
}

func TestApplyInOrder(t *testing.T) {
	dir := t.TempDir()
	write := func(name, content string) string {
		path := filepath.Join(dir, name)
		if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
		return path
	}
	specs := []Spec{
		{Name: "first", Format: FormatYAML, Path: write("a.yaml", "mode: global\n")},
		{Name: "second", Format: FormatJS, Path: write("b.js", `function main(c) { c.mode += "-js"; return c; }`)},
	}
	out, err := Apply([]byte(baseConfig), specs)
	if err != nil {
		t.Fatal(err)
	}
	if got := decode(t, out)["mode"]; got != "global-js" {
		t.Fatalf("mode = %v", got)
	}

	specs = append(specs, Spec{Name: "broken", Format: FormatYAML, Path: write("c.yaml", "- a\n")})
	if _, err := Apply([]byte(baseConfig), specs); err == nil || !strings.Contains(err.Error(), "broken") {
		t.Fatalf("err = %v", err)
	}
}
