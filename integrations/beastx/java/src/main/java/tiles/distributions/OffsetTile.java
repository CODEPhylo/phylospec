package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BoundDistribution;
import tiling.model.ParameterAttribute;

import java.util.IdentityHashMap;

public class OffsetTile extends GeneratorTile<
        BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Offset";
    }

    GeneratorTileInput<
            BoundDistribution<
                    ? extends BeastXRealScalarParam<? extends Real>,
                    ? extends DistributionLikelihood
                    >,
            BeastXState
            > baseInput =
            new GeneratorTileInput<>("base");

    GeneratorTileInput<RealScalar<? extends Real>, BeastXState> offsetInput =
            new GeneratorTileInput<>("offset");

    @Override
    public BoundDistribution<BeastXRealScalarParam<Real>, DistributionLikelihood> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        BoundDistribution<
                ? extends BeastXRealScalarParam<? extends Real>,
                ? extends DistributionLikelihood
                > base =
                this.baseInput.apply(beastState, indexVariables);

        RealScalar<? extends Real> offset =
                this.offsetInput.apply(beastState, indexVariables);

        double offsetValue =
                offset.get();

        Distribution offsetDistribution =
                new OffsetDistribution(
                        base.distribution.getDistribution(),
                        offsetValue
                );

        DistributionLikelihood likelihood =
                new DistributionLikelihood(offsetDistribution);

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(
                        base.stateNode.get() + offsetValue,
                        Real.INSTANCE
                );

        return new BoundDistribution<>(
                likelihood,
                defaultState,
                state -> likelihood.addData(new ParameterAttribute(state.getParameter()))
        );
    }

    private static class OffsetDistribution implements Distribution {

        private final Distribution base;
        private final double offset;

        private OffsetDistribution(
                Distribution base,
                double offset
        ) {
            this.base = base;
            this.offset = offset;
        }

        @Override
        public double pdf(double x) {
            return this.base.pdf(x - this.offset);
        }

        @Override
        public double logPdf(double x) {
            return this.base.logPdf(x - this.offset);
        }

        @Override
        public double cdf(double x) {
            return this.base.cdf(x - this.offset);
        }

        @Override
        public double quantile(double y) {
            return this.base.quantile(y) + this.offset;
        }

        @Override
        public double mean() {
            return this.base.mean() + this.offset;
        }

        @Override
        public double variance() {
            return this.base.variance();
        }

        @Override
        public UnivariateFunction getProbabilityDensityFunction() {
            UnivariateFunction baseDensity =
                    this.base.getProbabilityDensityFunction();

            return new UnivariateFunction() {
                @Override
                public double evaluate(double x) {
                    return baseDensity.evaluate(x - offset);
                }

                @Override
                public double getLowerBound() {
                    return baseDensity.getLowerBound() + offset;
                }

                @Override
                public double getUpperBound() {
                    return baseDensity.getUpperBound() + offset;
                }
            };
        }
    }
}