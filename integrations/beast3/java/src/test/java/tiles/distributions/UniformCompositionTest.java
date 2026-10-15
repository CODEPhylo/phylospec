package tiles.distributions;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.spec.domain.PositiveInt;
import beast.base.spec.inference.parameter.IntSimplexParam;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UniformCompositionTest {
    @Test
    void normalizesOverAllOrderedCompositions() {
        double sum = 0;
        for (int a = 1; a <= 3; a++) {
            for (int b = 1; b < 5 - a; b++) {
                var prior = prior(new int[] {a, b, 5 - a - b}, 5);
                assertEquals(-Math.log(6), prior.calculateLogP(), 1e-12);
                sum += Math.exp(prior.getCurrentLogP());
            }
        }
        assertEquals(1.0, sum, 1e-12);
    }

    @Test
    void rejectsNonPositiveEntriesAndWrongSum() {
        var prior = prior(new int[] {1, 2, 2}, 5);
        assertThrows(
                IllegalArgumentException.class, () -> prior.parameterInput.get().set(0, 0));
        prior.parameterInput.get().set(0, 2);
        assertEquals(Double.NEGATIVE_INFINITY, prior.calculateLogP());
    }

    @ParameterizedTest
    @CsvSource({"1,1", "5,1", "5,5", "85,4"})
    void samplesOnlyTheDeclaredSupport(int total, int num) {
        var random = new Random(34891);
        for (int repeat = 0; repeat < 100; repeat++) {
            int[] initial = new int[num];
            for (int i = 0; i < num; i++) initial[i] = total / num + (i < total % num ? 1 : 0);
            var prior = prior(initial, total);
            prior.sample(null, random);
            assertTrue(prior.parameterInput.get().getElements().stream().allMatch(x -> x > 0));
            assertEquals(
                    total,
                    prior.parameterInput.get().getElements().stream()
                            .mapToInt(Integer::intValue)
                            .sum());
            assertTrue(Double.isFinite(prior.calculateLogP()));
        }
    }

    @Test
    void samplingCanReachEveryCompositionInSmallSupport() {
        var random = new Random(7385);
        var values = new HashSet<List<Integer>>();
        for (int i = 0; i < 300; i++) {
            var prior = prior(new int[] {1, 2, 2}, 5);
            prior.sample(null, random);
            values.add(List.copyOf(prior.parameterInput.get().getElements()));
        }
        assertEquals(6, values.size());
    }

    private static UniformComposition prior(int[] values, int total) {
        var parameter = new IntSimplexParam<>(values, PositiveInt.INSTANCE, total);
        parameter.setID("groups");
        var prior = new UniformComposition();
        prior.initByName("parameter", parameter);
        assertEquals(List.of("groups"), prior.getArguments());
        assertTrue(prior.getConditions().isEmpty());
        return prior;
    }
}
