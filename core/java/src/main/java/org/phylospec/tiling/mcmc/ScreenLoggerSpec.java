package org.phylospec.tiling.mcmc;

import java.util.List;

public record ScreenLoggerSpec<T>(int logEvery, List<T> parameters) {}
