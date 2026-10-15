package tiles.sitemodels;

import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.spec.domain.*;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import beastconfig.BEASTState;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.Partial;
import org.phylospec.tiling.tiles.GeneratorTile;

public class SiteModelTile extends GeneratorTile<Partial<SiteModel, SubstitutionModel>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteGammaInv";
    }

    GeneratorTileInput<RealScalarParam<PositiveReal>, BEASTState> shapeInput = new GeneratorTileInput<>("shape");
    GeneratorTileInput<Integer, BEASTState> numCategoriesInput = new GeneratorTileInput<>("numCategories");
    GeneratorTileInput<RealScalarParam<UnitInterval>, BEASTState> invariantProportionInput =
            new GeneratorTileInput<>("invariantProportion", false);
    GeneratorTileInput<IntScalar<NonNegativeInt>, BEASTState> numSitesInput = new GeneratorTileInput<>("numSites");

    @Override
    public Partial<SiteModel, SubstitutionModel> applyTile(
            BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<PositiveReal> shape = this.shapeInput.apply(beastState, indexVariables);
        Integer numCategories = this.numCategoriesInput.apply(beastState, indexVariables);
        RealScalarParam<UnitInterval> invariantProportion = Objects.requireNonNullElse(
                invariantProportionInput.apply(beastState, indexVariables),
                new RealScalarParam<>(0.0, UnitInterval.INSTANCE));
        this.numSitesInput.apply(beastState, indexVariables);

        // Each likelihood owns its SiteModel, but can share the same shape/invariant parameters.
        // Reusing one mutable SiteModel would overwrite another partition's Q matrix or rate.
        return new Partial<>(new SiteModel(), (unused, substitutionModel) -> {
            SiteModel siteModel = new SiteModel();
            beastState.setInput(siteModel, siteModel.shapeParameterInput, shape);
            beastState.setInput(siteModel, siteModel.gammaCategoryCount, numCategories);
            beastState.setInput(siteModel, siteModel.invarParameterInput, invariantProportion);
            beastState.setInput(siteModel, siteModel.substModelInput, substitutionModel);
            return siteModel;
        });
    }
}
