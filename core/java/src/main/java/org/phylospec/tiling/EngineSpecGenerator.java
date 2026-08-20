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
import org.phylospec.components.EngineSpecificationSchema;
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
        EngineSpecificationSchema schema = generateEngineSpecification(
                tileLibrary,
                engineName,
                engineVersion,
                engineDependencies,
                installationInstructions,
                installationWebsite);

        Path generatedDirectory = Path.of(GENERATED_DIRECTORY);
        Files.createDirectories(generatedDirectory);

        File outputFile = generatedDirectory
                .resolve(engineName + "-" + engineVersion + ".json")
                .toFile();

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
        // initialize schema

        EngineSpecificationSchema schema = new EngineSpecificationSchema();
        schema.setName(engineName);
        schema.setEngineVersion(engineVersion);
        schema.setDependsOn(engineDependencies);
        schema.setInstallationInstructions(installationInstructions);
        schema.setInstallationWebsite(installationWebsite);

        // add generator descriptions

        Set<Generator__1> generators = new LinkedHashSet<>();
        for (CandidateTile<S> candidateTile : tileLibrary.getTiles()) {
            if (candidateTile instanceof GeneratorTile<?, ?> generatorTile) {
                generators.add(generateGeneratorSpecification(generatorTile));
            }
        }
        schema.setGenerators(new ArrayList<>(generators));

        return schema;
    }

    /**
     * Builds the engine-specification entry for a single generator tile, looking up its namespace
     * and generated type from the core component library.
     *
     * <p>Throws if the tile's generator name isn't known to the core component library, since the
     * resulting entry would otherwise be missing the namespace and type information the
     * specification schema requires.
     */
    private static Generator__1 generateGeneratorSpecification(GeneratorTile<?, ?> generatorTile) {
        String phyloSpecGeneratorName = generatorTile.getPhyloSpecGeneratorName();

        Generator__1 generator = new Generator__1();
        generator.setName(phyloSpecGeneratorName);

        List<Argument__1> arguments = new ArrayList<>();
        for (GeneratorTile.GeneratorTileInput<?, ?> input : generatorTile.getGeneratorTileInputs()) {
            Argument__1 argument = new Argument__1();
            argument.setName(input.getPhylospecArgumentName());
            argument.setRequired(input.isRequired());
            argument.setCanBeStochastic(input.getAcceptedStochasticities().contains(Stochasticity.STOCHASTIC));
            arguments.add(argument);
        }
        generator.setArguments(arguments);

        return generator;
    }
}
