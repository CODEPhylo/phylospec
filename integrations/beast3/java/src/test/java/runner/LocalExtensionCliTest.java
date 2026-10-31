package runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses JARs compiled during the test, so the adapter is absent from the initial classpath. */
class LocalExtensionCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String LIBRARY_ID = "local-fixture";
    private static final String MODEL = """
            use fixture.distributions
            Real x ~ CustomNormal(mean=0.0, sd=1.0)
            mcmc {
                Integer chainLength = 30
                Integer defaultLogEvery = 10
                Integer randomSeed = 42
            }
            """;

    @Test
    void loadingMakesAnAdapterAvailableButSelectionRemainsExplicitAndScoped(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path source = Files.writeString(directory.resolve("custom.phylospec"), MODEL);
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        CliResult absent = invoke("check", source.toString(), "--library", LIBRARY_ID);
        assertEquals(1, absent.exitCode(), absent.stderr());
        assertTrue(absent.stderr().contains(LIBRARY_ID), absent.stderr());

        CliResult unselected = invoke(
                "check", source.toString(), "--adapter-path", fixture.bundle().toString());
        assertEquals(1, unselected.exitCode(), unselected.stderr());
        assertTrue(unselected.stderr().contains("fixture.distributions"), unselected.stderr());
        assertSame(original, Thread.currentThread().getContextClassLoader());

        CliResult selected = invoke(
                "check", source.toString(), "--adapter-path", fixture.bundle().toString(), "--library", LIBRARY_ID);
        assertEquals(0, selected.exitCode(), selected.stderr());
        assertTrue(selected.stdout().contains("libraries: local-fixture,beast2"), selected.stdout());
        assertTrue(selected.stdout().contains("No MCMC was run."), selected.stdout());
        assertSame(original, Thread.currentThread().getContextClassLoader());
        assertFalse(Files.exists(directory.resolve("runs")));

        CliResult later = invoke("check", source.toString(), "--library", LIBRARY_ID);
        assertEquals(1, later.exitCode(), later.stderr());
        assertSame(original, Thread.currentThread().getContextClassLoader());
    }

    @Test
    void selectedLocalModelRunsAndRecordsItsActualProvidersAndJarFingerprints(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Path source = Files.writeString(directory.resolve("custom.phylospec"), MODEL);
        Path dependencies = Files.createDirectory(directory.resolve("separate dependencies"));
        Path helper = Files.move(
                fixture.helperJar(), dependencies.resolve(fixture.helperJar().getFileName()));
        Path run = directory.resolve("results");

        // Both repeatable paths are resolved from the command's working directory, including spaces.
        CliResult result = launch(
                directory,
                "run",
                source.getFileName().toString(),
                "--adapter-path",
                fixture.bundle().getFileName().toString(),
                "--adapter-path",
                dependencies.getFileName().toString(),
                "--library",
                LIBRARY_ID,
                "--out",
                run.getFileName().toString());
        assertEquals(0, result.exitCode(), result.stderr() + "\n" + result.stdout());
        assertTrue(result.stdout().contains("Run completed"), result.stdout());
        JsonNode record = JSON.readTree(run.resolve("run.json").toFile());
        assertEquals("succeeded", record.path("status").asText(), record.toPrettyString());
        assertEquals(MODEL, Files.readString(run.resolve("model.phylospec")));
        JsonNode provider = record.path("libraries").get(0);
        assertEquals(LIBRARY_ID, provider.path("id").asText());
        assertEquals("fixture.adapter.FixtureLibrary", provider.path("class").asText());
        assertEquals("1.2.3-fixture", provider.path("version").asText());
        assertEquals(
                fixture.adapterJar().toRealPath().toUri().toURL().toExternalForm(),
                provider.path("location").asText());
        assertEquals("beast2", record.path("libraries").get(1).path("id").asText());
        assertTrue(record.path("components").toString().contains("Local fixture components"));
        assertTrue(record.path("components").toString().contains("2.0-fixture"));
        JsonNode bundles = record.path("runtime").path("adapterBundles");
        assertEquals(2, bundles.size(), record.toPrettyString());
        assertBundle(bundles.get(0), fixture.bundle(), fixture.adapterJar());
        assertBundle(bundles.get(1), dependencies, helper);

        Path trace = run.resolve("output/custom.log");
        List<String> lines = Files.readAllLines(trace).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals(5, lines.size(), "Header plus samples 0, 10, 20, 30");
        for (int index = 1; index < lines.size(); index++) {
            String[] values = lines.get(index).trim().split("\\s+");
            assertEquals((index - 1) * 10, Integer.parseInt(values[0]));
            for (int column = 1; column < values.length; column++) {
                assertTrue(Double.isFinite(Double.parseDouble(values[column])), lines.get(index));
            }
        }
        assertTrue(Files.size(run.resolve("output/custom.state.xml")) > 0);
        assertTrue(Files.size(run.resolve("console.log")) > 0);
    }

    @Test
    void missingDependentJarFailsBeforeCreatingResultsAndRestoresContextLoader(@TempDir Path directory)
            throws Exception {
        Fixture fixture = fixture(directory);
        Files.delete(fixture.helperJar());
        Path source = Files.writeString(directory.resolve("custom.phylospec"), MODEL);
        Path run = directory.resolve("failed-run");
        ClassLoader original = Thread.currentThread().getContextClassLoader();

        for (String command : List.of("check", "run")) {
            List<String> arguments = new ArrayList<>(List.of(
                    command,
                    source.toString(),
                    "--adapter-path",
                    fixture.bundle().toString(),
                    "--library",
                    LIBRARY_ID,
                    "--debug"));
            if (command.equals("run")) arguments.addAll(List.of("--out", run.toString()));
            CliResult result = invoke(arguments.toArray(String[]::new));
            assertEquals(1, result.exitCode(), result.stderr());
            assertTrue(
                    result.stderr().contains("fixture/dependency/FixtureDependency")
                            || result.stderr().contains("fixture.dependency.FixtureDependency"),
                    result.stderr());
            assertSame(original, Thread.currentThread().getContextClassLoader());
            assertFalse(Files.exists(run));
            assertFalse(result.stdout().contains("Run completed"));
        }
    }

    @Test
    void addingABundleDoesNotReplaceTheDefaultBaseLibrary(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory);
        Path source = Files.writeString(directory.resolve("core.phylospec"), "Real x ~ Normal(mean=0.0, sd=1.0)");
        CliResult result = invoke(
                "check", source.toString(), "--adapter-path", fixture.bundle().toString());
        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("libraries: beast2"), result.stdout());
        assertFalse(Files.exists(directory.resolve("runs")));
    }

    @Test
    void invalidServiceRegistrationNamesTheMissingProvider(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory, "fixture.adapter.MissingLibrary\n", true);
        assertPreflightFailure(directory, fixture, "fixture.adapter.MissingLibrary", "not found");
    }

    @Test
    void duplicateLibraryIdsNameBothDistinctProviders(@TempDir Path directory) throws Exception {
        Fixture fixture =
                fixture(directory, "fixture.adapter.FixtureLibrary\nfixture.adapter.DuplicateLibrary\n", true);
        assertPreflightFailure(
                directory, fixture, LIBRARY_ID, "fixture.adapter.FixtureLibrary", "fixture.adapter.DuplicateLibrary");
    }

    @Test
    void missingDeclaredComponentMetadataNamesTheProviderAndResource(@TempDir Path directory) throws Exception {
        Fixture fixture = fixture(directory, "fixture.adapter.FixtureLibrary\n", false);
        assertPreflightFailure(directory, fixture, LIBRARY_ID, "/fixture-components.json", "not found");
    }

    private static void assertPreflightFailure(Path directory, Fixture fixture, String... expectedMessages)
            throws Exception {
        Path source = Files.writeString(directory.resolve("custom.phylospec"), MODEL);
        Path run = directory.resolve("failed-run");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        for (String command : List.of("check", "run")) {
            List<String> arguments = new ArrayList<>(List.of(
                    command,
                    source.toString(),
                    "--adapter-path",
                    fixture.bundle().toString(),
                    "--library",
                    LIBRARY_ID));
            if (command.equals("run")) arguments.addAll(List.of("--out", run.toString()));
            CliResult result = invoke(arguments.toArray(String[]::new));
            assertEquals(1, result.exitCode(), result.stderr());
            for (String expected : expectedMessages) {
                assertTrue(result.stderr().contains(expected), result.stderr());
            }
            assertFalse(result.stderr().contains("\tat "), result.stderr());
            assertFalse(result.stderr().contains("Usage:"), result.stderr());
            assertSame(original, Thread.currentThread().getContextClassLoader());
            assertFalse(Files.exists(run));
            assertFalse(Files.exists(directory.resolve("runs")));
            assertFalse(result.stdout().contains("Run completed"));
        }
    }

    private static void assertBundle(JsonNode bundle, Path directory, Path jar) throws Exception {
        assertEquals(directory.toRealPath().toString(), bundle.path("path").asText());
        assertEquals(1, bundle.path("jars").size(), bundle.toPrettyString());
        JsonNode entry = bundle.path("jars").get(0);
        assertEquals(jar.toRealPath().toString(), entry.path("path").asText());
        assertEquals(Files.size(jar), entry.path("size").asLong());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))),
                entry.path("sha256").asText());
    }

    private static Fixture fixture(Path directory) throws Exception {
        return fixture(directory, "fixture.adapter.FixtureLibrary\n", true);
    }

    private static Fixture fixture(Path directory, String serviceProviders, boolean includeComponents)
            throws Exception {
        Path sources = Files.createDirectories(directory.resolve("fixture-sources"));
        Path classes = Files.createDirectories(directory.resolve("fixture-classes"));
        Path bundle = Files.createDirectory(directory.resolve("local adapter bundle"));
        String librarySource = """
                package fixture.adapter;
                import beastconfig.BEASTState;
                import java.util.List;
                import org.phylospec.tiling.TileLibrary;
                import org.phylospec.tiling.tiles.CandidateTile;
                public final class FixtureLibrary extends TileLibrary<BEASTState> {
                    public String getId() { return fixture.dependency.FixtureDependency.libraryId(); }
                    public Class<BEASTState> getStateType() { return BEASTState.class; }
                    public List<CandidateTile<BEASTState>> getTiles() { return List.of(new CustomNormalTile()); }
                    public List<String> getComponentLibraryResources() { return List.of("/fixture-components.json"); }
                }
                """;
        Map<String, String> sourceFiles = Map.of(
                "fixture/dependency/FixtureDependency.java",
                """
                package fixture.dependency;
                public final class FixtureDependency {
                    public static String libraryId() { return "local-fixture"; }
                }
                """,
                "fixture/adapter/FixtureLibrary.java",
                librarySource,
                "fixture/adapter/DuplicateLibrary.java",
                librarySource.replace("FixtureLibrary", "DuplicateLibrary"),
                "fixture/adapter/CustomNormalTile.java",
                """
                package fixture.adapter;
                import beast.base.spec.domain.PositiveReal;
                import beast.base.spec.domain.Real;
                import beast.base.spec.inference.distribution.Normal;
                import beast.base.spec.inference.parameter.RealScalarParam;
                import beast.base.spec.type.RealScalar;
                import beastconfig.BEASTState;
                import beastconfig.OperatorSelector;
                import java.util.IdentityHashMap;
                import java.util.Optional;
                import org.phylospec.ast.Expr;
                import org.phylospec.tiling.tiles.GeneratorTile;
                import tiling.BoundDistribution;
                public final class CustomNormalTile
                        extends GeneratorTile<BoundDistribution<RealScalarParam<Real>, Normal>, BEASTState> {
                    public String getPhyloSpecGeneratorName() { return "CustomNormal"; }
                    public Optional<String> getNamespace() { return Optional.of("fixture.distributions"); }
                    GeneratorTileInput<RealScalar<Real>, BEASTState> meanInput = new GeneratorTileInput<>("mean");
                    GeneratorTileInput<RealScalar<PositiveReal>, BEASTState> sdInput = new GeneratorTileInput<>("sd");
                    public BoundDistribution<RealScalarParam<Real>, Normal> applyTile(
                            BEASTState state, IdentityHashMap<Expr.Variable, Integer> indices) {
                        Normal distribution = new Normal();
                        state.setInput(distribution, distribution.meanInput, meanInput.apply(state, indices));
                        state.setInput(distribution, distribution.sdInput, sdInput.apply(state, indices));
                        return new BoundDistribution<>(distribution, new RealScalarParam<>(0.1, Real.INSTANCE),
                                parameter -> state.setInput(distribution, distribution.paramInput, parameter),
                                OperatorSelector::getDefaultOperators);
                    }
                }
                """);
        List<String> compilerArguments =
                new ArrayList<>(List.of("-proc:none", "-classpath", classpath(), "-d", classes.toString()));
        for (var source : sourceFiles.entrySet()) {
            Path file = sources.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            compilerArguments.add(file.toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The extension fixture requires a JDK, as does this Maven build.");
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        assertEquals(
                0,
                compiler.run(null, diagnostics, diagnostics, compilerArguments.toArray(String[]::new)),
                diagnostics.toString(StandardCharsets.UTF_8));
        Path adapter = bundle.resolve("fixture-adapter.jar");
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("META-INF/services/org.phylospec.tiling.TileLibrary", serviceProviders);
        if (includeComponents) {
            resources.put("fixture-components.json", """
                        {
                          "componentLibrary": {
                            "name": "Local fixture components", "version": "2.0-fixture", "types": [],
                            "generators": [{
                              "name": "CustomNormal", "namespace": "fixture.distributions",
                              "generatedType": "Distribution<Real>",
                              "arguments": [
                                {"name": "mean", "type": "Real", "required": true},
                                {"name": "sd", "type": "PositiveReal", "required": true}
                              ]
                            }]
                          }
                        }
                        """);
        }
        createJar(
                adapter,
                classes,
                List.of(
                        "fixture/adapter/FixtureLibrary.class",
                        "fixture/adapter/DuplicateLibrary.class",
                        "fixture/adapter/CustomNormalTile.class"),
                resources);
        Path helper = bundle.resolve("fixture-helper.jar");
        createJar(helper, classes, List.of("fixture/dependency/FixtureDependency.class"), Map.of());
        return new Fixture(bundle, adapter, helper);
    }

    private static void createJar(Path jar, Path classes, List<String> classFiles, Map<String, String> resources)
            throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "1.2.3-fixture");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (String name : classFiles) {
                output.putNextEntry(new JarEntry(name));
                Files.copy(classes.resolve(name), output);
                output.closeEntry();
            }
            for (var resource : resources.entrySet()) {
                output.putNextEntry(new JarEntry(resource.getKey()));
                output.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private record Fixture(Path bundle, Path adapterJar, Path helperJar) {}

    private record CliResult(int exitCode, String stdout, String stderr) {}

    private static CliResult invoke(String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            int code = PhyloSpecCli.run(arguments, out, err);
            return new CliResult(
                    code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
        }
    }

    private static String classpath() {
        return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    }

    private static CliResult launch(Path directory, String... arguments) throws Exception {
        Path stdout = directory.resolve("cli.stdout");
        Path stderr = directory.resolve("cli.stderr");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.only=true",
                "-cp",
                classpath(),
                "runner.PhyloSpecCli"));
        command.addAll(List.of(arguments));
        Process child = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Local-extension run timed out");
            return new CliResult(child.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
            }
        }
    }
}
