package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import beastconfig.BEASTState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.phylospec.components.Argument;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.TileLibrary;
import tiles.popfunc.PopFuncTileLibrary;
import utils.popfunc.PopFuncEngineSpec;

public class PopFuncEngineSpecTest {

    private static final String VERSION = "0.1.0-SNAPSHOT";

    @TempDir
    Path temporaryDirectory;

    @Test
    public void exposesPopFuncCapabilities() throws IOException {
        ComponentResolver resolver = new ComponentResolver(
                TileLibrary.loadComponentLibraries(List.of(new PopFuncTileLibrary())));
        EngineSpecificationSchema specification = PopFuncEngineSpec.create(VERSION);

        assertEquals("popfunc", specification.getName());
        assertEquals(VERSION, specification.getEngineVersion());
        assertEquals(List.of("beast2"), specification.getDependsOn());
        assertEquals(9, specification.getGenerators().size());

        Generator__1 modelIndicator = findGenerator(specification, "modelIndicator");
        assertEquals("popfunc.distributions", modelIndicator.getNamespace());
        assertEquals(
                List.of("models"),
                modelIndicator.getArguments().stream().map(Argument__1::getName).toList());

        Generator__1 gompertzF0 = findGenerator(specification, "gompertzF0PopulationFunction");
        assertEquals("popfunc.functions.coalescent", gompertzF0.getNamespace());
        assertEquals(
                List.of(
                        "initialProportion",
                        "growthRate",
                        "initialPopulationSize",
                        "ancestralPopulationSize"),
                gompertzF0.getArguments().stream().map(Argument__1::getName).toList());
        assertFalse(gompertzF0.getArguments().getLast().getRequired());

        Generator gompertzF0Component = resolver.resolveGenerator(
                        "popfunc.functions.coalescent.gompertzF0PopulationFunction")
                .getFirst();
        assertEquals(
                "phylospec.types.Probability",
                gompertzF0Component.getArguments().getFirst().getType());

        Generator__1 gompertzT50 = findGenerator(specification, "gompertzT50PopulationFunction");
        assertEquals("popfunc.functions.coalescent", gompertzT50.getNamespace());

        Generator__1 consExpCons = findGenerator(
                specification, "constantExponentialConstantPopulationFunction");
        assertEquals("popfunc.functions.coalescent", consExpCons.getNamespace());

        Generator__1 selection = findGenerator(specification, "stochasticPopulationSelection");
        assertEquals("popfunc.functions.coalescent", selection.getNamespace());
        assertEquals(
                List.of("indicator", "models"),
                selection.getArguments().stream().map(Argument__1::getName).toList());
        assertTrue(selection.getArguments().stream().allMatch(Argument__1::getRequired));

        Generator selectionComponent = resolver.resolveGenerator(
                        "popfunc.functions.coalescent.stochasticPopulationSelection")
                .getFirst();
        assertEquals(
                List.of(
                        "phylospec.types.NonNegativeInteger",
                        "phylospec.types.Vector<phylospec.types.PopulationFunction>"),
                selectionComponent.getArguments().stream().map(Argument::getType).toList());
        assertEquals(
                List.of("indicator.value < models.num"),
                selectionComponent.getConstraints());

        Generator__1 logistic = findGenerator(specification, "logisticPopulationFunction");
        assertEquals("logisticPopulationFunction", logistic.getName());
        assertEquals("phylospec.functions.coalescent", logistic.getNamespace());
        assertEquals(
                List.of("inflectionAge", "carryingCapacity", "growthRate", "ancestralPopulationSize"),
                logistic.getArguments().stream().map(Argument__1::getName).toList());
        assertTrue(logistic.getArguments().subList(0, 3).stream().allMatch(Argument__1::getRequired));
        assertFalse(logistic.getArguments().get(3).getRequired());
        assertTrue(logistic.getArguments().stream().allMatch(Argument__1::getCanBeStochastic));

        List<Generator> components = resolver.resolveGenerator(
                "phylospec.functions.coalescent.logisticPopulationFunction");
        assertEquals(1, components.size());
        assertEquals(
                List.of(
                        "phylospec.types.Age",
                        "phylospec.types.PositiveReal",
                        "phylospec.types.NonNegativeReal",
                        "phylospec.types.NonNegativeReal"),
                components.getFirst().getArguments().stream().map(Argument::getType).toList());

        Generator__1 constant = findGenerator(specification, "constantPopulationFunction");
        assertEquals("phylospec.functions.coalescent", constant.getNamespace());
        assertEquals(
                List.of("populationSize"),
                constant.getArguments().stream().map(Argument__1::getName).toList());
        assertTrue(constant.getArguments().getFirst().getRequired());
        assertTrue(constant.getArguments().getFirst().getCanBeStochastic());

        Generator__1 exponential =
                findGenerator(specification, "exponentialPopulationFunction");
        assertEquals(
                List.of("populationSize", "growthRate", "ancestralPopulationSize"),
                exponential.getArguments().stream().map(Argument__1::getName).toList());
        assertTrue(exponential.getArguments().subList(0, 2).stream()
                .allMatch(Argument__1::getRequired));
        assertFalse(exponential.getArguments().get(2).getRequired());

        Generator__1 expansion =
                findGenerator(specification, "expansionPopulationFunction");
        assertEquals(
                List.of("populationSize", "growthRate", "transitionAge", "ancestralPopulationSize"),
                expansion.getArguments().stream().map(Argument__1::getName).toList());
        assertTrue(expansion.getArguments().subList(0, 3).stream()
                .allMatch(Argument__1::getRequired));
        assertFalse(expansion.getArguments().get(3).getRequired());

        Generator expansionComponent = resolver.resolveGenerator(
                        "phylospec.functions.coalescent.expansionPopulationFunction")
                .getFirst();
        assertEquals(
                List.of(
                        "phylospec.types.PositiveReal",
                        "phylospec.types.PositiveReal",
                        "phylospec.types.Age",
                        "phylospec.types.PositiveReal"),
                expansionComponent.getArguments().stream().map(Argument::getType).toList());
        assertEquals(
                List.of("ancestralPopulationSize.value < populationSize.value"),
                expansionComponent.getConstraints());
    }

    @Test
    public void writesRepositoryCompatibleJson() throws IOException {
        Path enginesDirectory = temporaryDirectory.resolve("engines");
        PopFuncEngineSpec.write(enginesDirectory, VERSION);

        Path specificationFile =
                enginesDirectory.resolve("popfunc-" + VERSION + ".json");
        assertTrue(Files.isRegularFile(specificationFile));

        EngineSpecificationSchema specification = new ObjectMapper()
                .readValue(specificationFile.toFile(), EngineSpecificationSchema.class);

        assertEquals("popfunc", specification.getName());
        assertEquals(VERSION, specification.getEngineVersion());
        assertEquals(List.of("beast2"), specification.getDependsOn());
        assertEquals(9, specification.getGenerators().size());
    }

    @Test
    public void discoversPackagedSpecification() throws IOException {
        List<EngineSpecificationSchema> specifications =
                TileLibrary.loadEngineSpecifications(BEASTState.class);

        List<EngineSpecificationSchema> popFuncSpecifications = specifications.stream()
                .filter(specification -> "popfunc".equals(specification.getName()))
                .toList();

        assertEquals(1, popFuncSpecifications.size());
        EngineSpecificationSchema specification = popFuncSpecifications.getFirst();
        assertEquals(VERSION, specification.getEngineVersion());
        assertEquals(List.of("beast2"), specification.getDependsOn());
        assertEquals(9, specification.getGenerators().size());
    }

    private static Generator__1 findGenerator(
            EngineSpecificationSchema specification, String name) {
        return specification.getGenerators().stream()
                .filter(generator -> name.equals(generator.getName()))
                .findFirst()
                .orElseThrow();
    }
}
