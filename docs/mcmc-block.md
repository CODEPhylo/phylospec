# MCMC Block



This document outlines the features of the MCMC block that are shared by all engines, and the command line arguments that go with it.



## What Belongs Where

The model file describes the analysis, the command line one execution of it. A setting belongs on the command line if changing it still gives the same analysis.


| Place                   | What belongs there                                                             |
| ----------------------- | ------------------------------------------------------------------------------ |
| *data* / *model* blocks | What defines the posterior.                                                    |
| *mcmc* block            | How it is sampled and what is logged, for every engine.                        |
| Engine block            | Sampling settings of a single engine (e.g. operators).                         |
| Command line            | What leaves the result statistically equivalent (e.g. seed, output directory). |


Everything set on the command line is recorded with the run, so the model file and the run record reproduce it.

## MCMC Block



```phylospec
mcmc {
    Sampler sampler = mcmc(
         chainLength=1000000
    )
    Logger screenLogger = screenLogger(
        logEvery=100, parameters=[birthRate], parameterNames=["myBirthRate"]
    )
    Logger fileLogger = fileLogger(
        file="log.log", logEvery=1000, parameters=[birthRate]
    )
    Logger treeLogger = treeLogger(
        file="trees.trees", logEvery=10000, tree
    )
}
```



- *mcmc*: a simple MCMC sampler. At most one sampler per block. If none is given, *mcmc()* with its defaults is used. Other samplers can be added later as further functions returning a *Sampler*.
  - *chainLength*: if not given, 10 million is used.
- *screenLogger*, *fileLogger*: always log the posterior, prior and likelihood.
  - *parameters*: optional list of the logged parameters. If not given, all assigned parameters including inline assignments and tree statistics are logged.
  - *parameterNames*: optional list of names used in the log instead of the variable names. Must have the same length as *parameters*.
- *fileLogger*, *treeLogger*: *file* is needed.
- *treeLogger*: logs a single tree. tree is needed.
- *logEvery*: defaults to 1000 for every logger.
- *No loggers*: a missing kind of logger is added with its defaults. This gives a screen logger, a file logger writing to *&lt;run-name&gt;.log* and, if a tree is sampled, a tree logger writing to *&lt;run-name&gt;.trees*.

*Change:*

The MCMC block is not type-checked. Its functions are not core components, and the type checker skips the block. Instead, every engine validates the block with the same engine-agnostic code:

- A block validator runs before the engine maps the model. It checks that the block only declares samplers and loggers, that every argument is known, that the needed arguments are given, and that there is at most one sampler.
- The sampler and the loggers are collected in specs shared by all engines. The specs check the values: *chainLength* and *logEvery* are positive, *file* is not empty, and *parameterNames* has the same length as *parameters*.

The settings *chainLength*, *defaultLogEvery*, *randomSeed* and *outputPrefix* are no longer part of the block. Using them is an error that points to the replacement.

Everything else (e.g. operator settings) should be in a specific engine block (e.g. "beastX").

## Command Line Arguments

Entries marked *new* are proposed, all others exist.


| Command                                             | Purpose                                                                        |
| --------------------------------------------------- | ------------------------------------------------------------------------------ |
| *check &lt;model.phylospec&gt;*                     | Check syntax, types and engine mappings.                                       |
| *export &lt;model.phylospec&gt; --xml &lt;file&gt;* | Write the analysis XML to the given file without running it. BEAST 2 / BEAST X specific. |
| *run &lt;model.phylospec&gt;*                       | Check and run the model.                                                       |
| *resume &lt;run-dir&gt;* (*new*)                    | Continue a run from its last checkpoint, with the engine recorded for the run. |



| Option                | Commands           | Purpose                                             |
| --------------------- | ------------------ | --------------------------------------------------- |
| *--engine &lt;id&gt;* | check, export, run | Engine to use: beast3 (default) or beastx.          |
| *--out &lt;dir&gt;*   | run                | Run directory. Must be absent or empty.             |
| *--run-name &lt;prefix&gt;* | run          | Prefix of the default output files in the run directory. Defaults to the name of the model file. |
| *--seed &lt;n&gt;*    | export, run        | Random seed. If not given, *run* chooses one uniformly and *export* sets none. |
| *--library &lt;ids&gt;* | check, export, run | Tile libraries to use, in order of preference.    |
| *--adapter-path &lt;dir&gt;* | check, export, run | Directory of an unpacked adapter to load. Can be repeated. |
| *--debug*             | all                | Show raw engine output and stack traces.            |
| *-h*, *--help*        |                    | Show the help.                                      |
| *--version*           |                    | Show the versions.                                  |


*Replicates*: run the same model multiple times with different *--seed* and *--out* values.
