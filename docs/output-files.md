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

*file* and *stateFile* can be relative to the working directory or absolute. Input files are always relative to the script.

## Rules

- Existing outputs are never overwritten. All destinations are checked before the MCMC starts.
- *&lt;name&gt;.run/* reserves the run name. A second run with the same name is refused.
- A failed run that wrote no outputs does not reserve its name, so it can simply be run again.
- *run.json* stores paths relative to its own folder, so results can be moved together. Files outside the working directory keep their absolute path.

## Other commands

- *check* writes nothing.
- *export* writes only the file given by *--xml*. Default log names use the stem of that file and are relative to where the XML is later run.
