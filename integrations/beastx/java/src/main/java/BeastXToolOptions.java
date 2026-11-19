import dr.math.MathUtils;

/** Options shared by the BEAST X worker tools: an optional {@code --seed <n>} and {@code --debug}. */
record BeastXToolOptions(Long seed, boolean debug) {

    /** Parses the options following the positional arguments. Returns null if they are malformed. */
    static BeastXToolOptions parse(String[] args, int firstOption) {
        Long seed = null;
        boolean debug = false;

        for (int i = firstOption; i < args.length; i++) {
            if (args[i].equals("--debug") && !debug) {
                debug = true;
            } else if (args[i].equals("--seed") && seed == null && i + 1 < args.length) {
                try {
                    seed = Long.parseLong(args[++i]);
                } catch (NumberFormatException invalid) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return new BeastXToolOptions(seed, debug);
    }

    /** Seeds the BEAST X random number generator. */
    static void applySeed(long seed) {
        if (seed == 0) throw new IllegalArgumentException("BEAST X requires a nonzero seed.");
        MathUtils.setSeed(seed);
    }
}
