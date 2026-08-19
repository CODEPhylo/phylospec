package tiling;

import beast.base.inference.Operator;
import beast.base.inference.StateNode;
import beastconfig.BEASTState;
import org.phylospec.tiling.TypeToken;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/// An {@link UnboundDistribution} that additionally carries a default state node, so it can be
/// resolved without a value supplied from the outside.
public class BoundDistribution<T extends StateNode, O extends beast.base.inference.Distribution> extends UnboundDistribution<T, O> {

    private final T stateNode;
    private final BiFunction<T, BEASTState, List<Operator>> operatorFactory;

    /**
     * Constructs a drawable distribution with the given BEAST distribution, the default state node,
     * the setter that attaches a parameter value to the distribution, and the factory
     * building the operators to register for the default state node when it is drawn.
     */
    public BoundDistribution(O distribution, T defaultState, Consumer<T> setStateNodeFunc, BiFunction<T, BEASTState, List<Operator>> operatorFactory) {
        super(distribution, setStateNodeFunc);
        this.stateNode = defaultState;
        this.operatorFactory = operatorFactory;
    }

    /**
     * Takes the sampled path: wires the default state node into the distribution, registers it
     * under the given id and type token, registers the distribution as that state node's prior
     * under {@code stateId + "_prior"}, and registers the operators the operator factory returns.
     * Returns the state node, now fully registered.
     *
     * @throws RuntimeException if this distribution has already been resolved
     */
    public T draw(BEASTState beastState, TypeToken<?> typeToken, String stateId) {
        this.markAsConsumed();
        this.setStateNodeFunc.accept(this.stateNode);
        beastState.addStateNodeWithoutOperators(this.stateNode, typeToken, stateId);
        beastState.addPriorDistribution(this.stateNode, this.distribution, stateId + "_prior");
        beastState.addOperators(this.stateNode, this.operatorFactory.apply(this.stateNode, beastState));
        return this.stateNode;
    }

    public T getStateNode() {
        return this.stateNode;
    }

}
