package tiles.functions;

import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

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

        double age =
                tree.getNodeHeight(node);

        if (age < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "Node age must be non-negative.",
                    "Use a tree whose node heights are non-negative.",
                    List.of("Age a = age(node=\"taxon1\", tree=tree)")
            );
        }

        return new BeastXRealScalarParam<>(
                age,
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
}
