package org.phylospec.tiling.mcmc;

import java.util.List;
import java.util.Map;

public record FileLoggerSpec<T>(
        int logEvery, String fileName, List<T> parameters, Map<String, T> namedParameters) {}
