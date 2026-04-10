package tiles.functions;

import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.inference.parameter.RealScalarParam;
import tiles.GeneratorTile;
import tiling.BEASTState;

public class TaxonAgeTile extends GeneratorTile<RealScalarParam<PositiveReal>> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "age";
    }

    GeneratorTileInput<String> nodeInput = new GeneratorTileInput<>("node");
    GeneratorTileInput<Tree> treeInput = new GeneratorTileInput<>("tree");

    @Override
    public RealScalarParam<PositiveReal> applyTile(BEASTState beastState) {
        String nodeName = this.nodeInput.apply(beastState);
        Tree tree = this.treeInput.apply(beastState);

        for (Node node : tree.getNodesAsArray()) {
            String taxonId = tree.getTaxonId(node);
            if (taxonId != null && taxonId.equals(nodeName)) {
                return new RealScalarParam<>(node.getHeight(), PositiveReal.INSTANCE);
            }
        }

        throw new RuntimeException("No node with the label '" + nodeName + "' found.");
    }

}
