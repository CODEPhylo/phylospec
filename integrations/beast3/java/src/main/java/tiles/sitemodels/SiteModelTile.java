package tiles.sitemodels;

import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.spec.domain.*;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.evolution.substitutionmodel.HKY;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import beast.base.spec.type.Simplex;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Partial;
import tiling.Tile;

public class SiteModelTile extends GeneratorTile<Partial<SiteModel, SubstitutionModel>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteGammaInv";
    }

    Input<RealScalar<PositiveReal>> shapeInput = new Input<>("shape");
    Input<Integer> numCategoriesInput = new Input<>("numCategories");
    Input<RealScalar<UnitInterval>> invariantProportionInput = new Input<>("invariantProportion", false);
    Input<IntScalar<NonNegativeInt>> numSitesInput = new Input<>("numSites");

    @Override
    public Partial<SiteModel, SubstitutionModel> applyTile(BEASTState beastState) {
        RealScalar<PositiveReal> shape = this.shapeInput.apply(beastState);
        Integer numCategories = this.numCategoriesInput.apply(beastState);
        RealScalar<UnitInterval> invariantProportion = this.invariantProportionInput.apply(beastState);
        IntScalar<NonNegativeInt> numSites = this.numSitesInput.apply(beastState);

        SiteModel partialSiteModel = new SiteModel();
        partialSiteModel.setInputValue("shape", shape);
        partialSiteModel.setInputValue("gammaCategoryCount", numCategories);
        partialSiteModel.setInputValue("proportionInvariant", invariantProportion);
        partialSiteModel.setInputValue("shape", shape);

        return new Partial<>(partialSiteModel, (siteModel, substitutionModel) -> {
            siteModel.setInputValue("substModel", substitutionModel);
            return siteModel;
        });
    }

    @Override
    protected Tile<?> createInstance() {
        return new SiteModelTile();
    }

}
