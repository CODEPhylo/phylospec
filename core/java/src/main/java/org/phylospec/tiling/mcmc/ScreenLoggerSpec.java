package org.phylospec.tiling.mcmc;

import java.util.List;
import java.util.Map;

public record ScreenLoggerSpec<T>(
        int logEvery, List<T> parameters, Map<String, T> namedParameters) {}
