package tiles.functions;

import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.GeneratorTile;
import tiling.BEASTState;

import java.util.List;

public class MRCATile extends GeneratorTile<RealScalarParam<PositiveReal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "mrca";
    }

    TileInput<Tree> treeInput = new TileInput<>("tree");
    TileInput<List<String>> cladeInput = new TileInput<>("clade");

    @Override
    public RealScalarParam<PositiveReal> applyTile(BEASTState beastState) {
        Tree tree = this.treeInput.apply(beastState);
        List<String> clade = this.cladeInput.apply(beastState);

        // TODO

        return new RealScalarParam<>(tree.getRoot().getHeight(), PositiveReal.INSTANCE);
    }

}
