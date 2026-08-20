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
import java.util.stream.Collectors;
import org.phylospec.components.Argument;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.tiles.CandidateTile;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;

public class EngineSpecGenerator {

    /**
     * Generates an engine specification for the given tile library and engine metadata, and
     * writes it as JSON to "generated/<engine-name>-<engine-version>.json".
     *
     * <p>The generator tiles are not validated against a component library. Use the overload taking
     * a {@link ComponentResolver} to also check that the implemented generators exist and declare
     * their arguments in the expected order.
     */
    public static <S> void writeEngineSpecification(
            Path generatedDirectory,
            TileLibrary<S> tileLibrary,
            String engineName,
            String engineVersion,
            List<String> engineDependencies,
            String installationInstructions,
            String installationWebsite)
            throws IOException {
        writeEngineSpecification(
                generatedDirectory,
                tileLibrary,
                null,
                engineName,
                engineVersion,
                engineDependencies,
                installationInstructions,
                installationWebsite);
    }

    /**
     * Generates an engine specification for the given tile library and engine metadata, and
     * writes it as JSON to "generated/<engine-name>-<engine-version>.json". The component resolver
     * is only used to validate the generator tiles.
     */
    public static <S> void writeEngineSpecification(
            Path generatedDirectory,
            TileLibrary<S> tileLibrary,
            ComponentResolver componentResolver,
            String engineName,
            String engineVersion,
            List<String> engineDependencies,
            String installationInstructions,
            String installationWebsite)
            throws IOException {
        EngineSpecificationSchema schema = generateEngineSpecification(
                tileLibrary,
                componentResolver,
                engineName,
                engineVersion,
                engineDependencies,
                installationInstructions,
                installationWebsite);

        Files.createDirectories(generatedDirectory);

        File outputFile = generatedDirectory
                .resolve(engineName + "-" + engineVersion + ".json")
                .toFile();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile, schema);
    }

    /**
     * Generates an engine specification for the given tile library and engine metadata. The
     * component resolver is only used to validate the generator tiles, it does not contribute to
     * the generated specification. It may be null, in which case no validation happens.
     */
    public static <S> EngineSpecificationSchema generateEngineSpecification(
            TileLibrary<S> tileLibrary,
            ComponentResolver componentResolver,
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

        // there might be multiple tiles with the same generator description (namespace + name + argument names)
        // we collapse them into one using a set
        Set<Generator__1> generators = new LinkedHashSet<>();

        for (CandidateTile<S> candidateTile : tileLibrary.getTiles()) {
            if (candidateTile instanceof GeneratorTile<?, ?> generatorTile) {
                if (componentResolver != null) {
                    validateGeneratorTile(generatorTile, componentResolver);
                }
                generators.add(generateGeneratorSpecification(generatorTile));
            }
        }
        schema.setGenerators(new ArrayList<>(generators));

        return schema;
    }

    /**
     * Builds the engine-specification entry for a single generator tile. The namespace is only set
     * if the tile specifies one.
     */
    private static Generator__1 generateGeneratorSpecification(GeneratorTile<?, ?> generatorTile) {
        String phyloSpecGeneratorName = generatorTile.getPhyloSpecGeneratorName();

        Generator__1 generator = new Generator__1();
        generator.setName(phyloSpecGeneratorName);
        generatorTile.getNamespace().ifPresent(generator::setNamespace);

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

    /**
     * Validates a generator tile against the component library: the generator it implements has to
     * exist, and its arguments have to be declared in the same relative order as in the component
     * library. The resolver is only used for these checks, the engine specification itself is built
     * from the tile alone.
     */
    private static void validateGeneratorTile(GeneratorTile<?, ?> generatorTile, ComponentResolver componentResolver) {
        String phyloSpecGeneratorName = generatorTile.getPhyloSpecGeneratorName();

        List<Generator> knownGenerators = componentResolver.resolveGenerator(phyloSpecGeneratorName);
        if (knownGenerators.isEmpty()) {
            throw new IllegalStateException("The tile '"
                    + generatorTile.getClass().getSimpleName()
                    + "' implements the generator '"
                    + phyloSpecGeneratorName
                    + "', which is not known to the component library. Did you mean '"
                    + componentResolver.findClosestComponent(phyloSpecGeneratorName)
                    + "'?");
        }

        List<String> tileArgumentNames = generatorTile.getGeneratorTileInputs().stream()
                .map(GeneratorTile.GeneratorTileInput::getPhylospecArgumentName)
                .toList();

        // a generator name can refer to multiple overloads, and a single tile may implement several
        // of them at once. so every argument only has to be known by at least one overload

        Set<String> knownArgumentNames = knownGenerators.stream()
                .flatMap(knownGenerator -> knownGenerator.getArguments().stream())
                .map(Argument::getName)
                .collect(Collectors.toSet());

        List<String> unknownArgumentNames = tileArgumentNames.stream()
                .filter(name -> !knownArgumentNames.contains(name))
                .toList();

        if (!unknownArgumentNames.isEmpty()) {
            throw new IllegalStateException("The tile '"
                    + generatorTile.getClass().getSimpleName()
                    + "' declares the arguments "
                    + unknownArgumentNames
                    + " for the generator '"
                    + phyloSpecGeneratorName
                    + "', which are not known to the component library. Known arguments are "
                    + knownArgumentNames
                    + ".");
        }

        // a tile may leave out optional arguments and mix arguments of different overloads, so we
        // only require that its arguments do not contradict the order of any overload

        for (Generator knownGenerator : knownGenerators) {
            List<String> expectedOrder = knownGenerator.getArguments().stream()
                    .map(Argument::getName)
                    .toList();

            List<String> tileOrderForThisOverload =
                    tileArgumentNames.stream().filter(expectedOrder::contains).toList();

            if (!isOrderedSubsequence(tileOrderForThisOverload, expectedOrder)) {
                throw new IllegalStateException("The tile '"
                        + generatorTile.getClass().getSimpleName()
                        + "' declares the arguments "
                        + tileArgumentNames
                        + " for the generator '"
                        + phyloSpecGeneratorName
                        + "', which contradicts the argument order "
                        + expectedOrder
                        + " of the component library.");
            }
        }
    }

    /**
     * Returns whether all elements of the candidate appear in the reference in the same relative
     * order.
     */
    private static boolean isOrderedSubsequence(List<String> candidate, List<String> reference) {
        int referenceIndex = 0;
        for (String element : candidate) {
            while (referenceIndex < reference.size()
                    && !reference.get(referenceIndex).equals(element)) {
                referenceIndex++;
            }
            if (referenceIndex == reference.size()) return false;
            referenceIndex++;
        }
        return true;
    }
}
