package e2e

import (
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"

	"github.com/Fukuemon/depwalk/core/internal/analyze"
	"github.com/Fukuemon/depwalk/core/internal/analyzer"
	"github.com/Fukuemon/depwalk/core/internal/graph"
	"github.com/Fukuemon/depwalk/core/internal/protocol"
)

func TestJavaAnalyzerSpringFixtureE2E(t *testing.T) {
	javaPath := findJava25(t)
	jarPath := findAnalyzerJar(t)
	projectRoot := filepath.Join(fixtureRoot(t), "spring-project")
	sourceRoot := filepath.Join(projectRoot, "src", "main", "java")
	classpath := loadDepwalkClasspath(t, filepath.Join(projectRoot, "build", "depwalk-classpath.txt"))
	metadataPairs := make([]string, 0, len(classpath))
	for _, entry := range classpath {
		metadataPairs = append(metadataPairs, "classpath="+entry)
	}
	metadataPairs = append(metadataPairs, "javaLanguageLevel=25")
	metadata, err := analyze.BuildMetadata(metadataPairs)
	if err != nil {
		t.Fatalf("build Spring fixture analyzer metadata: %v", err)
	}

	request := protocol.AnalysisRequest{
		SchemaVersion: protocol.SchemaVersion,
		RecordType:    protocol.RecordTypeAnalysisRequest,
		RequestID:     "e2e-spring-fixture",
		WorkspaceRoot: sourceRoot,
		SourceRoots:   []string{"."},
		Language:      protocol.LanguageJava,
		Metadata:      metadata,
	}
	if err := request.Validate(); err != nil {
		t.Fatalf("Spring fixture analysisRequest is invalid: %v", err)
	}
	runner := analyzer.New(analyzer.Command{Path: javaPath, Args: []string{"-jar", jarPath}})
	var records []protocol.Record
	runResult, err := runner.Run(request, func(record protocol.Record) { records = append(records, record) })
	if err != nil {
		t.Fatalf("run Spring fixture analyzer: %v", err)
	}
	if runResult.ValidationError != nil || runResult.AnalyzerError != nil || runResult.ExitCode != 0 {
		t.Fatalf("Spring fixture analyzer failed: validation=%v analyzer=%v exit=%d stderr=%s",
			runResult.ValidationError, runResult.AnalyzerError, runResult.ExitCode, runResult.Stderr)
	}

	var edges []protocol.CallEdge
	for _, record := range records {
		if edge, ok := record.(protocol.CallEdge); ok {
			edges = append(edges, edge)
		}
	}

	t.Run("AnalyzerJSONLCandidates", func(t *testing.T) {
		assertSpringCandidateEdge(t, edges,
			"java:com.example.springfixture.LombokCheckoutService#checkout()",
			"java:com.example.springfixture.PaypalPayment#pay()",
			"unique", []string{"sootup", "spring-di"})
		assertSpringCandidateEdge(t, edges,
			"java:com.example.springfixture.FieldCheckoutService#checkout()",
			"java:com.example.springfixture.StripePayment#pay()",
			"unique", []string{"sootup", "spring-di"})
		assertSpringCandidateEdge(t, edges,
			"java:com.example.springfixture.SetterCheckoutService#checkout()",
			"java:com.example.springfixture.PaypalPayment#pay()",
			"unique", []string{"sootup", "spring-di"})
		assertSpringCandidateEdge(t, edges,
			"java:com.example.springfixture.ConfiguredCheckoutService#checkout()",
			"java:com.example.springfixture.StripePayment#pay()",
			"unique", []string{"sootup", "spring-di"})
		for _, callee := range []string{
			"java:com.example.springfixture.FileAuditService#audit()",
			"java:com.example.springfixture.DatabaseAuditService#audit()",
		} {
			assertSpringCandidateEdge(t, edges,
				"java:com.example.springfixture.AuditRunner#run()",
				callee,
				"ambiguous", []string{"sootup", "spring-di"})
		}

		assertSpringCandidateCallees(t, edges,
			"java:com.example.springfixture.LombokCheckoutService#checkout()",
			[]string{"java:com.example.springfixture.PaypalPayment#pay()"})
		assertSpringCandidateCallees(t, edges,
			"java:com.example.springfixture.FieldCheckoutService#checkout()",
			[]string{"java:com.example.springfixture.StripePayment#pay()"})
		assertSpringCandidateCallees(t, edges,
			"java:com.example.springfixture.SetterCheckoutService#checkout()",
			[]string{"java:com.example.springfixture.PaypalPayment#pay()"})
		assertSpringCandidateCallees(t, edges,
			"java:com.example.springfixture.ConfiguredCheckoutService#checkout()",
			[]string{"java:com.example.springfixture.StripePayment#pay()"})
		assertSpringCandidateCallees(t, edges,
			"java:com.example.springfixture.AuditRunner#run()",
			[]string{
				"java:com.example.springfixture.DatabaseAuditService#audit()",
				"java:com.example.springfixture.FileAuditService#audit()",
			})
	})

	t.Run("ConditionalMetadata", func(t *testing.T) {
		edge, ok := findCallEdge(edges,
			"java:com.example.springfixture.NotificationRunner#run()",
			"java:com.example.springfixture.ProfileNotificationService#notifyUser()")
		if !ok {
			t.Fatal("conditional implementation edge not found")
		}
		if !metadataBool(edge.Metadata, "conditional") {
			t.Fatal("conditional metadata is not true")
		}
		conditionTypes, ok := edge.Metadata["conditionTypes"].([]any)
		if !ok || len(conditionTypes) == 0 {
			t.Fatalf("conditionTypes metadata missing: %#v", edge.Metadata)
		}
	})

	t.Run("Diagnostics", func(t *testing.T) {
		assertDiagnosticCode(t, runResult.Diagnostics, "JAVA_AMBIGUOUS_CANDIDATE")
		assertDiagnosticCode(t, runResult.Diagnostics, "JAVA_CONDITIONAL_BEAN")
		assertDiagnosticCode(t, runResult.Diagnostics, "JAVA_RUNTIME_PROVIDED")
	})

	t.Run("CoreGraphCallerCallee", func(t *testing.T) {
		callGraph := graph.New()
		for _, record := range records {
			switch typed := record.(type) {
			case protocol.MethodSymbol:
				callGraph.AddNode(graph.NodeFromMethodSymbol(typed))
			case protocol.CallEdge:
				callGraph.AddEdge(graph.EdgeFromCallEdge(typed))
			}
		}
		caller := "java:com.example.springfixture.LombokCheckoutService#checkout()"
		callee := "java:com.example.springfixture.PaypalPayment#pay()"
		found := false
		for _, edge := range callGraph.Neighbors(caller, graph.DirectionCallee) {
			if edge.CalleeID == callee {
				found = true
				break
			}
		}
		if !found {
			t.Fatalf("Core graph missing %s -> %s", caller, callee)
		}
	})
}

func loadDepwalkClasspath(t *testing.T, manifest string) []string {
	t.Helper()
	data, err := os.ReadFile(manifest)
	if err != nil {
		skipOrFail(t, "Spring fixture classpath manifest unavailable: %v; run the fixture writeDepwalkClasspath task", err)
	}
	var entries []string
	for _, line := range strings.Split(string(data), "\n") {
		entry := strings.TrimSpace(line)
		if entry == "" {
			continue
		}
		if !filepath.IsAbs(entry) {
			t.Fatalf("Spring fixture classpath entry is not absolute: %s", entry)
		}
		if _, err := os.Stat(entry); err != nil {
			skipOrFail(t, "Spring fixture classpath entry unavailable: %s: %v", entry, err)
		}
		entries = append(entries, entry)
	}
	if len(entries) == 0 {
		skipOrFail(t, "Spring fixture classpath manifest is empty: %s", manifest)
	}
	if !sort.StringsAreSorted(entries) {
		t.Fatalf("Spring fixture classpath manifest is not sorted: %v", entries)
	}
	seen := map[string]struct{}{}
	for _, entry := range entries {
		if _, exists := seen[entry]; exists {
			t.Fatalf("Spring fixture classpath manifest contains duplicate entry: %s", entry)
		}
		seen[entry] = struct{}{}
	}
	return entries
}

func assertSpringCandidateEdge(
	t *testing.T,
	edges []protocol.CallEdge,
	caller string,
	callee string,
	wantResolution string,
	wantProvenance []string,
) {
	t.Helper()
	edge, ok := findCallEdge(edges, caller, callee)
	if !ok {
		t.Fatalf("candidate edge not found: %s -> %s", caller, callee)
	}
	resolution, _ := metadataString(edge.Metadata, "resolution")
	if resolution != wantResolution {
		t.Errorf("resolution for %s -> %s = %q, want %q", caller, callee, resolution, wantResolution)
	}
	rawProvenance, ok := edge.Metadata["provenance"].([]any)
	if !ok {
		t.Fatalf("provenance missing for %s -> %s: %#v", caller, callee, edge.Metadata)
	}
	provenance := make([]string, 0, len(rawProvenance))
	for _, raw := range rawProvenance {
		value, ok := raw.(string)
		if !ok {
			t.Fatalf("non-string provenance for %s -> %s: %#v", caller, callee, raw)
		}
		provenance = append(provenance, value)
	}
	if strings.Join(provenance, "\x00") != strings.Join(wantProvenance, "\x00") {
		t.Errorf("provenance for %s -> %s = %v, want %v", caller, callee, provenance, wantProvenance)
	}
}

func assertSpringCandidateCallees(
	t *testing.T,
	edges []protocol.CallEdge,
	caller string,
	want []string,
) {
	t.Helper()
	var got []string
	for _, edge := range edges {
		if edge.CallerMethodID != caller {
			continue
		}
		if _, ok := metadataString(edge.Metadata, "resolution"); !ok {
			continue
		}
		got = append(got, edge.CalleeMethodID)
	}
	sort.Strings(got)
	want = append([]string(nil), want...)
	sort.Strings(want)
	if strings.Join(got, "\x00") != strings.Join(want, "\x00") {
		t.Errorf("candidate callees for %s = %v, want %v", caller, got, want)
	}
}
