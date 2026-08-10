package tiling.operators.joint;

import tiling.BeastXState;
import tiling.operators.OperatorSpec;

import java.util.List;

/** Selects operators that jointly update multiple related state objects. */
public interface JointOperatorSelector {
    List<OperatorSpec> select(BeastXState state);
}
