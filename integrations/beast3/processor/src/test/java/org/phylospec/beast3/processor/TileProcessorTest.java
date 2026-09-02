package org.phylospec.beast3.processor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TileProcessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    public void rejectsUnknownComponent() throws IOException {
        CompilationResult result =
                compile(
                        """
                        package mappings;

                        import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
                        import org.phylospec.annotations.GeneratorMapping;

                        @GeneratorMapping(
                                component = "phylospec.functions.coalescent.unknown",
                                implementation = ConstantPopulation.class)
                        public interface InvalidMapping {}
                        """);

        assertCompilationError(
                result,
                "Unknown PhyloSpec component "
                        + "'phylospec.functions.coalescent.unknown'.");
    }

    @Test
    public void rejectsUnknownArgument() throws IOException {
        CompilationResult result =
                compile(
                        """
                        package mappings;

                        import beast.base.spec.domain.PositiveReal;
                        import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
                        import beast.base.spec.type.RealScalar;
                        import org.phylospec.annotations.GeneratorMapping;
                        import org.phylospec.annotations.InputMapping;

                        @GeneratorMapping(
                                component = "phylospec.functions.coalescent.constantPopulationFunction",
                                implementation = ConstantPopulation.class)
                        public interface InvalidMapping {

                            @InputMapping(
                                    argument = "unknownArgument",
                                    input = "popSizeParameter")
                            RealScalar<? extends PositiveReal> populationSize();
                        }
                        """);

        assertCompilationError(
                result,
                "Unknown PhyloSpec argument 'unknownArgument'.");
    }

    @Test
    public void rejectsUnknownBeastInput() throws IOException {
        CompilationResult result =
                compile(
                        """
                        package mappings;

                        import beast.base.spec.domain.PositiveReal;
                        import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
                        import beast.base.spec.type.RealScalar;
                        import org.phylospec.annotations.GeneratorMapping;
                        import org.phylospec.annotations.InputMapping;

                        @GeneratorMapping(
                                component = "phylospec.functions.coalescent.constantPopulationFunction",
                                implementation = ConstantPopulation.class)
                        public interface InvalidMapping {

                            @InputMapping(
                                    argument = "populationSize",
                                    input = "missingInput")
                            RealScalar<? extends PositiveReal> populationSize();
                        }
                        """);

        assertCompilationError(
                result,
                "has no input field named 'missingInput'.");
    }

    @Test
    public void rejectsIncompatibleInputType() throws IOException {
        CompilationResult result =
                compile(
                        """
                        package mappings;

                        import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
                        import org.phylospec.annotations.GeneratorMapping;
                        import org.phylospec.annotations.InputMapping;

                        @GeneratorMapping(
                                component = "phylospec.functions.coalescent.constantPopulationFunction",
                                implementation = ConstantPopulation.class)
                        public interface InvalidMapping {

                            @InputMapping(
                                    argument = "populationSize",
                                    input = "popSizeParameter")
                            String populationSize();
                        }
                        """);

        assertCompilationError(
                result,
                "PhyloSpec argument produces Java type "
                        + "'java.lang.String'");

        assertCompilationError(
                result,
                "BEAST input 'popSizeParameter' expects");
    }

    @Test
    public void rejectsIncompatibleOutputType() throws IOException {
        CompilationResult result =
                compile(
                        """
                        package mappings;

                        import beast.base.spec.evolution.tree.coalescent.ConstantPopulation;
                        import org.phylospec.annotations.GeneratorMapping;

                        @GeneratorMapping(
                                component = "phylospec.functions.coalescent.constantPopulationFunction",
                                implementation = ConstantPopulation.class,
                                output = String.class)
                        public interface InvalidMapping {}
                        """);

        assertCompilationError(
                result,
                "cannot be returned as output type "
                        + "'java.lang.String'.");
    }

    @Test
    public void rejectsImplementationWithoutPublicNoArgumentConstructor()
            throws IOException {

        CompilationResult result =
                compile(
                        """
                        package mappings;

                        import org.phylospec.annotations.GeneratorMapping;

                        @GeneratorMapping(
                                component = "phylospec.functions.substitution.jc69",
                                implementation = InvalidMapping.Model.class)
                        public interface InvalidMapping {

                            final class Model {

                                public Model(String value) {}
                            }
                        }
                        """);

        assertCompilationError(
                result,
                "must declare a public no-argument constructor.");
    }

    @Test
    public void rejectsClassThatIsNotTypeAdapter()
            throws IOException {

        CompilationResult result =
                compile(
                        adapterMapping(
                                "NotAdapter",
                                """
                                public final class NotAdapter {

                                    public NotAdapter() {}
                                }
                                """));

        assertCompilationError(
                result,
                "must implement org.phylospec.tiling.TypeAdapter.");
    }

    @Test
    public void rejectsAdapterWithWrongSourceType()
            throws IOException {

        CompilationResult result =
                compile(
                        adapterMapping(
                                "WrongSourceAdapter",
                                """
                                public final class WrongSourceAdapter
                                        implements TypeAdapter<
                                                String,
                                                Frequencies,
                                                BEASTState> {

                                    public WrongSourceAdapter() {}

                                    @Override
                                    public Frequencies adapt(
                                            String value,
                                            BEASTState state) {

                                        return null;
                                    }
                                }
                                """));

        assertCompilationError(
                result,
                "accepts source type 'java.lang.String'");

        assertCompilationError(
                result,
                "produces 'beast.base.spec.type.Simplex'");
    }

    @Test
    public void rejectsAdapterWithWrongTargetType()
            throws IOException {

        CompilationResult result =
                compile(
                        adapterMapping(
                                "WrongTargetAdapter",
                                """
                                public final class WrongTargetAdapter
                                        implements TypeAdapter<
                                                Simplex,
                                                String,
                                                BEASTState> {

                                    public WrongTargetAdapter() {}

                                    @Override
                                    public String adapt(
                                            Simplex value,
                                            BEASTState state) {

                                        return "";
                                    }
                                }
                                """));

        assertCompilationError(
                result,
                "produces target type 'java.lang.String'");

        assertCompilationError(
                result,
                "BEAST input expects "
                        + "'beast.base.spec.evolution."
                        + "substitutionmodel.Frequencies'");
    }

    @Test
    public void rejectsAdapterWithWrongStateType()
            throws IOException {

        CompilationResult result =
                compile(
                        adapterMapping(
                                "WrongStateAdapter",
                                """
                                public final class WrongStateAdapter
                                        implements TypeAdapter<
                                                Simplex,
                                                Frequencies,
                                                String> {

                                    public WrongStateAdapter() {}

                                    @Override
                                    public Frequencies adapt(
                                            Simplex value,
                                            String state) {

                                        return null;
                                    }
                                }
                                """));

        assertCompilationError(
                result,
                "cannot accept BEASTState as its engine state.");
    }

    @Test
    public void rejectsAdapterWithoutPublicNoArgumentConstructor()
            throws IOException {

        CompilationResult result =
                compile(
                        adapterMapping(
                                "ConstructorAdapter",
                                """
                                public final class ConstructorAdapter
                                        implements TypeAdapter<
                                                Simplex,
                                                Frequencies,
                                                BEASTState> {

                                    public ConstructorAdapter(
                                            String value) {}

                                    @Override
                                    public Frequencies adapt(
                                            Simplex value,
                                            BEASTState state) {

                                        return null;
                                    }
                                }
                                """));

        assertCompilationError(
                result,
                "@InputMapping adapter must declare "
                        + "a public no-argument constructor.");
    }

    private String adapterMapping(
            String adapterName,
            String adapterDeclaration) {

        return """
                package mappings;

                import beast.base.spec.evolution.substitutionmodel.Frequencies;
                import beast.base.spec.evolution.substitutionmodel.WAG;
                import beast.base.spec.type.Simplex;
                import beastconfig.BEASTState;
                import org.phylospec.annotations.GeneratorMapping;
                import org.phylospec.annotations.InputMapping;
                import org.phylospec.tiling.TypeAdapter;

                @GeneratorMapping(
                        component = "phylospec.functions.substitution.wag",
                        implementation = WAG.class)
                public interface InvalidMapping {

                    @InputMapping(
                            argument = "baseFrequencies",
                            input = "frequenciesInput",
                            adapter = InvalidMapping.%s.class)
                    Simplex baseFrequencies();

                %s
                }
                """
                .formatted(
                        adapterName,
                        adapterDeclaration);
    }

    private CompilationResult compile(String source)
            throws IOException {

        JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();

        assertNotNull(
                compiler,
                "The tests must run with a JDK, not a JRE.");

        Path sourceDirectory =
                temporaryDirectory.resolve("src");

        Path classDirectory =
                temporaryDirectory.resolve("classes");

        Path generatedDirectory =
                temporaryDirectory.resolve("generated");

        Path sourceFile =
                sourceDirectory
                        .resolve("mappings")
                        .resolve("InvalidMapping.java");

        Path stateFile =
                sourceDirectory
                        .resolve("beastconfig")
                        .resolve("BEASTState.java");

        Files.createDirectories(
                sourceFile.getParent());

        Files.createDirectories(
                stateFile.getParent());

        Files.createDirectories(
                classDirectory);

        Files.createDirectories(
                generatedDirectory);

        Files.writeString(
                sourceFile,
                source);

        Files.writeString(
                stateFile,
                """
                package beastconfig;

                public final class BEASTState {}
                """);

        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(
                             diagnostics,
                             Locale.ROOT,
                             null)) {

            Iterable<? extends JavaFileObject> compilationUnits =
                    fileManager.getJavaFileObjects(
                            sourceFile.toFile(),
                            stateFile.toFile());

            List<String> options =
                    List.of(
                            "-classpath",
                            System.getProperty(
                                    "java.class.path"),
                            "-d",
                            classDirectory.toString(),
                            "-s",
                            generatedDirectory.toString(),
                            "-proc:only");

            JavaCompiler.CompilationTask task =
                    compiler.getTask(
                            null,
                            fileManager,
                            diagnostics,
                            options,
                            null,
                            compilationUnits);

            task.setProcessors(
                    List.of(
                            new TileProcessor()));

            boolean success =
                    Boolean.TRUE.equals(
                            task.call());

            List<String> errors =
                    diagnostics.getDiagnostics().stream()
                            .filter(
                                    diagnostic ->
                                            diagnostic.getKind()
                                                    == Diagnostic.Kind.ERROR)
                            .map(
                                    diagnostic ->
                                            diagnostic.getMessage(
                                                    Locale.ROOT))
                            .toList();

            return new CompilationResult(
                    success,
                    errors);
        }
    }

    private void assertCompilationError(
            CompilationResult result,
            String expectedMessage) {

        assertFalse(
                result.success(),
                "Expected compilation to fail.");

        assertTrue(
                result.errors().stream()
                        .anyMatch(
                                error ->
                                        error.contains(
                                                expectedMessage)),
                () ->
                        "Expected an error containing:\n"
                                + expectedMessage
                                + "\n\nActual errors:\n"
                                + String.join(
                                "\n",
                                result.errors()));
    }

    private record CompilationResult(
            boolean success,
            List<String> errors) {}
}