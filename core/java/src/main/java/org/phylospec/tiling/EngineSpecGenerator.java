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

        // there might be multiple tiles with the same generator description (namespace, name and
        // arguments including their stochasticity), we collapse them into one using a set. tiles
        // that differ in the accepted stochasticity stay separate entries
        Set<Generator__1> generators = new LinkedHashSet<>();

        for (CandidateTile<S> candidateTile : tileLibrary.getTiles()) {
            if (candidateTile instanceof GeneratorTile<?, ?> generatorTile) {
                GeneratorTileMappingDescriptor descriptor = generatorTile.getMappingDescriptor();
                if (componentResolver != null) {
                    validateGeneratorTile(descriptor, componentResolver);
                }
                generators.add(generateGeneratorSpecification(descriptor));
            }
        }
        schema.setGenerators(new ArrayList<>(generators));

        return schema;
    }

    /**
     * Builds the engine-specification entry for a single generator tile. The namespace is only set
     * if the tile specifies one.
     */
    private static Generator__1 generateGeneratorSpecification(GeneratorTileMappingDescriptor descriptor) {
        Generator__1 generator = new Generator__1();
        generator.setName(descriptor.componentName());
        descriptor.namespace().ifPresent(generator::setNamespace);

        List<Argument__1> arguments = new ArrayList<>();
        for (GeneratorTileMappingDescriptor.Input input : descriptor.inputs()) {
            Argument__1 argument = new Argument__1();
            argument.setName(input.name());
            argument.setRequired(input.required());
            argument.setCanBeStochastic(input.acceptedStochasticities().contains(Stochasticity.STOCHASTIC));
            arguments.add(argument);
        }
        generator.setArguments(arguments);

        return generator;
    }

    /**
     * Validates a generator tile against the component library: the generator it implements has to
     * exist, its arguments have to be known, and its first argument has to be the one the component
     * library expects in first position. The resolver is only used for these checks, the engine
     * specification itself is built from the tile alone.
     */
    private static void validateGeneratorTile(
            GeneratorTileMappingDescriptor descriptor, ComponentResolver componentResolver) {
        // if the tile knows its namespace, we resolve the qualified name. otherwise a tile could be
        // validated against a same-named generator of another imported namespace

        String phyloSpecGeneratorName = getResolvableGeneratorName(descriptor);

        // validate that we know the generator

        List<Generator> knownGenerators = componentResolver.resolveGenerator(phyloSpecGeneratorName);
        if (knownGenerators.isEmpty()) {
            throw new IllegalStateException("The tile '"
                    + descriptor.implementationClass().getSimpleName()
                    + "' implements the generator '"
                    + phyloSpecGeneratorName
                    + "', which is not known to the component library. Did you mean '"
                    + componentResolver.findClosestComponent(phyloSpecGeneratorName)
                    + "'?");
        }

        // validate that the arguments are known and declared in correct order

        List<String> tileArgumentNames = descriptor.inputs().stream()
                .map(GeneratorTileMappingDescriptor.Input::name)
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
                    + descriptor.implementationClass().getSimpleName()
                    + "' declares the arguments "
                    + unknownArgumentNames
                    + " for the generator '"
                    + phyloSpecGeneratorName
                    + "', which are not known to the component library. Known arguments are "
                    + knownArgumentNames
                    + ".");
        }

        // first argument can be passed positionally, so it is the only one whose position
        // has to agree with the component library. the remaining arguments are passed by name and
        // may be declared in any order, or left out entirely

        if (tileArgumentNames.isEmpty()) return;

        String tileFirstArgumentName = tileArgumentNames.getFirst();

        List<String> expectedFirstArgumentNames = knownGenerators.stream()
                .map(Generator::getArguments)
                .filter(arguments -> !arguments.isEmpty())
                .map(arguments -> arguments.getFirst().getName())
                .toList();

        if (!expectedFirstArgumentNames.contains(tileFirstArgumentName)) {
            throw new IllegalStateException("The tile '"
                    + descriptor.implementationClass().getSimpleName()
                    + "' declares '"
                    + tileFirstArgumentName
                    + "' as the first argument of the generator '"
                    + phyloSpecGeneratorName
                    + "', but the component library expects one of "
                    + expectedFirstArgumentNames
                    + ".");
        }
    }

    /**
     * Returns the name under which the generator of the given tile can be resolved in a component
     * library. This is the qualified name if the tile specifies its namespace, and the plain
     * generator name otherwise.
     */
    private static String getResolvableGeneratorName(GeneratorTileMappingDescriptor descriptor) {
        String generatorName = descriptor.componentName();

        return descriptor
                .namespace()
                .map(namespace ->
                        generatorName.startsWith(namespace + ".") ? generatorName : namespace + "." + generatorName)
                .orElse(generatorName);
    }
}
