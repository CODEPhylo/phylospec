package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;

import java.util.List;

/**
 * The rootAge argument of Yule and BirthDeath, following BEAST 3: the density is conditioned on
 * the root age, and the root stays at that age during MCMC.
 *
 * <p>Only a fixed rootAge is supported. As in BEAST 3, the taxa must be contemporaneous, since the
 * conditioned density assumes that all tips are at age 0.</p>
 */
public final class RootAge {

    private static final String EXAMPLE = "Tree tree ~ Yule(birthRate=1.0, rootAge=10.0, taxa=taxa)";

    private RootAge() {
    }

    /**
     * Checks a given rootAge and returns it, or returns null when rootAge is not given.
     *
     * @param samplingProbability the sampling probability parameter, or null when not given
     */
    static Double conditioningAge(
            AstNode node,
            BeastXState state,
            RealScalar<?> rootAge,
            Taxa taxa,
            Parameter samplingProbability,
            String generatorName
    ) {
        if (rootAge == null) {
            return null;
        }

        double age =
                rootAge.get();

        if (!Double.isFinite(age) || age <= 0.0) {
            throw error(node, generatorName + " needs a finite rootAge greater than 0.", "Use a positive rootAge.");
        }

        if (taxa.getTaxonCount() < 2) {
            throw error(node, generatorName + " with rootAge needs at least two taxa.", "Add taxa to the tree.");
        }

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            Taxon taxon =
                    taxa.getTaxon(i);

            if (taxon.getDate() != null && taxon.getDate().getTimeValue() != 0.0) {
                throw error(
                        node,
                        generatorName + " with rootAge needs contemporaneous taxa, but '" + taxon.getId() + "' has an age.",
                        "The root-conditioned density assumes all tips at age 0 (as in BEAST 3). Remove the tip ages or rootAge."
                );
            }
        }

        // BEAST X's birth-death model refuses to combine conditioning on the root with sampling.
        if (samplingProbability != null
                && (state.stateNodes.containsKey(samplingProbability) || samplingProbability.getParameterValue(0) != 1.0)) {
            throw error(
                    node,
                    "BEAST X cannot combine rootAge with a samplingProbability other than 1.",
                    "Remove samplingProbability or rootAge."
            );
        }

        return age;
    }

    /** A Gernhard (2008) birth-death model conditioned on the age of the root. */
    static BirthDeathGernhard08Model conditionedModel(
            String id,
            Parameter diversificationRate,
            Parameter turnover,
            BirthDeathGernhard08Model.TreeType treeType
    ) {
        return new BirthDeathGernhard08Model(
                id,
                diversificationRate,
                turnover,
                null,
                treeType,
                dr.evolution.util.Units.Type.YEARS,
                true
        );
    }

    /** Keeps the root fixed: operators that could move it are left out for this tree. */
    static void fixRoot(BeastXState state, TreeModel tree) {
        state.rootConditionedTrees.add(tree);
    }

    /**
     * Moves the fixed root onto a tree observed in place of the default tree. As in BEAST 3, the
     * observed tree is not rescaled: its root must be at rootAge and its tips at age 0.
     */
    public static void bindObservedTree(AstNode node, BeastXState state, TreeModel defaultTree, TreeModel observedTree) {
        if (!state.rootConditionedTrees.remove(defaultTree)) {
            return;
        }

        double age =
                defaultTree.getNodeHeight(defaultTree.getRoot());

        double tolerance =
                8.0 * Math.ulp(age);

        boolean contemporaneous = true;

        for (int i = 0; i < observedTree.getExternalNodeCount(); i++) {
            if (Math.abs(observedTree.getNodeHeight(observedTree.getExternalNode(i))) > tolerance) {
                contemporaneous = false;
            }
        }

        double observedRoot =
                observedTree.getNodeHeight(observedTree.getRoot());

        if (Math.abs(observedRoot - age) > tolerance || !contemporaneous) {
            throw error(
                    node,
                    "The observed tree must have its root at rootAge=" + age + " and all tips at age 0, but its root is at "
                            + observedRoot + ".",
                    "Observed trees are not rescaled (as in BEAST 3). Use the observed root height as rootAge."
            );
        }

        state.rootConditionedTrees.add(observedTree);
    }

    private static TileApplicationError error(AstNode node, String title, String hint, String example) {
        return new TileApplicationError(node, title, hint, List.of(example));
    }

    private static TileApplicationError error(AstNode node, String title, String hint) {
        return error(node, title, hint, EXAMPLE);
    }
}
