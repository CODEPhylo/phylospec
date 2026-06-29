package tiles.functions;

import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Statistic;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.TypeToken;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.params.BeastXStatisticRealScalar;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class AgeNodeTreeTile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "age";
    }

    GeneratorTileInput<String, BeastXState> nodeInput =
            new GeneratorTileInput<>(
                    "node",
                    Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC)
            );

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        String nodeName =
                this.nodeInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        NodeRef node =
                findExternalNode(tree, nodeName);

        if (node == null) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Could not find node '" + nodeName + "' in the tree.",
                    "Use a taxon name or species name that exists in the tree.",
                    List.of("Age a = age(node=\"taxon1\", tree=tree)")
            );
        }

        NodeAgeStatistic statistic =
                new NodeAgeStatistic(
                        beastState.getAvailableID(
                                this.getId("nodeAge", indexVariables, "")
                        ),
                        tree,
                        node
                );

        if (statistic.getStatisticValue(0) < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Node age must be non-negative.",
                    "Use a tree whose node heights are non-negative.",
                    List.of("Age a = age(node=\"taxon1\", tree=tree)")
            );
        }

        beastState.addCalculationNode(
                statistic,
                new TypeToken<NodeAgeStatistic>() {},
                statistic.getId()
        );

        return new BeastXStatisticRealScalar<>(
                statistic,
                NonNegativeReal.INSTANCE
        );
    }

    private static NodeRef findExternalNode(
            TreeModel tree,
            String nodeName
    ) {
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            NodeRef node =
                    tree.getExternalNode(i);

            Taxon taxon =
                    tree.getNodeTaxon(node);

            if (taxon == null) {
                continue;
            }

            if (nodeName.equals(taxon.getId())) {
                return node;
            }

            Object species =
                    taxon.getAttribute("species");

            if (species != null && nodeName.equals(species.toString())) {
                return node;
            }
        }

        return null;
    }

    public static class NodeAgeStatistic extends Statistic.Abstract {

        private final TreeModel tree;
        private final NodeRef node;

        public NodeAgeStatistic(
                String id,
                TreeModel tree,
                NodeRef node
        ) {
            super(id);
            this.tree = tree;
            this.node = node;
        }

        @Override
        public int getDimension() {
            return 1;
        }

        @Override
        public double getStatisticValue(int dim) {
            if (dim != 0) {
                throw new IndexOutOfBoundsException("Node age statistic only has dimension 0.");
            }

            return this.tree.getNodeHeight(this.node);
        }
    }
}