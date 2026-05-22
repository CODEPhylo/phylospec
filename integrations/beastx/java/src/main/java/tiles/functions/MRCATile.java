package tiles.functions;

import dr.evolution.tree.NodeRef;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.Expr;
import org.phylospec.domain.NonNegativeReal;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.types.RealScalar;
import tiling.BeastXRealScalarParam;
import tiling.BeastXState;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class MRCATile extends GeneratorTile<RealScalar<NonNegativeReal>, BeastXState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "mrca";
    }

    GeneratorTileInput<List<String>, BeastXState> cladeInput =
            new GeneratorTileInput<>("clade");

    GeneratorTileInput<TreeModel, BeastXState> treeInput =
            new GeneratorTileInput<>("tree");

    @Override
    public RealScalar<NonNegativeReal> applyTile(
            BeastXState beastState,
            IdentityHashMap<Expr.Variable, Integer> indexVariables
    ) {
        List<String> clade =
                this.cladeInput.apply(beastState, indexVariables);

        TreeModel tree =
                this.treeInput.apply(beastState, indexVariables);

        if (clade.isEmpty()) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA requires at least one taxon name.",
                    "Provide a non-empty clade.",
                    List.of("Age a = mrca(clade=[\"taxon1\", \"taxon2\"], tree=tree)")
            );
        }

        NodeRef mrca =
                findMRCA(tree, clade);

        double age =
                tree.getNodeHeight(mrca);

        if (age < 0.0) {
            throw new TileApplicationError(
                    this.getRootNode(),
                    "MRCA age must be non-negative.",
                    "Use a tree whose node heights are non-negative.",
                    List.of("Age a = mrca(clade=[\"taxon1\", \"taxon2\"], tree=tree)")
            );
        }

        return new BeastXRealScalarParam<>(
                age,
                NonNegativeReal.INSTANCE
        );
    }

    private static NodeRef findMRCA(
            TreeModel tree,
            List<String> clade
    ) {
        NodeRef currentMRCA =
                findExternalNode(tree, clade.get(0));

        if (currentMRCA == null) {
            throw missingTaxon(clade.get(0));
        }

        for (int i = 1; i < clade.size(); i++) {
            NodeRef nextNode =
                    findExternalNode(tree, clade.get(i));

            if (nextNode == null) {
                throw missingTaxon(clade.get(i));
            }

            currentMRCA =
                    findPairwiseMRCA(tree, currentMRCA, nextNode);
        }

        return currentMRCA;
    }

    private static NodeRef findPairwiseMRCA(
            TreeModel tree,
            NodeRef first,
            NodeRef second
    ) {
        Set<NodeRef> firstAncestors =
                new HashSet<>();

        NodeRef current =
                first;

        while (current != null) {
            firstAncestors.add(current);

            if (tree.isRoot(current)) {
                break;
            }

            current =
                    tree.getParent(current);
        }

        current =
                second;

        while (current != null) {
            if (firstAncestors.contains(current)) {
                return current;
            }

            if (tree.isRoot(current)) {
                break;
            }

            current =
                    tree.getParent(current);
        }

        return tree.getRoot();
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

    private static TileApplicationError missingTaxon(String taxonName) {
        return new TileApplicationError(
                "Could not find taxon '" + taxonName + "' in the tree.",
                "Use taxon names or species names that exist in the tree."
        );
    }
}
