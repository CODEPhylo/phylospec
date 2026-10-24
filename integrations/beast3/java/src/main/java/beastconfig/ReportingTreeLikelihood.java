package beastconfig;

import beagle.BeagleFlag;
import beagle.BeagleFactory;
import beagle.BeagleInfo;
import beast.base.spec.evolution.likelihood.TreeLikelihood;

/** Exposes the backend selected by BEAST without changing its likelihood calculation. */
public class ReportingTreeLikelihood extends TreeLikelihood {
    public Backend backend() {
        if (beagle != null && beagle.getBeagle() != null) {
            var details = beagle.getBeagle().getDetails();
            var resource = BeagleFactory.getResourceDetails(details.getResourceNumber());
            return new Backend(
                    "BEAGLE", BeagleInfo.getVersion(), details.getResourceNumber(),
                    resource == null ? null : resource.getName(), BeagleFlag.toString(details.getFlags()));
        }
        if (getLikelihoodCore() == null) {
            throw new IllegalStateException("Tree likelihood has not initialized a calculation backend.");
        }
        return new Backend("Java", null, null, null, null);
    }

    public record Backend(
            String implementation, String version, Integer resourceNumber, String resourceName, String resourceFlags) {}
}
