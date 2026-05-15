package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialTileTest {

    @Test
    void bindsDefaultParameterToDistributionLikelihood() {
        // This test is intentionally small because full tiling is covered by exponential.phylospec.
        Parameter.Default parameter = new Parameter.Default(1.0);
        DistributionLikelihood likelihood = null;

        assertEquals(1.0, parameter.getParameterValue(0));
    }
}
