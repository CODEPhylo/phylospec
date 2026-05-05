package tiling;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the BEAST state that is built up incrementally during tiling.
 * Tracks state nodes, calculation nodes, distributions, operators, and loggers.
 * Provides utilities for assigning unique IDs, wiring inputs, and initializing
 * BEAST objects in the correct order.
 */
public class BeastXState {

    public final String runName;

    private final Set<String> ids;

    public BeastXState(String runName) {
        this.runName = runName;
        ids = new HashSet<>();
    }

    /**
     * Gets the next available ID based on the proposed ID.
     */
    public String getAvailableID(String proposal) {
        if (!this.ids.contains(proposal)) {
            this.ids.add(proposal);
            return proposal;
        }

        int prefix = 2;
        while (this.ids.contains(proposal + "_" + prefix)) {
            prefix++;
        }

        proposal = proposal + "_" + prefix;
        this.ids.add(proposal);
        return proposal;
    }
    
}
