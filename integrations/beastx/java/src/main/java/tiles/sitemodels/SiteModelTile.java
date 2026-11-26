package tiles.sitemodels;

import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;
import tiling.params.BeastXRealScalarParam;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class SiteModelTile extends GeneratorTile<
        Partial<GammaSiteRateModel, Parameter>,
        BeastXState
        > {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteGammaInv";
    }

    GeneratorTileInput<RealScalar<PositiveReal>, BeastXState> shapeInput =
            new GeneratorTileInput<>("shape");

    // The number of rate categories fixes the structure of the site model.
    GeneratorTileInput<IntScalar<? extends PositiveInt>, BeastXState> numCategoriesInput =
            new GeneratorTileInput<>("numCategories", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    GeneratorTileInput<RealScalar<UnitInterval>, BeastXState> invariantProportionInput =
            new GeneratorTileInput<>("invariantProportion", false);

    GeneratorTileInput<IntScalar<? extends NonNegativeInt>, BeastXState> numSitesInput =
            new GeneratorTileInput<>("numSites");

    @Override
    public Partial<GammaSiteRateModel, Parameter> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        RealScalar<PositiveReal> shape =
                this.shapeInput.apply(beastState, indexVariables);

        IntScalar<? extends PositiveInt> numCategories =
                this.numCategoriesInput.apply(beastState, indexVariables);

        RealScalar<UnitInterval> invariantProportion =
                this.invariantProportionInput.apply(beastState, indexVariables);

        this.numSitesInput.apply(beastState, indexVariables);

        Parameter shapeParameter =
                toParameter(shape);

        // BEAST X 10.5 (still in 10.6) gives +I+G the wrong category weights and mean rate, even for
        // an explicit 0 (CODEPhylo/phylospec#152). A proportion of 0 is the same model as none, so it
        // is left out; a sampled or non-zero proportion is rejected until BEAST X is fixed.
        if (invariantProportion != null
                && (isSampled(beastState, invariantProportion) || invariantProportion.get() != 0.0)) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "BEAST X computes DiscreteGammaInv with a non-zero invariantProportion incorrectly"
                            + " (wrong site-rate weights and mean rate), so it is not supported yet.",
                    "Leave out invariantProportion, or use the BEAST 3 engine for +I+G models"
                            + " (see CODEPhylo/phylospec#152).",
                    List.of("DiscreteGammaInv(shape=1.0, numCategories=4, numSites=numSites(data))")
            );
        }

        // PhyloCTMC completes the site model with its partition rate, which BEAST X
        // only registers as a model variable when it is passed to the constructor.
        return new Partial<>(
                null,
                (unused, relativeRateParameter) -> new GammaSiteRateModel(
                        "siteRateModel",
                        relativeRateParameter,
                        1.0,
                        shapeParameter,
                        numCategories.get(),
                        null
                )
        );
    }

    private static boolean isSampled(BeastXState beastState, RealScalar<?> scalar) {
        return scalar instanceof BeastXRealScalarParam<?> beastXScalar
                && beastState.stateNodes.containsKey(beastXScalar.getParameter());
    }

    private Parameter toParameter(RealScalar<?> scalar) {
        if (scalar instanceof BeastXRealScalarParam<?> beastXScalar) {
            return beastXScalar.getParameter();
        }

        return new Parameter.Default(scalar.get());
    }

    private Parameter unitIntervalParameter(double value) {
        Parameter.Default parameter =
                new Parameter.Default(value);

        parameter.addBounds(0.0, 1.0);

        return parameter;
    }
}
