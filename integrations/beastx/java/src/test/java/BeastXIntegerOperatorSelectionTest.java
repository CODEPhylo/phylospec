import dr.inference.model.Parameter;
import dr.inference.operators.MCMCOperator;
import dr.inference.operators.RandomWalkIntegerOperator;
import dr.inference.operators.RandomWalkOperator;
import dr.inference.operators.SwapOperator;
import dr.inference.operators.UniformIntegerOperator;
import org.junit.jupiter.api.Test;
import org.phylospec.domain.Int;
import org.phylospec.tiling.TypeToken;
import org.phylospec.types.IntScalar;
import org.phylospec.types.IntVector;
import tiling.BeastXState;
import tiling.operators.OperatorBuilder;
import tiling.params.BeastXIntScalarParam;
import tiling.params.BeastXIntVectorParam;
import tiling.xml.XmlElement;
import tiling.xml.builders.OperatorXmlBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeastXIntegerOperatorSelectionTest {

    @Test
    public void unboundedIntegerScalarUsesOnlyIntegerRandomWalk() {
        Parameter parameter = new Parameter.Default(2.0);
        parameter.addBounds(new Parameter.DefaultBounds(
                Double.POSITIVE_INFINITY, 0, 1));

        BeastXState state = new BeastXState("integer-scalar");
        state.addStateNode(
                new BeastXIntScalarParam<>(parameter, Int.INSTANCE),
                new TypeToken<IntScalar<Int>>() {},
                "count");

        List<MCMCOperator> operators = new OperatorBuilder().build(state);

        assertEquals(1, operators.size());
        assertTrue(hasOperator(operators, RandomWalkIntegerOperator.class));
        assertFalse(hasOperator(operators, RandomWalkOperator.class));
        assertXmlTags(state, "randomWalkIntegerOperator");
    }

    @Test
    public void boundedIntegerScalarAddsUniformButNotSwap() throws Exception {
        BeastXState state = stateFrom(
                "Integer count ~ DiscreteUniform(lower=1, upper=10)");

        List<MCMCOperator> operators = new OperatorBuilder().build(state);

        assertEquals(2, operators.size());
        assertTrue(hasOperator(operators, RandomWalkIntegerOperator.class));
        assertTrue(hasOperator(operators, UniformIntegerOperator.class));
        assertFalse(hasOperator(operators, SwapOperator.class));
        assertXmlTags(state, "randomWalkIntegerOperator", "uniformIntegerOperator");
    }

    @Test
    public void boundedIntegerVectorUsesRandomWalkSwapAndUniform() {
        Parameter parameter = new Parameter.Default(new double[]{0, 1, 2});
        parameter.addBounds(new Parameter.DefaultBounds(3, 0, 3));

        BeastXState state = new BeastXState("integer-vector");
        state.addStateNode(
                new BeastXIntVectorParam<>(parameter, Int.INSTANCE),
                new TypeToken<IntVector<Int>>() {},
                "categories");

        List<MCMCOperator> operators = new OperatorBuilder().build(state);

        assertEquals(3, operators.size());
        assertTrue(hasOperator(operators, RandomWalkIntegerOperator.class));
        assertTrue(hasOperator(operators, SwapOperator.class));
        assertTrue(hasOperator(operators, UniformIntegerOperator.class));
        assertXmlTags(
                state,
                "randomWalkIntegerOperator",
                "swapOperator",
                "uniformIntegerOperator");
    }

    private static BeastXState stateFrom(String source) throws Exception {
        return new PhyloSpecRunner(source).buildModel("integer-operators").beastState;
    }

    private static boolean hasOperator(
            List<MCMCOperator> operators,
            Class<? extends MCMCOperator> type
    ) {
        return operators.stream().anyMatch(type::isInstance);
    }

    private static void assertXmlTags(BeastXState state, String... expectedTags) {
        List<String> actualTags = new OperatorXmlBuilder().buildOperators(state).stream()
                .map(XmlElement::tag)
                .toList();
        assertEquals(List.of(expectedTags), actualTags);
    }
}
