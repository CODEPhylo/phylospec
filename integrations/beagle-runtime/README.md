# BEAGLE runtime integration

This engine-independent module discovers and reports an existing native BEAGLE
installation. BEAST X currently consumes it directly; the future common
launcher will use the same API for BEAST 3. The module deliberately does not
bundle, download, or install platform-specific native libraries. It never
invokes a package manager and never writes to system library directories.

## Structure

- Platform and configuration: `BeaglePlatform`, `BeagleRuntimeConfig`
  (`Location`, `Source`)
- Discovery: `BeagleRuntimeLocator` (`Probe`, `Result`) and
  `BeagleRuntimeInstallation`
- Native verification: `BeagleRuntimeVerifier` (`Result`, `Status`, `Resource`)
- Isolated preflight: `BeaglePreflightProcess` (`Request`, `Result`, `Status`) and
  `BeaglePreflightMain`
- User-facing output: `BeagleRuntimeReport`, `BeagleRuntimeDiagnostics`

Discovery never loads native code. Verification uses the engine-provided Java
API, while reports only present structured results. The diagnostics entry point
performs discovery by default and invokes isolated verification only when
`--preflight` is requested.

The search order is:

1. `-Dphylospec.beagle.home=/path/to/beagle`
2. `PHYLOSPEC_BEAGLE_HOME`
3. `BEAGLE_LIB` (compatible with the LPhyBEAST launcher convention)
4. directories already present in `java.library.path`
5. common operating-system locations such as `/opt/homebrew/lib` and
   `/usr/local/lib`

Each configured location may be either the installation root or its native
library directory. The locator checks the configured directory itself, its
`lib` child, and the `bin` child on Windows.

Discovery verifies file presence only; it does not claim that the JNI library
or its plugins can be loaded. It also does not change an already-running JVM's
native library path.

Engine selection is deliberately outside this module. The PhyloSpec execution
layer selects an engine and supplies its Java executable, classpath, JVM
arguments, environment, and timeout as a `BeaglePreflightProcess.Request`.
The BEAGLE module only augments that launch context with the discovered native
library and plugin paths; it contains no BEAST 3/BEAST X switch.

The module also provides `BeagleRuntimeVerifier`. It uses the BEAGLE Java API
already supplied by the selected engine to perform a real native load check,
read the BEAGLE version, and enumerate resources. The shared runtime does not
add another BEAGLE Java dependency to the engine classpath. BEAST X invokes
this verifier before materializing a native likelihood.

`BeaglePreflightProcess` runs verification in a disposable child JVM. Its
explicit `Request` makes the preflight use the same Java executable, classpath,
JVM arguments, environment, and timeout that an engine launch will use. It
prepends the discovered library directory to `java.library.path`, preserves
other library-path entries, prepends the discovered directory to an existing
`BEAGLE_PLUGIN_PATH` while preserving and de-duplicating its entries, captures
bounded output, and distinguishes an unavailable runtime from a native crash
or timeout. These changes apply only to the child process environment; the
parent process and operating system configuration are not modified.
`Request.fromCurrentJvm` is a convenience for diagnostics, not an
engine-selection mechanism.

The future PhyloSpec execution layer should reuse
`Request.resolvedJvmArguments()` and `Request.resolvedEnvironment()` when it
starts the selected engine. This keeps preflight and real execution consistent
without teaching the BEAGLE module about engines.

Run the diagnostics entry point with:

```shell
mvn -f integrations/beagle-runtime/java/pom.xml compile \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.phylospec.beagle.BeagleRuntimeDiagnostics
```

Pass `--preflight` when the diagnostics class is running on an engine classpath
that provides `beagle.BeagleFactory` to perform the isolated native check.
