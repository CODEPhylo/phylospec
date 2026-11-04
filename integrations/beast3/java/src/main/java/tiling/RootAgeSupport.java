package tiling;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeInterface;
import beast.base.evolution.tree.TreeUtils;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.domain.Real;
import beast.base.spec.evolution.operator.ScaleTreeOperator;
import beast.base.spec.evolution.speciation.YuleModel;
import beast.base.spec.evolution.tree.coalescent.RandomTree;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import beastconfig.BEASTState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.phylospec.ast.AstNode;
import org.phylospec.tiling.errors.TileApplicationError;
import tiles.input.TaxaData;

/** Binding a supplied root age is separate from conditioning the native density on the root. */
public final class RootAgeSupport {
    private RootAgeSupport() {}

    public static void prepare(
            BEASTState state,
            YuleModel model,
            Input<RealScalar<? extends Real>> input,
            RandomTree initialTree,
            RealScalar<? extends Real> age,
            AstNode source) {
        if (age == null) return;
        if (!validAge(age.get())) {
            throw new TileApplicationError(
                    source,
                    "Cannot initialize a root-conditioned tree.",
                    "rootAge must be finite and greater than zero for a nondegenerate tree.");
        }
        state.setInput(model, input, age);
        state.setInput(model, model.conditionalOnRootInput, true);
        // This is an initializer setting only. The density retains the original age input.
        // Keeping a separate constant also avoids a StateNode-to-StateNode dependency.
        state.setInput(
                initialTree, initialTree.rootHeightInput, new RealScalarParam<>(age.get(), PositiveReal.INSTANCE));
    }

    public static void bindTree(
            BEASTState state, YuleModel model, Tree tree, TaxaData taxa, RealScalar<? extends Real> age) {
        state.setInput(model, model.treeInput, tree);
        if (age == null) return;
        Set<String> actual = new HashSet<>();
        tree.getExternalNodes().forEach(tip -> actual.add(tip.getID()));
        if (tree.getLeafNodeCount() != taxa.size()
                || !actual.equals(new HashSet<>(taxa.taxonSet().asStringList()))) {
            throw new TileApplicationError(
                    "Incompatible taxa for a root-conditioned tree.",
                    "The tree must contain exactly the supplied taxa.");
        }
        if (tree.getTaxonset() == null) state.setInput(tree, tree.m_taxonset, taxa.taxonSet());
        try {
            requireCompatibleTree(tree, age);
        } catch (IllegalArgumentException exception) {
            throw new TileApplicationError("Incompatible tree and rootAge.", exception.getMessage());
        }
    }

    public static void validateInputs(YuleModel model, RealScalar<? extends Real> age) {
        // Native Yule's root check assumes a separate legacy MRCAPrior. Here the age input
        // and coupled proposals supply that constraint; retain every ordinary Input check.
        for (Input<?> input : model.listInputs()) input.validate();
        if (!model.conditionalOnRootInput.get() || model.originHeightParameterInput.get() != null) {
            throw new IllegalArgumentException("rootAge requires conditioning on the root, not on the origin.");
        }
        requireCompatibleTree(model.treeInput.get(), age);
    }

    public static boolean validAge(double age) {
        return Double.isFinite(age) && age > 0.0;
    }

    public static boolean matches(double height, double age) {
        return validAge(age) && Double.isFinite(height) && Math.abs(height - age) <= 8.0 * Math.ulp(age);
    }

    public static boolean compatibleTree(TreeInterface tree, RealScalar<? extends Real> age) {
        return tree != null
                && tree.getLeafNodeCount() >= 2
                && matches(tree.getRoot().getHeight(), age.get())
                && TreeUtils.isUltrametric(tree, 8.0 * Math.ulp(age.get()));
    }

    private static void requireCompatibleTree(TreeInterface tree, RealScalar<? extends Real> age) {
        if (!compatibleTree(tree, age)) {
            throw new IllegalArgumentException(
                    "Root-conditioned Yule/BirthDeath requires at least two contemporaneous taxa "
                            + "and a tree root equal to the finite positive rootAge; observed trees are not rescaled.");
        }
    }

    /** Install after all declarations, so shared ages include every linked sampled tree. */
    public static void configureOperators(BEASTState state) {
        List<Distribution> distributions = new ArrayList<>(state.priorDistributions.values());
        distributions.addAll(state.likelihoodDistributions);
        var links = new IdentityHashMap<RealScalar<? extends Real>, Link>();
        for (Distribution distribution : distributions) {
            RealScalar<? extends Real> age = null;
            if (distribution instanceof LiveYuleModel model) age = model.rootAgeParameterInput.get();
            else if (distribution instanceof LiveBirthDeathGernhard08Model model)
                age = model.rootAgeParameterInput.get();
            if (age == null) continue;
            Tree tree = (Tree) ((YuleModel) distribution).treeInput.get();
            RealScalarParam<?> parameter =
                    age instanceof RealScalarParam<?> scalar && state.stateNodes.containsKey(scalar) ? scalar : null;
            if (parameter == null
                    && hasStateDependency(age, state, Collections.newSetFromMap(new IdentityHashMap<>()))) {
                throw new IllegalArgumentException(
                        "A stochastic rootAge must currently be a directly sampled real scalar parameter. "
                                + "Derived rootAge expressions cannot be updated by tree-root proposals.");
            }
            if (parameter != null && !state.stateNodes.containsKey(tree)) {
                throw new IllegalArgumentException(
                        "A sampled rootAge cannot currently be bound to a fully observed tree. "
                                + "Use the observed root height as a fixed rootAge; observed data must not be changed by proposals.");
            }
            requireCompatibleTree(tree, age);
            if (state.stateNodes.containsKey(tree)) {
                Link link = links.computeIfAbsent(age, ignored -> new Link(parameter, new ArrayList<>()));
                if (!link.trees().contains(tree)) link.trees().add(tree);
            }
        }
        if (links.isEmpty()) return;

        List<Operator> operators = new ArrayList<>();
        for (Operator operator : state.operators) {
            if (operator instanceof RootAgeOperator) {
                operators.add(operator);
                continue;
            }
            List<StateNode> nodes = operator.listStateNodes();
            RootAgeOperator replacement = null;
            boolean omit = false;
            for (var entry : links.entrySet()) {
                Link link = entry.getValue();
                List<StateNode> drivers = nodes.stream()
                        .filter(node -> node == link.parameter() || link.trees().contains(node))
                        .toList();
                if (drivers.isEmpty()) continue;
                if (nodes.size() != 1
                        || replacement != null
                        || operator.getEvaluatorDistribution() != null
                        || !operator.requiresStateInitialisation()) {
                    throw new IllegalArgumentException("Root-age binding currently supports ordinary operators on one "
                            + "age parameter or one linked tree at a time: " + operator.getID());
                }
                // Scaling the whole tree always changes a fixed root and would always be rejected.
                if (link.parameter() == null && operator instanceof ScaleTreeOperator) {
                    omit = true;
                } else {
                    replacement = new RootAgeOperator(
                            operator, entry.getKey(), link.parameter(), link.trees(), drivers.getFirst());
                    state.initBEASTObject(replacement);
                }
            }
            if (!omit) operators.add(replacement == null ? operator : replacement);
        }
        state.operators.clear();
        state.operators.addAll(operators);
    }

    private static boolean hasStateDependency(Object value, BEASTState state, Set<BEASTInterface> visited) {
        if (value instanceof StateNode node && state.stateNodes.containsKey(node)) return true;
        if (!(value instanceof BEASTInterface object) || !visited.add(object)) return false;
        for (BEASTInterface input : object.listActiveBEASTObjects()) {
            if (hasStateDependency(input, state, visited)) return true;
        }
        return false;
    }

    private record Link(RealScalarParam<?> parameter, List<Tree> trees) {}
}
