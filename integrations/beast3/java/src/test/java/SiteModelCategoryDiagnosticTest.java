import beast.base.evolution.sitemodel.SiteModel;
import beast.base.inference.parameter.RealParameter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SiteModelCategoryDiagnosticTest {

    @Test
    public void printsDiscreteGammaCategoryRatesAndProportions() {
        printSiteModel("beast3-gamma-no-invariant", siteModel(false));
        printSiteModel("beast3-gamma-zero-invariant", siteModel(true));
    }

    private static SiteModel siteModel(boolean includeInvariantParameter) {
        SiteModel siteModel =
                new SiteModel();

        siteModel.gammaCategoryCount.setValue(4, siteModel);
        siteModel.shapeParameterInput.setValue(new RealParameter("1.0"), siteModel);

        if (includeInvariantParameter) {
            siteModel.invarParameterInput.setValue(new RealParameter("0.0"), siteModel);
        }

        siteModel.initAndValidate();

        return siteModel;
    }

    private static void printSiteModel(
            String label,
            SiteModel siteModel
    ) {
        double[] rates =
                siteModel.getCategoryRates(null);

        double[] proportions =
                siteModel.getCategoryProportions(null);

        assertEquals(rates.length, proportions.length);
        assertEquals(4, siteModel.getCategoryCount());
        assertEquals(1.0, weightedMean(rates, proportions), 1e-12);

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
