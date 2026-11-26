package org.phylospec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.errors.Diagnostic;
import org.phylospec.workspace.Workspace;

public class ValidateToolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private List<Diagnostic> validate(String source) throws IOException {
        ComponentResolver resolver = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        return new Validator(resolver, new Workspace()).validate(source).diagnostics();
    }

    /** Runs the validate tool and returns its exit code, stdout and stderr. */
    private record Run(int exitCode, String out, String err) {}

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Tools.validate(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Path writeModel(String source) throws IOException {
        Path file = tempDir.resolve("model.phylospec");
        Files.writeString(file, source);
        return file;
    }

    @Test
    public void validModelHasNoDiagnostics() throws IOException {
        assertTrue(validate("Real x ~ Normal(mean=0, sd=1)\n").isEmpty());
    }

    @Test
    public void unsupportedGeneratorsAreWarningsWhenAnEngineIsGiven() throws IOException {
        EngineSpecificationSchema engine;
        try (InputStream stream = getClass().getResourceAsStream("/test-engine-specification.json")) {
            engine = MAPPER.readValue(stream, EngineSpecificationSchema.class);
        }
        ComponentResolver resolver = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());

        // the test engine does not implement Normal
        Validator.Result result =
                new Validator(resolver, new Workspace(), List.of(engine)).validate("Real x ~ Normal(mean=0, sd=1)\n");

        assertEquals(1, result.diagnostics().size());
        assertFalse(result.diagnostics().getFirst().isError());
        assertEquals(1, result.diagnostics().getFirst().line());
    }

    @Test
    public void resultKeepsTheAnalysisForEditorFeatures() throws IOException {
        ComponentResolver resolver = new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        Validator.Result result =
                new Validator(resolver, new Workspace()).validate("Real x ~ Normal(mean=0, sd=1)\nReal y = x * 2\n");

        assertEquals(2, result.statements().size());
        assertFalse(result.tokens().isEmpty());
        assertFalse(result.typeResolver().resolveVariable("y").isEmpty());
        assertTrue(result.parser().getRangeForAstNode(result.statements().getFirst()) != null);
    }

    @Test
    public void syntaxErrorsAreReported() throws IOException {
        List<Diagnostic> diagnostics = validate("Real x ~ Normal(mean=0, sd=1\n");

        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.getFirst().isError());
        assertEquals(1, diagnostics.getFirst().line());
    }

    @Test
    public void typeErrorsInSeparateStatementsAreAllReported() throws IOException {
        List<Diagnostic> diagnostics = validate("""
                Real x ~ Normal(mean=0, sd=1)
                Real y ~ Foo(a=1)
                Real z ~ Bar(b=2)
                """);

        assertEquals(2, diagnostics.size());
        assertEquals(2, diagnostics.get(0).line());
        assertEquals(3, diagnostics.get(1).line());
        assertTrue(diagnostics.get(0).message().contains("Foo"));
    }

    @Test
    public void jsonReportForValidModel() throws IOException {
        Path file = writeModel("Real x ~ Normal(mean=0, sd=1)\n");

        Run run = run("validate", "--json", file.toString());
        JsonNode report = MAPPER.readTree(run.out());

        assertEquals(0, run.exitCode());
        assertTrue(report.get("valid").asBoolean());
        assertEquals(0, report.get("errorCount").asInt());
        assertEquals(0, report.get("diagnostics").size());
    }

    @Test
    public void jsonReportForInvalidModel() throws IOException {
        Path file = writeModel("Real x ~ Normal(mean=0, sd=1)\nReal y ~ Foo(a=1)\n");

        Run run = run("validate", file.toString(), "--json");
        JsonNode report = MAPPER.readTree(run.out());

        assertEquals(1, run.exitCode());
        assertFalse(report.get("valid").asBoolean());
        assertEquals(1, report.get("errorCount").asInt());

        JsonNode diagnostic = report.get("diagnostics").get(0);
        assertEquals("error", diagnostic.get("severity").asText());
        assertEquals(2, diagnostic.get("line").asInt());
        assertTrue(diagnostic.get("column").asInt() >= 1);
        assertTrue(
                diagnostic.get("endColumn").asInt() >= diagnostic.get("column").asInt());
        assertTrue(diagnostic.get("message").asText().contains("Foo"));
        assertTrue(diagnostic.has("hint"));
        assertTrue(diagnostic.get("examples").isArray());
    }

    @Test
    public void humanReadableOutputReportsErrors() throws IOException {
        Path file = writeModel("Real y ~ Foo(a=1)\n");

        Run run = run("validate", file.toString());

        assertEquals(1, run.exitCode());
        assertTrue(run.out().contains("Foo"));
    }

    @Test
    public void missingFileIsAUsageError() {
        Run run = run("validate", "--json", tempDir.resolve("missing.phylospec").toString());

        assertEquals(2, run.exitCode());
        assertTrue(run.out().isEmpty());
    }

    @Test
    public void missingArgumentIsAUsageError() {
        assertEquals(2, run("validate", "--json").exitCode());
        assertEquals(2, run("validate", "--unknown", "model.phylospec").exitCode());
    }
}
