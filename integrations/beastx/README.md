# PhyloSpec BEAST X Integration

PhyloSpec parser and runner for [BEAST X](https://github.com/beast-dev/beast-mcmc).

## Setup

BEAST X is not published to a public Maven repository, so its jar must be
installed into your local Maven repository before you can build this module.

From the repository root:

```sh
./integrations/beastx/install-beast-mcmc.sh
```

The script downloads the official BEAST X release tgz from GitHub, extracts the
bundled fat jar (`beast.jar`, ~12 MB, includes JEBL, JAM, JDOM, commons-math,
colt, BEAGLE, EJML, MTJ), and runs `mvn install:install-file` to register it as
`dr:beast-mcmc:<version>` in `~/.m2/repository`.

The pinned version lives at the top of the script (`BEAST_MCMC_VERSION`). When
it bumps, every developer reruns the script.

### Requirements

- `bash`, `curl`, `tar` (Mac/Linux: built in; Windows: WSL or Git Bash)
- `mvn` on `PATH`

## Build

Once BEAST X is installed locally:

```sh
mvn -pl integrations/beastx/java -am compile
```
