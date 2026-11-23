# Output Files

A run writes its results to the **working directory**, named after the **run name**. Its record sits next to them in one folder.

```
hky-yule.log
hky-yule.trees
hky-yule.state.xml
hky-yule.run/
    run.json
    console.log
    model.phylospec
    analysis.xml
```

## Names

The run name is the script name without its extension, or the value of *--run-name*. It is a plain name and cannot contain a path. To write somewhere else, change the working directory.

| File                         | Default name                                                     | Set with                    |
|------------------------------|------------------------------------------------------------------|-----------------------------|
| Trace log                    | *&lt;name&gt;.log*                                               | *file* of *fileLogger*      |
| Trees                        | *&lt;name&gt;.trees*, or *&lt;name&gt;-&lt;tree&gt;.trees* for several default trees | *file* of *treeLogger* |
| Final state (BEAST 3 only)   | *&lt;name&gt;.state.xml*                                         | *stateFile* of *mcmc*       |
| Run record                   | *&lt;name&gt;.run/*                                              | always follows the run name |

Input files are always relative to the script.

## Paths in the model

*file* and *stateFile* can be a file name or a path. Relative paths use the working directory, not the folder of the script. For a run in */work* named *hky*:

| In the model                 | Written to            | Path in *run.json*   |
|------------------------------|-----------------------|----------------------|
| *file="trace.log"*           | */work/trace.log*     | *../trace.log*       |
| *file="logs/trace.log"*      | */work/logs/trace.log* | *../logs/trace.log* |
| *file="../trace.log"*        | */trace.log*          | */trace.log*         |
| *file="/data/trace.log"*     | */data/trace.log*     | */data/trace.log*    |

- Missing folders are created.
- A name set in the model does not change with the run name. Running the model again with another *--run-name* is refused because the file already exists. Omit *file* to get a new file for each run name.
- Only the named file moves. All other outputs keep their default names, and the record stays in *hky.run/* in the working directory.
- A run is refused if two outputs use the same file, if an output is also used as a folder, if a parent of the path is a file, or if the path passes through a symbolic link inside the working directory.
- BEAST file name templates such as *$(tree)* are not supported.

## Rules

- Existing outputs are never overwritten. All destinations are checked before the MCMC starts.
- *&lt;name&gt;.run/* reserves the run name. A second run with the same name is refused.
- A failed run that wrote no outputs does not reserve its name, so it can simply be run again.
- *run.json* stores paths relative to its own folder, so results can be moved together. Files outside the working directory keep their absolute path.

## Other commands

- *check* writes nothing.
- *export* writes only the file given by *--xml*. Default log names use the stem of that file and are relative to where the XML is later run.
