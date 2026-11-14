package tiles.sitemodels;

import dr.evomodel.siteratemodel.GammaSiteRateModel;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeInt;
import org.phylospec.domain.PositiveInt;
import org.phylospec.domain.PositiveReal;
import org.phylospec.domain.UnitInterval;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.IntScalar;
import org.phylospec.types.RealScalar;
import org.phylospec.typeresolver.Stochasticity;
import tiling.BeastXState;
import tiling.params.BeastXRealScalarParam;

import java.util.IdentityHashMap;
import java.util.Set;

public class SiteModelTile extends GeneratorTile<
        Partial<GammaSiteRateModel, SubstitutionModel>,
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
    public Partial<GammaSiteRateModel, SubstitutionModel> applyTile(
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

        Parameter relativeRateParameter =
                new Parameter.Default(1.0);

        Parameter shapeParameter =
                toParameter(shape);

        Parameter invariantProportionParameter =
                invariantProportion == null
                        ? null
                        : toParameter(invariantProportion);

        GammaSiteRateModel siteRateModel =
                new GammaSiteRateModel(
                        "siteRateModel",
                        relativeRateParameter,
                        1.0,
                        shapeParameter,
                        numCategories.get(),
                        invariantProportionParameter
                );

        return new Partial<>(
                siteRateModel,
                (partialSiteRateModel, substitutionModel) -> {
                    partialSiteRateModel.setSubstitutionModel(substitutionModel);
                    return partialSiteRateModel;
                }
        );
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
