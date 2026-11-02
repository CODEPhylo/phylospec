package tiles.functions;

import beast.base.evolution.alignment.Taxon;
import beast.base.evolution.alignment.TaxonSet;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.TraitSet;
import beast.base.evolution.tree.Tree;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import org.phylospec.ast.Expr;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.tiling.tiles.GeneratorTile;
import org.phylospec.typeresolver.Stochasticity;
import tiles.input.TaxaData;

/** Extracts taxa from a fixed tree without modifying or sampling that tree. */
public class TreeTaxaTile extends GeneratorTile<TaxaData, BEASTState> {

    @Override
    public String getPhyloSpecGeneratorName() {
        return "taxa";
    }

    GeneratorTileInput<Tree, BEASTState> treeInput =
            new GeneratorTileInput<>("tree", Set.of(Stochasticity.CONSTANT, Stochasticity.DETERMINISTIC));

    @Override
    public TaxaData applyTile(BEASTState state, IdentityHashMap<Expr.Variable, Integer> indexVariables) {
        return fromFixedTree(state, treeInput.apply(state, indexVariables));
    }

    static TaxaData fromFixedTree(BEASTState state, Tree tree) {
        if (tree.isEstimatedInput.get() || state.stateNodes.containsKey(tree)) {
            throw new TileApplicationError(
                    "taxa(tree) currently requires a fixed tree.",
                    "Use a tree imported with fromTree or fromNewick; sampled tip ages are not supported here.");
        }

        // Read existing leaf heights directly. getDateTrait() can process traits and
        // change node heights; registering the reference tree can reinitialize it.
        List<Node> leaves = tree.getExternalNodes();
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        double minimumAge = Double.POSITIVE_INFINITY;
        double maximumAge = 0.0;
        for (Node leaf : leaves) {
            String name = leaf.getID();
            if (name == null || name.isBlank() || !seen.add(name)) {
                throw new TileApplicationError(
                        "Invalid tree taxa.", "Every leaf must have a unique, nonblank taxon name.");
            }
            double age = leaf.getHeight();
            if (!Double.isFinite(age) || age < 0.0) {
                throw new TileApplicationError(
                        "Invalid sampling age.", "Taxon '" + name + "' must have a finite, nonnegative leaf height.");
            }
            names.add(name);
            minimumAge = Math.min(minimumAge, age);
            maximumAge = Math.max(maximumAge, age);
        }
        if (names.isEmpty()) {
            throw new TileApplicationError("No taxa found.", "The fixed tree must contain named leaves.");
        }
        if (minimumAge != 0.0) {
            // Native TraitSet age values are shifted by their minimum. Reject an
            // unsupported time origin instead of silently changing sampling ages.
            throw new TileApplicationError(
                    "Unsupported sampling-age origin.",
                    "taxa(tree) currently requires at least one tip at height zero to preserve all sampling ages.");
        }

        TaxonSet taxonSet = new TaxonSet(Taxon.createTaxonList(names));
        if (maximumAge == 0.0) {
            return new TaxaData(taxonSet, null);
        }

        StringJoiner values = new StringJoiner(",");
        for (Node leaf : leaves) {
            String name = leaf.getID();
            if (name.contains(",")
                    || name.contains("=")
                    || !name.equals(name.replaceAll("\\s+", " ").strip())) {
                throw new TileApplicationError(
                        "Unsupported taxon name for sampling ages.",
                        "Taxon '" + name + "' cannot be represented without changing its name in BEAST's TraitSet.");
            }
            values.add(name + "=" + leaf.getHeight());
        }

        TraitSet ages = new TraitSet();
        state.setInput(ages, ages.traitNameInput, "age");
        state.setInput(ages, ages.taxaInput, taxonSet);
        state.setInput(ages, ages.traitsInput, values.toString());
        ages.initAndValidate();
        return new TaxaData(taxonSet, ages);
    }
}
