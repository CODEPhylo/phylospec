package tiling.params;

import dr.inference.model.Parameter;

/**
 * Log-space mean {@code mu = log(mean) - logSd^2 / 2} of a LogNormal with a real-space mean and
 * a log-space standard deviation, as in PhyloSpec's {@code LogNormal(mean, logSd)}.
 *
 * <p>BEAST X has no parameterization for this pair, so the distribution uses
 * {@code (mu, sigma = logSd)} instead. The value follows {@code mean} and {@code logSd} when they
 * are sampled. The two parameters are kept so the XML export can write the same expression.</p>
 */
public class BeastXLogNormalMu extends BeastXDerivedScalarParameter {

    private final Parameter mean;
    private final Parameter logSd;

    public BeastXLogNormalMu(Parameter mean, Parameter logSd) {
        super(
                "mu",
                () -> Math.log(mean.getParameterValue(0)) - 0.5 * Math.pow(logSd.getParameterValue(0), 2),
                mean,
                logSd
        );
        this.mean = mean;
        this.logSd = logSd;
    }

    public Parameter getMean() {
        return this.mean;
    }

    public Parameter getLogSd() {
        return this.logSd;
    }
}
