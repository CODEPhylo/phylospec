package tiling.operators.joint;

import tiling.BeastXState;
import tiling.operators.OperatorSpec;

import java.util.List;

/** Selects joint operators that update multiple related model objects. */
public interface JointOperatorSelector {

    List<OperatorSpec> select(BeastXState state);
}
