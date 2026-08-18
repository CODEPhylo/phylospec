package org.phylospec.tiling.mcmc;

public record TreeLoggerSpec<T>(int logEvery, String fileName, T tree) {}
