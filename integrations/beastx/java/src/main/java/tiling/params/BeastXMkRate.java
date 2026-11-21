package tiling.params;

import dr.inference.model.Parameter;

/**
 * Exchangeability {@code 2 * rate} of PhyloSpec's binary {@code mk(rate)}.
 *
 * <p>BEAST X's general substitution model sets the rate from state i to j to
 * {@code exchangeability * frequency(j)}. With two states of frequency 1/2, the expected rate of
 * change is half the exchangeability, so it is twice the PhyloSpec rate. The value follows the
 * rate when it is sampled, and the rate is kept so the XML export can write the same expression.</p>
 */
public class BeastXMkRate extends BeastXDerivedScalarParameter {

    private final Parameter rate;

    public BeastXMkRate(Parameter rate) {
        super("mkExchangeability", () -> 2.0 * rate.getParameterValue(0), rate);
        this.rate = rate;
    }

    public Parameter getRate() {
        return this.rate;
    }
}
