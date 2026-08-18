package org.phylospec.tiling.mcmc;

import java.util.List;

public record FileLoggerSpec<T>(int logEvery, String fileName, List<T> parameters) {}
