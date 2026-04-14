package tiles.sitemodels;

import beast.base.evolution.substitutionmodel.SubstitutionModel;
import beast.base.spec.domain.*;
import beast.base.spec.evolution.sitemodel.SiteModel;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.IntScalar;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.Partial;

import java.util.Objects;

public class SiteModelTile extends GeneratorTile<Partial<SiteModel, SubstitutionModel>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "DiscreteGammaInv";
    }

    GeneratorTileInput<RealScalarParam<PositiveReal>> shapeInput = new GeneratorTileInput<>("shape");
    GeneratorTileInput<Integer> numCategoriesInput = new GeneratorTileInput<>("numCategories");
    GeneratorTileInput<RealScalarParam<UnitInterval>> invariantProportionInput = new GeneratorTileInput<>("invariantProportion", false);
    GeneratorTileInput<IntScalar<NonNegativeInt>> numSitesInput = new GeneratorTileInput<>("numSites");

    @Override
    public Partial<SiteModel, SubstitutionModel> applyTile(BEASTState beastState) {
        RealScalarParam<PositiveReal> shape = this.shapeInput.apply(beastState);
        Integer numCategories = this.numCategoriesInput.apply(beastState);
        RealScalarParam<UnitInterval> invariantProportion = Objects.requireNonNullElse(
                invariantProportionInput.apply(beastState), new RealScalarParam<>(0.0, UnitInterval.INSTANCE)
        );
        this.numSitesInput.apply(beastState);

        SiteModel partialSiteModel = new SiteModel();
        beastState.setInput(partialSiteModel, partialSiteModel.shapeParameterInput, shape);
        beastState.setInput(partialSiteModel, partialSiteModel.gammaCategoryCount, numCategories);
        beastState.setInput(partialSiteModel, partialSiteModel.invarParameterInput, invariantProportion);

        return new Partial<>(partialSiteModel, (siteModel, substitutionModel) -> {
            beastState.setInput(siteModel, siteModel.substModelInput, substitutionModel);
            return siteModel;
        });
    }

}
