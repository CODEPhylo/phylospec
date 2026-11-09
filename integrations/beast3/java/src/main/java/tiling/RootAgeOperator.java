package tiling;

import beast.base.core.Input;
import beast.base.evolution.tree.Node;
import beast.base.evolution.tree.Tree;
import beast.base.inference.Distribution;
import beast.base.inference.Operator;
import beast.base.inference.OperatorSchedule;
import beast.base.inference.StateNode;
import beast.base.internal.json.JSONObject;
import beast.base.spec.domain.Real;
import beast.base.spec.inference.parameter.RealScalarParam;
import beast.base.spec.type.RealScalar;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.List;

/** Keeps a root-age coordinate and its tree roots together while reusing a native proposal. */
public final class RootAgeOperator extends Operator {

    public final Input<Operator> delegateInput = new Input<>(
            "operator", "Native operator providing the proposal and Hastings ratio.", Input.Validate.REQUIRED);

    private final RealScalar<? extends Real> age;
    private final RealScalarParam<?> sampledAge;
    private final List<Tree> trees;
    private final StateNode driver;

    public RootAgeOperator(
            Operator delegate,
            RealScalar<? extends Real> age,
            RealScalarParam<?> sampledAge,
            List<Tree> trees,
            StateNode driver) {
        if (driver == null || trees.isEmpty() || (driver != sampledAge && !trees.contains(driver))) {
            throw new IllegalArgumentException("A root-age operator requires a linked tree or its age as driver.");
        }
        if (sampledAge != null && sampledAge != age) {
            throw new IllegalArgumentException("The sampled root age must be the original scalar parameter.");
        }
        this.age = age;
        this.sampledAge = sampledAge;
        this.trees = List.copyOf(trees);
        this.driver = driver;
        delegateInput.setValue(delegate, this);
        m_pWeight.setValue(delegate.getWeight(), this);
        setID(delegate.getID());
    }

    @Override
    public void initAndValidate() {
        // The delegate follows the existing BEASTState initialization lifecycle.
        // Do not reinitialize it or reset its tuning state here.
    }

    @Override
    public double proposal() {
        double logHastings = delegateInput.get().proposal();
        if (!Double.isFinite(logHastings)) return Double.NEGATIVE_INFINITY;

        double height = driver == sampledAge
                ? sampledAge.get()
                : ((Tree) driver).getRoot().getHeight();
        if (!Double.isFinite(height) || height <= 0.0 || !age.isValid(height)) return Double.NEGATIVE_INFINITY;

        if (sampledAge == null) {
            // Projecting a changed root back to the fixed value would change the native proposal.
            // Reject instead, leaving State to restore every edited node.
            double tolerance = 8.0 * Math.ulp(age.get());
            if (Math.abs(height - age.get()) > tolerance) return Double.NEGATIVE_INFINITY;
        }

        for (Tree tree : trees) {
            for (Node child : tree.getRoot().getChildren()) {
                if (!(height > child.getHeight())) return Double.NEGATIVE_INFINITY;
            }
        }
        if (sampledAge != null) {
            if (sampledAge.get() != height) sampledAge.set(height);
            for (Tree tree : trees) {
                if (tree.getRoot().getHeight() != height) tree.getRoot().setHeight(height);
            }
        }
        // Tree roots duplicate the same coordinate, so mirroring adds no independent dimension.
        return logHastings;
    }

    @Override
    public List<StateNode> listStateNodes() {
        LinkedHashSet<StateNode> nodes = new LinkedHashSet<>(delegateInput.get().listStateNodes());
        if (sampledAge != null) nodes.add(sampledAge);
        nodes.addAll(trees);
        return List.copyOf(nodes);
    }

    @Override
    public void setOperatorSchedule(OperatorSchedule schedule) {
        super.setOperatorSchedule(schedule);
        delegateInput.get().setOperatorSchedule(schedule);
    }

    @Override
    public void accept() {
        super.accept();
        delegateInput.get().accept();
    }

    @Override
    public void reject(int reason) {
        super.reject(reason);
        delegateInput.get().reject(reason);
    }

    @Override
    public void optimize(double logAlpha) {
        delegateInput.get().optimize(logAlpha);
    }

    @Override
    public double getCoercableParameterValue() {
        return delegateInput.get().getCoercableParameterValue();
    }

    @Override
    public void setCoercableParameterValue(double value) {
        delegateInput.get().setCoercableParameterValue(value);
    }

    @Override
    public double getTargetAcceptanceProbability() {
        return delegateInput.get().getTargetAcceptanceProbability();
    }

    @Override
    public double getDefaultTargetAcceptanceProbability() {
        return delegateInput.get().getDefaultTargetAcceptanceProbability();
    }

    @Override
    public String getPerformanceSuggestion() {
        return delegateInput.get().getPerformanceSuggestion();
    }

    @Override
    public Distribution getEvaluatorDistribution() {
        return delegateInput.get().getEvaluatorDistribution();
    }

    @Override
    public boolean requiresStateInitialisation() {
        return delegateInput.get().requiresStateInitialisation();
    }

    @Override
    public void setAcceptedRejected(int accepted, int rejected, int acceptedForCorrection, int rejectedForCorrection) {
        super.setAcceptedRejected(accepted, rejected, acceptedForCorrection, rejectedForCorrection);
        delegateInput.get().setAcceptedRejected(accepted, rejected, acceptedForCorrection, rejectedForCorrection);
    }

    @Override
    public void storeToFile(PrintWriter out) {
        // ID and counters are shared; retain the native operator's checkpoint format and tuning.
        delegateInput.get().storeToFile(out);
    }

    @Override
    public void restoreFromFile(JSONObject object) {
        super.restoreFromFile(object);
        delegateInput.get().restoreFromFile(object);
    }
}
