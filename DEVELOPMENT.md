# Development

## Prerequisites

- JDK 17+
- CMake 3.16+
- C++17 compiler (GCC, Clang, or MSVC)
- Git

## Clone

```bash
git clone --recursive https://github.com/techjarves/localgemma.git
cd localgemma
```

If you already cloned without `--recursive`, initialize the submodule:

```bash
git submodule update --init --recursive
```

The llama.cpp submodule is pinned to tag `b9704`.

## Build the native library

```bash
cmake -S . -B build/cmake -DCMAKE_BUILD_TYPE=Release
cmake --build build/cmake --parallel
```

This produces a shared library (`liblocalgemma.so`, `liblocalgemma.dylib`, or `localgemma.dll`) in `build/cmake/`.

## Build and run the Kotlin app

```bash
# Run directly from Gradle
./gradlew run --args="serve"

# Or build a distribution
./gradlew installDist
./build/install/localgemma/bin/localgemma serve --model gemma-2b-it-q4
```

The Gradle `processResources` task depends on the CMake build, so the native library is automatically copied into `build/resources/main/native/` and bundled in the fat JAR.

## Project structure

```
src/main/kotlin/com/localgemma/
  cli/LocalGemma.kt          # Clikt CLI entry point
  api/OpenAiServer.kt        # Ktor OpenAI-compatible server
  api/OpenAiModels.kt        # Request/response DTOs
  inference/
    InferenceEngine.kt       # Engine interface
    LlamaCppEngine.kt        # llama.cpp wrapper
    LlamaCppNative.kt        # JNI declarations
    GenerationParams.kt      # Sampling parameters
  model/
    Model.kt                 # Data classes
    ModelRegistry.kt         # Installed model persistence
    ModelAllowlist.kt        # Built-in model catalog
    ModelDownloader.kt       # HuggingFace download logic
  config/
    ConfigManager.kt         # ~/.localgemma/config.json
    AppConfig.kt             # Config data class

src/main/cpp/
  llama_jni.cpp              # JNI bridge to llama.cpp C API

vendor/llama.cpp/            # Git submodule
```

## Running tests

```bash
./gradlew test
```

## Manual native library placement

If you prefer not to run CMake through Gradle, you can build manually and place the shared library in `src/main/resources/native/`. The runtime extraction logic in `LlamaCppNative.loadNativeLibrary()` will find it there.

## Windows notes

On Windows, use `gradlew.bat` instead of `./gradlew`. The install script skips the symlink step on Windows and advises adding `build/install/localgemma/bin` to `%PATH%`.
