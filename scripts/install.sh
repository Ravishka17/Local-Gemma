#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo "Error: $1 is required but not installed." >&2
        exit 1
    fi
}

check_java_version() {
    local version
    version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    local major
    major=$(echo "$version" | awk -F '.' '{print $1}')
    if [ "$major" -lt 17 ]; then
        echo "Error: Java 17+ is required (found $version)." >&2
        exit 1
    fi
}

echo "Checking prerequisites..."
check_command git
check_command cmake
check_command java
check_java_version

cd "$PROJECT_DIR"

if [ ! -d "vendor/llama.cpp/.git" ]; then
    echo "Initializing llama.cpp submodule..."
    git submodule update --init --recursive
fi

echo "Building native library..."
cmake -S . -B build/cmake -DCMAKE_BUILD_TYPE=Release
cmake --build build/cmake --parallel

echo "Building Kotlin application..."
./gradlew installDist

BIN_PATH="$PROJECT_DIR/build/install/localgemma/bin/localgemma"

if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    echo ""
    echo "Build complete!"
    echo "Add the following directory to your PATH:"
    echo "  $PROJECT_DIR/build/install/localgemma/bin"
    echo ""
    echo "Then run: localgemma --help"
else
    if [ -w "/usr/local/bin" ]; then
        ln -sf "$BIN_PATH" /usr/local/bin/localgemma
        echo ""
        echo "Installation complete! LocalGemma is now available as 'localgemma'."
    else
        echo ""
        echo "Build complete! To install system-wide, run:"
        echo "  sudo ln -sf $BIN_PATH /usr/local/bin/localgemma"
        echo ""
        echo "Or add the following to your PATH:"
        echo "  $PROJECT_DIR/build/install/localgemma/bin"
    fi
    echo ""
    echo "Get started:"
    echo "  localgemma pull Gemma3-1B-IT-q4"
    echo "  localgemma run Gemma3-1B-IT-q4"
fi
