package tiling.params;

import dr.inference.model.Parameter;

/**
 * Common interface for PhyloSpec wrappers backed by a BEAST X parameter.
 */
public interface BeastXParam {

    Parameter getParameter();
}
