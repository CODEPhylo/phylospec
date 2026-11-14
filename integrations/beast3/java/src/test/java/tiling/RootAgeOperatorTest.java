package tiling;

import static org.junit.jupiter.api.Assertions.*;

import beast.base.evolution.tree.Tree;
import beast.base.evolution.tree.TreeParser;
import beast.base.inference.Operator;
import beast.base.inference.OperatorSchedule;
import beast.base.inference.State;
import beast.base.inference.StateNode;
import beast.base.internal.json.JSONObject;
import beast.base.spec.domain.PositiveReal;
import beast.base.spec.evolution.operator.ScaleTreeOperator;
import beast.base.spec.inference.operator.ScaleOperator;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.util.Randomizer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RootAgeOperatorTest {
    private static final String NEWICK = "((a:1.0,b:1.0):4.0,(c:2.0,d:2.0):3.0);";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void scalarProposalsKeepNativeHastingsAndMirrorSharedTrees(boolean accept) {
        RealScalarParam<PositiveReal> age = age();
        Tree first = tree("first");
        Tree second = tree("second");
        ScaleOperator delegate = scalarScale(age, 0.1);
        RootAgeOperator joint = new RootAgeOperator(delegate, age, age, List.of(first, second), age);
        State state = state(joint.listStateNodes());
        RealScalarParam<PositiveReal> referenceAge = age();
        ScaleOperator reference = scalarScale(referenceAge, 0.1);
        State referenceState = state(List.of(referenceAge));
        assertEquals(List.of(age, first, second), joint.listStateNodes());
        assertEquals(delegate.getID(), joint.getID());
        assertEquals(delegate.getWeight(), joint.getWeight());
        double firstInternal = first.getRoot().getLeft().getHeight();
        double secondInternal = second.getRoot().getRight().getHeight();

        state.store(1);
        referenceState.store(1);
        Randomizer.setSeed(83171);
        double expectedHastings = reference.proposal();
        Randomizer.setSeed(83171);
        double actualHastings = joint.proposal();
        assertTrue(Double.isFinite(actualHastings));
        assertEquals(expectedHastings, actualHastings, 0.0);
        assertEquals(referenceAge.get(), age.get(), 0.0);
        assertNotEquals(5.0, age.get());
        assertEquals(age.get(), first.getRoot().getHeight(), 0.0);
        assertEquals(age.get(), second.getRoot().getHeight(), 0.0);
        assertEquals(firstInternal, first.getRoot().getLeft().getHeight(), 0.0);
        assertEquals(secondInternal, second.getRoot().getRight().getHeight(), 0.0);
        double accepted = age.get();
        if (!accept) state.restore();
        state.setEverythingDirty(false);
        double retained = accept ? accepted : 5.0;
        assertEquals(retained, age.get(), 0.0);
        assertEquals(retained, first.getRoot().getHeight(), 0.0);
        assertEquals(retained, second.getRoot().getHeight(), 0.0);

        state.store(2);
        Randomizer.setSeed(83172);
        assertTrue(Double.isFinite(joint.proposal()));
        assertEquals(age.get(), first.getRoot().getHeight(), 0.0);
        assertEquals(age.get(), second.getRoot().getHeight(), 0.0);
        state.restore();
        state.setEverythingDirty(false);
        assertEquals(retained, age.get(), 0.0);
        assertEquals(retained, first.getRoot().getHeight(), 0.0);
        assertEquals(retained, second.getRoot().getHeight(), 0.0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void treeProposalsKeepNativeHastingsAndMirrorTheAgeAndOtherRoots(boolean accept) {
        RealScalarParam<PositiveReal> age = age();
        Tree first = tree("first");
        Tree second = tree("second");
        ScaleTreeOperator delegate = treeScale(first);
        RootAgeOperator joint = new RootAgeOperator(delegate, age, age, List.of(first, second), first);
        State state = state(joint.listStateNodes());
        Tree referenceTree = tree("reference");
        ScaleTreeOperator reference = treeScale(referenceTree);
        State referenceState = state(List.of(referenceTree));
        String beforeFirst = first.getRoot().toNewick();
        String beforeSecond = second.getRoot().toNewick();
        double secondInternal = second.getRoot().getRight().getHeight();

        state.store(1);
        referenceState.store(1);
        Randomizer.setSeed(49117);
        double expectedHastings = reference.proposal();
        Randomizer.setSeed(49117);
        double actualHastings = joint.proposal();
        assertTrue(Double.isFinite(actualHastings));
        assertEquals(
                expectedHastings,
                actualHastings,
                0.0,
                "Mirrored roots are not extra dimensions in the scale Jacobian.");
        assertEquals(referenceTree.getRoot().toNewick(), first.getRoot().toNewick());
        assertEquals(first.getRoot().getHeight(), age.get(), 0.0);
        assertEquals(age.get(), second.getRoot().getHeight(), 0.0);
        assertEquals(secondInternal, second.getRoot().getRight().getHeight(), 0.0);
        double retainedAge = accept ? age.get() : 5.0;
        String retainedFirst = accept ? first.getRoot().toNewick() : beforeFirst;
        String retainedSecond = accept ? second.getRoot().toNewick() : beforeSecond;
        if (!accept) state.restore();
        state.setEverythingDirty(false);
        assertEquals(retainedAge, age.get(), 0.0);
        assertEquals(retainedFirst, first.getRoot().toNewick());
        assertEquals(retainedSecond, second.getRoot().toNewick());

        state.store(2);
        Randomizer.setSeed(49118);
        assertTrue(Double.isFinite(joint.proposal()));
        state.restore();
        state.setEverythingDirty(false);
        assertEquals(retainedAge, age.get(), 0.0);
        assertEquals(retainedFirst, first.getRoot().toNewick());
        assertEquals(retainedSecond, second.getRoot().toNewick());
    }

    @Test
    void rootBelowAnyLinkedChildIsRejectedAndStateRestoresTheOriginalParameter() {
        RealScalarParam<PositiveReal> age = age();
        Tree first = tree("first");
        Tree second = tree("second");
        Operator delegate = proposal(age, () -> age.set(1.5));
        RootAgeOperator joint = new RootAgeOperator(delegate, age, age, List.of(first, second), age);
        State state = state(joint.listStateNodes());
        state.store(1);
        assertEquals(Double.NEGATIVE_INFINITY, joint.proposal());
        assertEquals(1.5, age.get());
        assertEquals(5.0, first.getRoot().getHeight());
        state.restore();
        state.setEverythingDirty(false);
        assertEquals(5.0, age.get());
        assertEquals(5.0, second.getRoot().getHeight());
    }

    @Test
    void treeDrivenMoveRespectsTheOriginalAgeBounds() {
        RealScalarParam<PositiveReal> age = new RealScalarParam<>(5.0, PositiveReal.INSTANCE) {
            @Override
            public Double getUpper() {
                return 6.0;
            }
        };
        Tree tree = tree("tree");
        RootAgeOperator joint =
                new RootAgeOperator(proposal(tree, () -> tree.getRoot().setHeight(7.0)), age, age, List.of(tree), tree);
        State state = state(joint.listStateNodes());
        state.store(1);
        assertEquals(Double.NEGATIVE_INFINITY, joint.proposal());
        assertEquals(5.0, age.get());
        state.restore();
        state.setEverythingDirty(false);
        assertEquals(5.0, tree.getRoot().getHeight());
    }

    @Test
    void fixedRootMovesAreRejectedRatherThanProjectedBack() {
        RealScalarParam<PositiveReal> fixed = age();
        Tree tree = tree("tree");
        RootAgeOperator joint = new RootAgeOperator(treeScale(tree), fixed, null, List.of(tree), tree);
        State state = state(joint.listStateNodes());
        assertEquals(List.of(tree), joint.listStateNodes());
        String original = tree.getRoot().toNewick();
        state.store(1);
        Randomizer.setSeed(49117);
        assertEquals(Double.NEGATIVE_INFINITY, joint.proposal());
        assertNotEquals(5.0, tree.getRoot().getHeight());
        assertEquals(5.0, fixed.get());
        state.restore();
        state.setEverythingDirty(false);
        assertEquals(original, tree.getRoot().toNewick());
    }

    @Test
    void fixedRootAllowsAnInternalNodeMoveAndRetainsItsHastingsRatio() {
        Tree tree = tree("tree");
        RootAgeOperator joint = new RootAgeOperator(
                proposal(tree, () -> tree.getRoot().getLeft().setHeight(1.25)), age(), null, List.of(tree), tree);
        State state = state(joint.listStateNodes());
        state.store(1);
        assertEquals(0.25, joint.proposal());
        assertEquals(5.0, tree.getRoot().getHeight());
        assertEquals(1.25, tree.getRoot().getLeft().getHeight());
        state.restore();
        state.setEverythingDirty(false);
        assertEquals(1.0, tree.getRoot().getLeft().getHeight());
    }

    @Test
    void adaptationCountersAndCheckpointUseTheNativeOperatorState() throws Exception {
        RealScalarParam<PositiveReal> age = age();
        ScaleOperator delegate = scalarScale(age, 0.1);
        RootAgeOperator joint = new RootAgeOperator(delegate, age, age, List.of(tree("tree")), age);
        ScaleOperator reference = scalarScale(age(), 0.1);
        schedule(joint);
        schedule(reference);
        joint.accept();
        reference.accept();
        joint.reject(-2);
        reference.reject(-2);
        joint.accept();
        reference.accept();
        assertCounts(reference, joint);
        assertCounts(reference, delegate);
        assertEquals(reference.getTargetAcceptanceProbability(), joint.getTargetAcceptanceProbability());
        joint.optimize(-0.2);
        reference.optimize(-0.2);
        assertEquals(reference.getCoercableParameterValue(), joint.getCoercableParameterValue(), 0.0);
        assertEquals(reference.getPerformanceSuggestion(), joint.getPerformanceSuggestion());

        StringWriter stored = new StringWriter();
        joint.storeToFile(new PrintWriter(stored));
        RealScalarParam<PositiveReal> restoredAge = age();
        ScaleOperator restoredDelegate = scalarScale(restoredAge, 0.9);
        RootAgeOperator restored = new RootAgeOperator(
                restoredDelegate, restoredAge, restoredAge, List.of(tree("restoredTree")), restoredAge);
        schedule(restored);
        restored.restoreFromFile(new JSONObject(stored.toString()));
        assertCounts(joint, restored);
        assertCounts(joint, restoredDelegate);
        assertEquals(joint.getCoercableParameterValue(), restored.getCoercableParameterValue(), 0.0);
        joint.optimize(-0.7);
        restored.optimize(-0.7);
        assertEquals(joint.getCoercableParameterValue(), restored.getCoercableParameterValue(), 0.0);
        restored.setAcceptedRejected(7, 3, 6, 2);
        assertCounts(restored, restoredDelegate);
    }

    private static RealScalarParam<PositiveReal> age() {
        RealScalarParam<PositiveReal> age = new RealScalarParam<>(5.0, PositiveReal.INSTANCE);
        age.setID("rootAge");
        return age;
    }

    private static Tree tree(String id) {
        Tree tree = new TreeParser(NEWICK);
        tree.setID(id);
        return tree;
    }

    private static ScaleOperator scalarScale(RealScalarParam<?> parameter, double factor) {
        ScaleOperator operator = new ScaleOperator();
        operator.initByName("parameter", parameter, "scaleFactor", factor, "weight", 5.0);
        operator.setID("ageScaler");
        return operator;
    }

    private static ScaleTreeOperator treeScale(Tree tree) {
        ScaleTreeOperator operator = new ScaleTreeOperator();
        operator.initByName("tree", tree, "scaleFactor", 0.1, "weight", 5.0);
        operator.setID("treeScaler");
        return operator;
    }

    private static Operator proposal(StateNode driver, Runnable mutation) {
        Operator operator = new Operator() {
            @Override
            public void initAndValidate() {}

            @Override
            public double proposal() {
                mutation.run();
                return 0.25;
            }

            @Override
            public List<StateNode> listStateNodes() {
                return List.of(driver);
            }
        };
        operator.m_pWeight.setValue(5.0, operator);
        return operator;
    }

    private static State state(List<? extends StateNode> nodes) {
        State state = new State();
        state.initByName("stateNode", nodes);
        state.initialise();
        return state;
    }

    private static void schedule(Operator operator) {
        OperatorSchedule schedule = new OperatorSchedule();
        schedule.initByName("operator", operator, "autoOptimizeDelay", 0);
    }

    private static void assertCounts(Operator expected, Operator actual) {
        assertEquals(expected.get_m_nNrAccepted(), actual.get_m_nNrAccepted());
        assertEquals(expected.get_m_nNrRejected(), actual.get_m_nNrRejected());
        assertEquals(expected.get_m_nNrAcceptedForCorrection(), actual.get_m_nNrAcceptedForCorrection());
        assertEquals(expected.get_m_nNrRejectedForCorrection(), actual.get_m_nNrRejectedForCorrection());
    }
}
