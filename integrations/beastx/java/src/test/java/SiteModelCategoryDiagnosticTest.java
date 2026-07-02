import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.inference.model.Parameter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SiteModelCategoryDiagnosticTest {

    @Test
    public void printsDiscreteGammaCategoryRatesAndProportions() {
        printSiteModel(
                "beastx-gamma-no-invariant",
                siteModel(false),
                4,
                1.0
        );

        printSiteModel(
                "beastx-gamma-zero-invariant",
                siteModel(true),
                5,
                1.25
        );
    }

    private static GammaSiteRateModel siteModel(boolean includeInvariantParameter) {
        Parameter relativeRateParameter =
                new Parameter.Default(1.0);

        Parameter shapeParameter =
                new Parameter.Default(1.0);

        Parameter invariantProportionParameter =
                includeInvariantParameter
                        ? new Parameter.Default(0.0)
                        : null;

        return new GammaSiteRateModel(
                "siteRateModel",
                relativeRateParameter,
                1.0,
                shapeParameter,
                4,
                invariantProportionParameter
        );
    }

    private static void printSiteModel(
            String label,
            GammaSiteRateModel siteModel,
            int expectedCategoryCount,
            double expectedWeightedMeanRate
    ) {
        double[] rates =
                siteModel.getCategoryRates();

        double[] proportions =
                siteModel.getCategoryProportions();

        assertEquals(rates.length, proportions.length);
        assertEquals(expectedCategoryCount, siteModel.getCategoryCount());
        assertEquals(expectedWeightedMeanRate, weightedMean(rates, proportions), 1e-12);

        System.out.println(label);
        System.out.println("categoryCount=" + siteModel.getCategoryCount());
        System.out.println("rates=" + Arrays.toString(rates));
        System.out.println("proportions=" + Arrays.toString(proportions));
        System.out.println("weightedMeanRate=" + weightedMean(rates, proportions));
    }

    private static double weightedMean(
            double[] rates,
            double[] proportions
    ) {
        double mean =
                0.0;

        for (int i = 0; i < rates.length; i++) {
            mean += rates[i] * proportions[i];
        }

        return mean;
    }
}
