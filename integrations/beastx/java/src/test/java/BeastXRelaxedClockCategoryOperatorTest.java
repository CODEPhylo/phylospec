import dr.inference.model.Bounds;
import dr.inference.model.Parameter;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.SwapOperator;
import dr.inference.operators.UniformIntegerOperator;
import dr.math.MathUtils;
import org.junit.jupiter.api.Test;
import tiling.BeastXState;
import tiling.operators.OperatorBuilder;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BeastXRelaxedClockCategoryOperatorTest {

    private static final Path RELAXED_CLOCK_MODEL =
            Path.of(
                    "src",
                    "test",
                    "java",
                    "tiling",
                    "branchmodels",
                    "relaxedClock.phylospec"
            );

    @Test
    public void categoryOperatorsUseIntegerMovesAndParameterBounds() throws Exception {
        BeastXState state =
                PhyloSpecRunner
                        .fromFile(RELAXED_CLOCK_MODEL)
                        .buildState("relaxedClockCategoryOperators");

        Parameter categories =
                state.treeRelaxedClockModels
                        .values()
                        .stream()
                        .findFirst()
                        .orElseThrow()
                        .rateCategoriesParameter();

        List<MCMCOperator> categoryOperators =
                new OperatorBuilder()
                        .build(state)
                        .stream()
                        .filter(operator -> operator.getOperatorName().contains(categories.getId()))
                        .toList();

        assertEquals(2, categoryOperators.size());
        SwapOperator swapOperator =
                assertInstanceOf(SwapOperator.class, categoryOperators.get(0));
        assertEquals(10.0, swapOperator.getWeight());

        UniformIntegerOperator actualOperator =
                assertInstanceOf(
                        UniformIntegerOperator.class,
                        categoryOperators.get(1)
                );
        assertEquals(10.0, actualOperator.getWeight());

        Bounds<Double> bounds =
                categories.getBounds();

        int lower =
                (int) Math.ceil(bounds.getLowerLimit(0));

        int upper =
                (int) Math.floor(bounds.getUpperLimit(0));

        Parameter referenceCategories =
                new Parameter.Default(categories.getParameterValues());

        referenceCategories.addBounds(
                new Parameter.DefaultBounds(
                        upper,
                        lower,
                        referenceCategories.getDimension()
                )
        );

        UniformIntegerOperator referenceOperator =
                new UniformIntegerOperator(
                        referenceCategories,
                        lower,
                        upper,
                        10.0,
                        1
                );

        for (long seed = 1; seed <= 20; seed++) {
            resetCategories(categories, lower, upper);
            resetCategories(referenceCategories, lower, upper);

            MathUtils.setSeed(seed);
            actualOperator.operate();
            actualOperator.accept(0.0);

            MathUtils.setSeed(seed);
            referenceOperator.operate();
            referenceOperator.accept(0.0);

            assertArrayEquals(
                    referenceCategories.getParameterValues(),
                    categories.getParameterValues(),
                    0.0,
                    "Uniform category proposal differs for seed " + seed
            );
        }
    }

    private void resetCategories(
            Parameter parameter,
            int lower,
            int upper
    ) {
        int numberOfValues =
                upper - lower + 1;

        for (int index = 0; index < parameter.getDimension(); index++) {
            parameter.setParameterValueQuietly(
                    index,
                    lower + index % numberOfValues
            );
        }

        parameter.fireParameterChangedEvent();
    }
}
