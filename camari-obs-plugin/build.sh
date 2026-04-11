#!/usr/bin/env bash
# Local build script for camari-obs-plugin (Linux / macOS)
#
# Usage:
#   ./build.sh              # Release build
#   ./build.sh --debug      # Debug build
#   ./build.sh --test       # Build and run unit tests
#   ./build.sh --clean      # Delete build directory first
#   ./build.sh --clean --test --debug   # Combine as needed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
BUILD_TYPE="Release"
RUN_TESTS=0
CLEAN=0

for arg in "$@"; do
  case "$arg" in
    --debug) BUILD_TYPE="Debug" ;;
    --test)  RUN_TESTS=1 ;;
    --clean) CLEAN=1 ;;
    *)
      echo "Unknown argument: $arg"
      echo "Usage: $0 [--debug] [--test] [--clean]"
      exit 1
      ;;
  esac
done

if [ "$CLEAN" = "1" ]; then
  echo "Cleaning $BUILD_DIR..."
  rm -rf "$BUILD_DIR"
fi

EXTRA_FLAGS=()
if [ "$RUN_TESTS" = "1" ]; then
  EXTRA_FLAGS+=("-DBUILD_TESTING=ON")
fi

echo "Configuring ($BUILD_TYPE)..."
cmake -B "$BUILD_DIR" -S "$SCRIPT_DIR" \
  -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
  "${EXTRA_FLAGS[@]}"

echo "Building..."
cmake --build "$BUILD_DIR" --parallel "$(nproc 2>/dev/null || sysctl -n hw.logicalcpu)"

if [ "$RUN_TESTS" = "1" ]; then
  echo "Running tests..."
  ctest --test-dir "$BUILD_DIR" --output-on-failure
fi

echo "Done. Plugin: $BUILD_DIR/camari-obs-plugin$(uname | grep -q Darwin && echo '.dylib' || echo '.so')"
