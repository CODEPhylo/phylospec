package tiles.sitemodels;

import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.spec.domain.*;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.tiles.GeneratorTile;
import beastconfig.BEASTState;
import org.phylospec.tiling.Partial;

import java.util.IdentityHashMap;
import java.util.Objects;

public class SiteModelTile extends GeneratorTile<Partial<SiteModel, SubstitutionModel>, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteGammaInv";
    }

    GeneratorTileInput<RealScalarParam<PositiveReal>, BEASTState> shapeInput = new GeneratorTileInput<>("shape");
    GeneratorTileInput<Integer, BEASTState> numCategoriesInput = new GeneratorTileInput<>("numCategories");
    GeneratorTileInput<RealScalarParam<UnitInterval>, BEASTState> invariantProportionInput = new GeneratorTileInput<>("invariantProportion", false);
    GeneratorTileInput<IntScalar<NonNegativeInt>, BEASTState> numSitesInput = new GeneratorTileInput<>("numSites");

    @Override
    public Partial<SiteModel, SubstitutionModel> applyTile(BEASTState beastState, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        RealScalarParam<PositiveReal> shape = this.shapeInput.apply(beastState, indexVariables);
        Integer numCategories = this.numCategoriesInput.apply(beastState, indexVariables);
        RealScalarParam<UnitInterval> invariantProportion = Objects.requireNonNullElse(
                invariantProportionInput.apply(beastState, indexVariables), new RealScalarParam<>(0.0, UnitInterval.INSTANCE)
        );
        this.numSitesInput.apply(beastState, indexVariables);

        SiteModel partialSiteModel = new SiteModel();
        beastState.setInput(partialSiteModel, partialSiteModel.shapeParameterInput, shape);
        beastState.setInput(partialSiteModel, partialSiteModel.gammaCategoryCount, numCategories);
        beastState.setInput(partialSiteModel, partialSiteModel.invarParameterInput, invariantProportion);

        // we return a partial site model where the substitution model can be set later
        return new Partial<>(partialSiteModel, (siteModel, substitutionModel) -> {
            beastState.setInput(siteModel, siteModel.substModelInput, substitutionModel);
            return siteModel;
        });
    }

}
