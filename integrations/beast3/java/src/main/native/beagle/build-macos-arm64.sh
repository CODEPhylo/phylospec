#!/bin/sh
# Build-time only: users receive these libraries inside the CLI ZIP.
set -eu

if [ "$(uname -s)" != Darwin ] || [ "$(uname -m)" != arm64 ]; then
    echo "The BEAGLE bundle must be built on macOS Apple Silicon with an arm64 JDK." >&2
    exit 1
fi
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <build-directory>" >&2
    exit 1
fi
for tool in cmake curl tar shasum patch xcrun otool lipo codesign; do
    command -v "$tool" >/dev/null || { echo "Missing build tool: $tool" >&2; exit 1; }
done

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
mkdir -p "$1"
work_dir=$(CDPATH= cd -- "$1" && pwd)
revision=bf35ad4a28e5f03edaa10dfd77c1b54eee0d9595
source_sha256=793916bf22955b6ae64a54e3c4884e2ec017edfc7e73c223c4b659d6408c8b25
source_url="https://codeload.github.com/beagle-dev/beagle-lib/tar.gz/$revision"
archive="$work_dir/downloads/beagle-$revision.tar.gz"
source_dir="$work_dir/source"
build_dir="$work_dir/build"
bundle_dir="$work_dir/macos-arm64"
mkdir -p "$work_dir/downloads"
if [ ! -f "$archive" ]; then
    curl --fail --location --retry 2 "$source_url" -o "$archive.part"
    mv "$archive.part" "$archive"
fi
actual_sha256=$(shasum -a 256 "$archive" | awk '{print $1}')
if [ "$actual_sha256" != "$source_sha256" ]; then
    echo "BEAGLE source checksum mismatch: $archive" >&2
    exit 1
fi

# Always rebuild from the checked source; never copy a developer's installed libraries.
rm -rf "$source_dir" "$build_dir" "$bundle_dir"
mkdir -p "$source_dir" "$bundle_dir"
tar -xzf "$archive" --strip-components=1 -C "$source_dir"
patch --batch -d "$source_dir" -p1 < "$script_dir/macos-arm64.patch"
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home)}
export JAVA_HOME
cmake -S "$source_dir" -B "$build_dir" \
    -DCMAKE_C_COMPILER="$(xcrun --find clang)" \
    -DCMAKE_CXX_COMPILER="$(xcrun --find clang++)" \
    -DCMAKE_OSX_ARCHITECTURES=arm64 \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0 \
    -DCMAKE_INSTALL_NAME_DIR=@loader_path \
    -DCMAKE_BUILD_WITH_INSTALL_NAME_DIR=ON \
    -DCMAKE_BUILD_WITH_INSTALL_RPATH=ON \
    -DBUILD_JNI=ON -DBUILD_SSE=OFF -DBUILD_OPENCL=OFF -DBUILD_CUDA=OFF
cmake --build "$build_dir" --target hmsbeagle hmsbeagle-jni hmsbeagle-cpu --parallel 4

cp "$build_dir/libhmsbeagle/libhmsbeagle.1.dylib" "$bundle_dir/"
cp "$build_dir/libhmsbeagle/JNI/libhmsbeagle-jni.jnilib" "$bundle_dir/"
cp "$build_dir/libhmsbeagle/CPU/libhmsbeagle-cpu.40.so" "$bundle_dir/"
for library in "$bundle_dir"/*; do
    if [ "$(lipo -archs "$library")" != arm64 ]; then
        echo "Unexpected native architecture: $library" >&2
        exit 1
    fi
    # Reject any accidental dependency on Homebrew, a JDK, or this build directory.
    otool -L "$library" | awk 'NR > 1 {print $1}' | while IFS= read -r dependency; do
        case "$dependency" in
            @loader_path/*|/usr/lib/*|/System/Library/*) ;;
            *) echo "Nonportable native dependency: $dependency" >&2; exit 1 ;;
        esac
    done
    codesign --verify --strict "$library"
done

cp "$source_dir/LICENSE" "$bundle_dir/BEAGLE-LICENSE.txt"
cp "$script_dir/macos-arm64.patch" "$bundle_dir/"
cat > "$bundle_dir/beagle-build.properties" <<EOF
version=4.0.1
revision=$revision
source.url=$source_url
source.sha256=$source_sha256
platform=macos-arm64
macos.deployment.target=11.0
plugins=CPU
patch=macos-arm64.patch
EOF
cat > "$bundle_dir/NOTICE.txt" <<EOF
BEAGLE 4.0.1, Copyright (c) 2021 Phylogenetic Likelihood Working Group.
Distributed under the included MIT license.
Source: $source_url
The included patch only changes macOS architecture, compiler portability,
and library/plugin lookup paths. No likelihood algorithms are changed.
The bundle contains only the JNI bridge, core library and CPU plugin.
macOS 11.0 is the compilation target, not a claim of acceptance on every release.
Build recipe: integrations/beast3/java/src/main/native/beagle/build-macos-arm64.sh
in the matching PhyloSpec source release.
EOF
echo "Built self-contained BEAGLE CPU libraries: $bundle_dir"
