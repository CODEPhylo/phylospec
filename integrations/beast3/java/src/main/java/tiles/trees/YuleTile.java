package tiles.trees;

import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.type.RealScalar;
import tiles.GeneratorTile;
import tiling.BEASTState;
import tiling.EvaluatedDistribution;
import tiling.Tile;

import java.util.Set;

public class YuleTile extends GeneratorTile<EvaluatedDistribution.WithInitialState<Tree, YuleModel>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "Yule";
    }

    Input<RealScalar<? extends PositiveReal>> birthRateInput = new Input<>("birthRate");
    Input<RealScalar<? extends PositiveReal>> rootAgeInput = new Input<>("rootAge", false);
    Input<TaxonSet> taxaInput = new Input<>("taxa", false);

    @Override
    public EvaluatedDistribution.WithInitialState<Tree, YuleModel> applyTile(BEASTState beastState) {
        YuleModel yuleModel = new YuleModel();

        RealScalar<? extends PositiveReal> birthRate = this.birthRateInput.apply(beastState);
        RealScalar<? extends PositiveReal> rootAge = this.rootAgeInput.apply(beastState);

        yuleModel.setInputValue("birthDiffRate", birthRate);
        yuleModel.setInputValue("originHeight", rootAge);

        return new EvaluatedDistribution.WithInitialState<>(
                yuleModel, null, Set.of(), tree -> yuleModel.treeInput.setValue(tree, null)
        );
    }

    @Override
    protected Tile<?> createInstance() {
        return new YuleTile();
    }

}
