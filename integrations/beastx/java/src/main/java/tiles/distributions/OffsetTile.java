package tiles.distributions;

import dr.inference.distribution.DistributionLikelihood;
import dr.inference.model.Parameter;
import dr.math.UnivariateFunction;
import dr.math.distributions.Distribution;
import org.phylospec.ast.Expr;
import org.phylospec.domain.Real;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.params.BeastXParameters;
import tiling.params.BeastXRealScalarParam;
import tiling.BeastXState;
import tiling.model.BeastXTruncatedLikelihood;
import tiling.model.BoundDistribution;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Offset(base, offset): the base distribution shifted by offset. As in BEAST 3, the offset may be
 * sampled, and the base may be any real distribution, including Truncated.
 */
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

        Parameter offset =
                BeastXParameters.toParameter(this.offsetInput.apply(beastState, indexVariables));

        double offsetValue =
                offset.getParameterValue(0);

        DistributionLikelihood likelihood =
                new DistributionLikelihood(
                        new OffsetDistribution(
                                base.distribution.getDistribution(),
                                offset
                        )
                );

        double lower =
                Double.NEGATIVE_INFINITY;

        double upper =
                Double.POSITIVE_INFINITY;

        // The distribution of a truncated likelihood is its base without the truncation, so the
        // truncation is moved outside: Offset(Truncated(b, l, u), o) = Truncated(Offset(b, o), l + o, u + o).
        if (base.distribution instanceof BeastXTruncatedLikelihood truncated) {
            if (beastState.stateNodes.containsKey(offset)) {
                throw new TileApplicationError(
                        this.getRootNode(),
                        "Offset of a Truncated distribution needs a fixed offset.",
                        "The truncation bounds move with the offset, but BEAST X keeps them fixed. Use a fixed offset.",
                        List.of("Real x ~ Offset(base=Truncated(base=Normal(mean=0.0, sd=1.0), lower=0.0), offset=2.0)")
                );
            }

            lower = truncated.getLower() + offsetValue;
            upper = truncated.getUpper() + offsetValue;
            likelihood = new BeastXTruncatedLikelihood(likelihood, lower, upper);
        }

        // BEAST X throws when the bounds of a parameter without bounds are read.
        Parameter.Default defaultParameter = new Parameter.Default(base.stateNode.get() + offsetValue);
        defaultParameter.addBounds(new Parameter.DefaultBounds(upper, lower, 1));

        BeastXRealScalarParam<Real> defaultState =
                new BeastXRealScalarParam<>(defaultParameter, Real.INSTANCE);

        DistributionLikelihood boundLikelihood =
                likelihood;

        return new BoundDistribution<>(
                boundLikelihood,
                defaultState,
                state -> boundLikelihood.addData(state.getParameter())
        );
    }

    /**
     * The base distribution shifted by the offset, which is read on every call so that a sampled
     * offset is followed. Public so the XML export can read both.
     */
    public static final class OffsetDistribution implements Distribution {

        private final Distribution base;
        private final Parameter offset;

        public Distribution getBase() {
            return base;
        }

        public Parameter getOffset() {
            return offset;
        }

        private OffsetDistribution(
                Distribution base,
                Parameter offset
        ) {
            this.base = base;
            this.offset = offset;
        }

        private double offset() {
            return this.offset.getParameterValue(0);
        }

        @Override
        public double pdf(double x) {
            return this.base.pdf(x - offset());
        }

        @Override
        public double logPdf(double x) {
            return this.base.logPdf(x - offset());
        }

        @Override
        public double cdf(double x) {
            return this.base.cdf(x - offset());
        }

        @Override
        public double quantile(double y) {
            return this.base.quantile(y) + offset();
        }

        @Override
        public double mean() {
            return this.base.mean() + offset();
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
                    return baseDensity.evaluate(x - offset());
                }

                @Override
                public double getLowerBound() {
                    return baseDensity.getLowerBound() + offset();
                }

                @Override
                public double getUpperBound() {
                    return baseDensity.getUpperBound() + offset();
                }
            };
        }
    }
}
