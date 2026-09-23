package tiling.operators;

/**
 * Model-level meaning of an estimated BEAST X parameter.
 *
 * <p>BEAUti chooses operators from a parameter's role in the model rather
 * than from its Java or PhyloSpec type alone. Tiles register these roles and
 * {@link OperatorSelector} translates them into the BEAUti 10.5 default
 * operator schedule.</p>
 */
public enum ParameterRole {
    SUBSTITUTION_SCALE,
    SUBSTITUTION_SIMPLEX,
    SITE_MODEL_SCALE,
    SITE_MODEL_PROPORTION,
    CLOCK_RATE,
    RELAXED_CLOCK_CATEGORIES,
    DEMOGRAPHIC_SCALE,
    DEMOGRAPHIC_GROWTH_RATE,
    TREE_PRIOR_SCALE,
    TREE_PRIOR_PROPORTION,
    SERIAL_TREE_PRIOR_SCALE,
    SERIAL_TREE_PRIOR_PROPORTION
}
