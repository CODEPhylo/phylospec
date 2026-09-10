package tiling.popfunc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.phylospec.components.Argument;
import org.phylospec.components.Argument__1;
import org.phylospec.components.ComponentResolver;
import org.phylospec.components.EngineSpecificationSchema;
import org.phylospec.components.Generator;
import org.phylospec.components.Generator__1;
import org.phylospec.tiling.EngineSpecGenerator;
import tiles.popfunc.PopFuncTileLibrary;

public class PopFuncEngineSpecTest {

    @Test
    public void exposesPopFuncCapabilities() throws IOException {
        ComponentResolver resolver =
                new ComponentResolver(ComponentResolver.loadCoreComponentLibraries());

        EngineSpecificationSchema specification = EngineSpecGenerator.generateEngineSpecification(
                new PopFuncTileLibrary(),
                resolver,
                "popfunc",
                "0.1.0-SNAPSHOT",
                List.of("beast2"),
                "Install the PopFunc BEAST package.",
                "https://github.com/LinguaPhylo/PopFunc");

        assertEquals("popfunc", specification.getName());
        assertEquals("0.1.0-SNAPSHOT", specification.getEngineVersion());
        assertEquals(List.of("beast2"), specification.getDependsOn());
        assertEquals(4, specification.getGenerators().size());

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

    private static Generator__1 findGenerator(
            EngineSpecificationSchema specification, String name) {
        return specification.getGenerators().stream()
                .filter(generator -> name.equals(generator.getName()))
                .findFirst()
                .orElseThrow();
    }
}
