package org.phylospec.tiling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class EngineSpecGenerator {

    /** Directory the generated engine specifications are written to, relative to the repo root. */
    private static final String GENERATED_DIRECTORY = "generated";

    /**
     * Generates an engine specification for the given tile library and engine metadata, and
     * writes it as JSON to "generated/<engine-name>-<engine-version>.json".
     */
    public static <S> void writeEngineSpecification(
            TileLibrary<S> tileLibrary,
            String engineName,
            String engineVersion,
            List<String> engineDependencies,
            String installationInstructions,
            String installationWebsite)
            throws IOException {
        EngineSpecificationSchema schema =
                generateEngineSpecification(
                        tileLibrary,
                        engineName,
                        engineVersion,
                        engineDependencies,
                        installationInstructions,
                        installationWebsite);

        Path generatedDirectory = Path.of(GENERATED_DIRECTORY);
        Files.createDirectories(generatedDirectory);

        File outputFile =
                generatedDirectory.resolve(engineName + "-" + engineVersion + ".json").toFile();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile, schema);
    }

    /**
     * Generates an engine specification for the given tile library and engine metadata.
     */
    public static <S> EngineSpecificationSchema generateEngineSpecification(
            TileLibrary<S> tileLibrary,
            String engineName,
            String engineVersion,
            List<String> engineDependencies,
            String installationInstructions,
            String installationWebsite) {
        ComponentResolver componentResolver;
        try {
            componentResolver =
                    new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());
        } catch (IOException e) {
            throw new RuntimeException("Could not load the core component library.", e);
        }

        EngineSpecificationSchema schema = new EngineSpecificationSchema();
        schema.setName(engineName);
        schema.setEngineVersion(engineVersion);
        schema.setDependsOn(engineDependencies);
        schema.setInstallationInstructions(installationInstructions);
        schema.setInstallationWebsite(installationWebsite);

        // multiple tiles can implement the same PhyloSpec generator with different Java type
        // bounds (e.g. an "x: Int" and an "x: Real" overload); those are indistinguishable from
        // the specification's point of view, so we deduplicate identical entries
        Set<Generator__1> generators = new LinkedHashSet<>();
        for (CandidateTile<S> candidateTile : tileLibrary.getTiles()) {
            if (!(candidateTile instanceof GeneratorTile<?, ?> generatorTile)) continue;

            generators.add(generateGeneratorSpecification(generatorTile, componentResolver));
        }
        schema.setGenerators(new ArrayList<>(generators));

        return schema;
    }

    /**
     * Builds the engine-specification entry for a single generator tile, looking up its namespace
     * and generated type from the core component library.
     */
    private static Generator__1 generateGeneratorSpecification(
            GeneratorTile<?, ?> generatorTile, ComponentResolver componentResolver) {
        String phyloSpecGeneratorName = generatorTile.getPhyloSpecGeneratorName();

        List<Generator> knownGenerators =
                componentResolver.resolveGenerator(phyloSpecGeneratorName);
        Generator knownGenerator = knownGenerators.isEmpty() ? null : knownGenerators.getFirst();

        Generator__1 generator = new Generator__1();
        generator.setName(phyloSpecGeneratorName);
        if (knownGenerator != null) {
            generator.setNamespace(knownGenerator.getNamespace());
            generator.setGeneratedType(knownGenerator.getGeneratedType());
        }

        List<Argument__1> arguments = new ArrayList<>();
        for (GeneratorTile.GeneratorTileInput<?, ?> input :
                generatorTile.getGeneratorTileInputs()) {
            arguments.add(generateArgumentSpecification(input, knownGenerator));
        }
        generator.setArguments(arguments);

        return generator;
    }

    /**
     * Builds the engine-specification entry for a single generator argument, looking up its
     * canonical type from the matching argument of the core component library's generator.
     */
    private static Argument__1 generateArgumentSpecification(
            GeneratorTile.GeneratorTileInput<?, ?> input, Generator knownGenerator) {
        Argument__1 argument = new Argument__1();
        argument.setName(input.getPhylospecArgumentName());
        argument.setRequired(input.isRequired());
        argument.setCanBeStochastic(
                input.getAcceptedStochasticities().contains(Stochasticity.STOCHASTIC));

        if (knownGenerator != null) {
            knownGenerator.getArguments().stream()
                    .filter(a -> a.getName().equals(input.getPhylospecArgumentName()))
                    .findFirst()
                    .ifPresent(a -> argument.setType(a.getType()));
        }

        return argument;
    }
}
