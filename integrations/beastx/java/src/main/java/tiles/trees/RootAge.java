package tiles.trees;

import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evomodel.speciation.BirthDeathGernhard08Model;
import dr.evomodel.tree.DefaultTreeModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.AbstractDistributionLikelihood;
import dr.inference.model.AbstractModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import dr.inference.model.Variable;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.TileApplicationError;
import org.phylospec.types.RealScalar;
import tiling.BeastXState;
import tiling.params.BeastXDerivedScalarParameter;
import tiling.params.BeastXRealScalarParam;
import tiling.params.BeastXStatisticRealScalar;
import tiling.rpn.BeastXRPNStatistic;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The rootAge argument of Yule and BirthDeath, following BEAST 3: the density is conditioned on
 * the root age. A fixed rootAge keeps the root at that age during MCMC. A sampled rootAge is the
 * root height of the tree: its prior applies to the root height, and the tree operators move it
 * (BEAST 3 moves both together with its RootAgeOperator, which BEAST X XML cannot express).
 *
 * <p>As in BEAST 3, the taxa must be contemporaneous, since the conditioned density assumes that
 * all tips are at age 0.</p>
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

    /**
     * A fixed rootAge keeps the root fixed: operators that could move it are left out for this tree.
     * A sampled rootAge becomes the tree's root height: its prior is moved onto the root height
     * parameter, which takes its name, and it is no longer a parameter of its own.
     */
    static void bindRoot(AstNode node, BeastXState state, RealScalar<?> rootAge, DefaultTreeModel tree, String generatorName) {
        Parameter parameter = rootAge instanceof BeastXRealScalarParam<?> scalar ? scalar.getParameter() : null;

        if (parameter == null || !state.stateNodes.containsKey(parameter)) {
            // A value computed from sampled values would stay at its starting value.
            if (rootAge instanceof BeastXStatisticRealScalar<?>
                    || parameter != null && parameter.getClass() != Parameter.Default.class) {
                throw error(node, generatorName + " needs a fixed or sampled rootAge, not a value computed from sampled values.",
                        "Give rootAge a number or a sampled variable.");
            }

            state.rootConditionedTrees.add(tree);
            return;
        }

        if (!(state.priorDistributions.remove(parameter) instanceof AbstractDistributionLikelihood prior)) {
            throw error(node, "The sampled rootAge of " + generatorName + " needs a prior distribution.",
                    "Draw rootAge from a distribution, as in PositiveReal rootAge ~ LogNormal(logMean=1.0, logSd=0.5).");
        }

        Parameter rootHeight = tree.getRootHeightParameter();

        prior.getDataList().clear();
        if (prior.getModel() instanceof AbstractModel model && model.hasVariable(parameter)) {
            model.removeVariable(parameter);
        }
        prior.addData(rootHeight);

        state.priorDistributions.put(rootHeight, prior);
        state.stateNodes.remove(parameter);
        state.stateNodesByPhyloSpecName.replaceAll((name, value) -> value == parameter ? rootHeight : value);
        state.sampledRootAges.put(tree, parameter);
        rootHeight.setId(parameter.getId());
    }

    /**
     * A sampled rootAge has been replaced by the root height of its tree, so it can only be used as
     * that rootAge: anywhere else it would stay at its starting value.
     */
    public static void requireSampledRootAgesUsedOnlyAsRootAge(BeastXState state, Model posterior) {
        for (Parameter rootAge : state.sampledRootAges.values()) {
            boolean used = uses(posterior, rootAge, Collections.newSetFromMap(new IdentityHashMap<>()));

            for (Statistic statistic : state.calculationNodes.keySet()) {
                used |= statistic instanceof BeastXRPNStatistic rpn && rpn.getVariables().containsValue(rootAge);
            }

            if (used) {
                throw new IllegalArgumentException(
                        "The sampled rootAge '" + rootAge.getId() + "' is the root height of its tree in BEAST X,"
                                + " so it can only be used as rootAge. Use rootAge(tree=...) to refer to the root age"
                                + " elsewhere."
                );
            }
        }
    }

    private static boolean uses(Model model, Parameter parameter, Set<Model> visited) {
        if (!visited.add(model)) {
            return false;
        }

        for (int i = 0; i < model.getVariableCount(); i++) {
            if (dependsOn(model.getVariable(i), parameter)) {
                return true;
            }
        }

        for (int i = 0; i < model.getModelCount(); i++) {
            if (uses(model.getModel(i), parameter, visited)) {
                return true;
            }
        }

        return false;
    }

    private static boolean dependsOn(Variable<?> variable, Parameter parameter) {
        if (variable == parameter) {
            return true;
        }

        if (variable instanceof BeastXDerivedScalarParameter derived) {
            for (Parameter dependency : derived.getDependencies()) {
                if (dependsOn(dependency, parameter)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Moves the fixed root onto a tree observed in place of the default tree. As in BEAST 3, the
     * observed tree is not rescaled: its root must be at rootAge and its tips at age 0.
     */
    public static void bindObservedTree(AstNode node, BeastXState state, TreeModel defaultTree, TreeModel observedTree) {
        // A sampled rootAge is the root height of the sampled tree, which an observed tree replaces.
        if (state.sampledRootAges.containsKey(defaultTree)) {
            throw error(node, "An observed tree cannot have a sampled rootAge in BEAST X.",
                    "Its root age is fixed by the observation: give rootAge the observed root height.");
        }

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
