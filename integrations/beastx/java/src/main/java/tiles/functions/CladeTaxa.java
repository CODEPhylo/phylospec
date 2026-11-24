package tiles.functions;

import dr.evolution.tree.TreeUtils;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.TileApplicationError;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tips of a tree selected by their exact names, as for mrca(clade, tree) and age(node, tree).
 *
 * <p>As in BEAST 3, only exact tip names are accepted: species names are not resolved, so a
 * species shared by several tips cannot select one of them silently.</p>
 */
public final class CladeTaxa {

    private CladeTaxa() {
    }

    /** A live statistic for the age of the most recent common ancestor of the named tips. */
    public static TMRCAStatistic mrcaStatistic(AstNode node, TreeModel tree, List<String> names, String example) {
        try {
            return new TMRCAStatistic("mrcaAge", tree, resolve(node, tree, names, example), false, false);
        } catch (TreeUtils.MissingTaxonException exception) {
            // resolve() has already checked that every name is a tip of this tree.
            throw new IllegalStateException(exception);
        }
    }

    static Taxa resolve(AstNode node, TreeModel tree, List<String> names, String example) {
        if (names.isEmpty()) {
            throw error(node, "Select at least one taxon.", "Give the names of tree tips.", example);
        }

        Set<String> selected =
                new LinkedHashSet<>();

        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw error(node, "Taxon names must not be blank.", "Give the names of tree tips.", example);
            }

            selected.add(name);
        }

        Taxa taxa =
                new Taxa();

        for (String name : selected) {
            Taxon taxon =
                    findTip(tree, name);

            if (taxon == null) {
                throw error(
                        node,
                        "Taxon '" + name + "' is not a tip of the tree.",
                        "Use exact tip names; species names are not supported (as in BEAST 3).",
                        example
                );
            }

            taxa.addTaxon(taxon);
        }

        return taxa;
    }

    private static Taxon findTip(TreeModel tree, String name) {
        for (int i = 0; i < tree.getTaxonCount(); i++) {
            if (name.equals(tree.getTaxonId(i))) {
                return tree.getTaxon(i);
            }
        }

        return null;
    }

    private static TileApplicationError error(AstNode node, String title, String hint, String example) {
        return new TileApplicationError(node, title, hint, List.of(example));
    }
}
