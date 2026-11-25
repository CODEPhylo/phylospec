# MCMC Block



This document outlines the features of the MCMC block that are shared by all engines, and the command line arguments that go with it.



## What Belongs Where

The model file describes the analysis, the command line one execution of it. A setting belongs on the command line if changing it still gives the same analysis.


| Place                   | What belongs there                                                             |
| ----------------------- | ------------------------------------------------------------------------------ |
| *data* / *model* blocks | What defines the posterior.                                                    |
| *mcmc* block            | How it is sampled and what is logged, for every engine.                        |
| Engine block            | Sampling settings of a single engine (e.g. operators).                         |
| Command line            | What leaves the result statistically equivalent (e.g. seed, run name).         |


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
  - *stateFile*: optional path of the file storing the final state. If not given, *&lt;run-name&gt;.state.xml* in the working folder is used. BEAST X does not support it.
- *screenLogger*, *fileLogger*: always log the posterior, prior and likelihood.
  - *parameters*: optional list of the logged parameters, which can have different types. It is not type-checked, and the engine rejects values it cannot log. If not given, all assigned parameters including inline assignments and tree statistics are logged.
  - *parameterNames*: optional list of names used in the log instead of the variable names. Must have the same length as *parameters*.
- *fileLogger*, *treeLogger*: *file* is optional. It defaults to *&lt;run-name&gt;.log* or *&lt;run-name&gt;.trees* in the working folder. Relative paths use the working folder, and absolute paths can write elsewhere.
- *treeLogger*: logs a single tree. tree is needed.
- *logEvery*: defaults to 1000 for every logger.
- *No loggers*: a missing kind of logger is added with its defaults. This gives a screen logger, a file logger writing to *&lt;run-name&gt;.log* and, if a tree is sampled, a tree logger writing to *&lt;run-name&gt;.trees*.

The MCMC block is type-checked like the rest of the model. *Sampler* and *Logger* are core types, and *mcmc*, *screenLogger*, *fileLogger* and *treeLogger* are core components.

- The type checker treats its statements like any other statement: every argument must be known and have the right type. Only the logged *parameters* are exempt.
- The sampler and the loggers are collected in specs shared by all engines. The specs only check what types cannot express: *file* is not blank, and *parameterNames* has the same length as *parameters*.

Everything else (e.g. operator settings) should be in a specific engine block (e.g. "beastX").

## Command Line Arguments

| Command                                             | Purpose                                                                        |
| --------------------------------------------------- | ------------------------------------------------------------------------------ |
| *check &lt;model.phylospec&gt;*                     | Check syntax, types and engine mappings.                                       |
| *export &lt;model.phylospec&gt; --xml &lt;file&gt;* | Write the analysis XML to the given file without running it. BEAST 2 / BEAST X specific. |
| *run &lt;model.phylospec&gt;*                       | Check and run the model.                                                       |



| Option                | Commands           | Purpose                                             |
| --------------------- | ------------------ | --------------------------------------------------- |
| *--engine &lt;id&gt;* | check, export, run | Engine to use: beast3 (default) or beastx.          |
| *--run-name &lt;name&gt;* | run                | Name of the output files and the run record. Defaults to the name of the model file. |
| *--seed &lt;n&gt;*    | export, run        | Random seed. If not given, *run* chooses one uniformly and *export* sets none. |
| *--overwrite*         | run                | Replace existing output files and the record of an earlier run with the same name. Without it, existing outputs are never replaced. |
| *--library &lt;ids&gt;* | check, export, run | Tile libraries to use, in order of preference.    |
| *--adapter-path &lt;dir&gt;* | check, export, run | Directory of an unpacked adapter to load. Can be repeated. |
| *--debug*             | all                | Show raw engine output and stack traces.            |
| *-h*, *--help*        |                    | Show the help.                                      |
| *--version*           |                    | Show the versions.                                  |


*Outputs*: logs, trees and state files are written to the working folder, with the run record next to them in *&lt;name&gt;.run/*. See [Output Files](output-files.md).

*Replicates*: run the same model multiple times with different *--seed* values, and keep the runs apart in one of two ways.

- Give each replicate its own *--run-name* in the same working folder. Every default output and the run record carry the run name, so nothing collides. An output with a *file* set in the model keeps that name for every run name, so the second replicate is refused. Omit *file* to use this approach.
- Run each replicate from its own working folder. Relative output paths use the working folder, so this also separates outputs with a *file* set in the model. Input paths still use the folder of the model file.

There is no option naming the output folder. The run name is a plain name without a path, and *--out* is refused with a hint to use one of the two ways above.
