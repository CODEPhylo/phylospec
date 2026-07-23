# PhyloSpec Engine Benchmark

This directory contains the benchmarking and validation pipeline of the different parsers.

## Getting started

Install [uv](https://docs.astral.sh/uv/getting-started/installation/) and [just](https://just.systems/man/en/packages.html), then enter this directory:

```shell
cd benchmark
uv sync
```

The project requires Python 3.13 or newer. `uv sync` creates the local virtual
environment and installs the project environment described by `pyproject.toml`.

Use `uv add <package>` to add new dependencies to the project.

## Build the engine JARs

Build the BEAST 3 and BEAST X fat JARs and copy them into `benchmark/jars`:

```shell
just build-jars
```

This command runs the Maven reactor build to create the current JARs. The BEAST X Maven dependency must already be installed as described in the BEAST X integration documentation.

## Run the engines

`main.py` contains an example code running `example.phylospec` with BEAST 3 followed by BEAST X. Like any other python script with `uv`, it can be executed with `uv run`:

```shell
uv run main.py
```
