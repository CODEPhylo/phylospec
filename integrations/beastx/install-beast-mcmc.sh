#!/usr/bin/env bash
# Downloads the BEAST X release and installs the bundled beast.jar into the
# local Maven repository as dr:beast-mcmc:<version>. Run once after cloning
# (or whenever BEAST_MCMC_VERSION bumps).

set -euo pipefail

BEAST_MCMC_VERSION="10.5.0"
URL="https://github.com/beast-dev/beast-mcmc/releases/download/v${BEAST_MCMC_VERSION}/BEAST_X_v${BEAST_MCMC_VERSION}.tgz"
JAR_IN_TGZ="BEASTv${BEAST_MCMC_VERSION}/lib/beast.jar"

WORK_DIR=$(mktemp -d)
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Downloading BEAST X v${BEAST_MCMC_VERSION}..."
curl -fsSL -o "$WORK_DIR/beastx.tgz" "$URL"

echo "Extracting beast.jar..."
tar -xzf "$WORK_DIR/beastx.tgz" -C "$WORK_DIR" "$JAR_IN_TGZ"

echo "Installing dr:beast-mcmc:${BEAST_MCMC_VERSION} to local Maven repository..."
mvn install:install-file \
  -Dfile="$WORK_DIR/$JAR_IN_TGZ" \
  -DgroupId=dr \
  -DartifactId=beast-mcmc \
  -Dversion="${BEAST_MCMC_VERSION}" \
  -Dpackaging=jar

echo
echo "Done. dr:beast-mcmc:${BEAST_MCMC_VERSION} is now available locally."
