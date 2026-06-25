# LocalGemma — A CLI for running local GGUF models with an OpenAI-compatible API

LocalGemma is a lightweight Kotlin/JVM command-line tool that lets you run GGUF models locally using [llama.cpp](https://github.com/ggerganov/llama.cpp) and exposes an OpenAI-compatible HTTP API. Think of it as a minimal, hackable alternative to Ollama.

## Model Format Note

LocalGemma uses **GGUF** weights, not the Android-only `.task` (LiteRT / MediaPipe GenAI) format.

- **LiteRT `.task` files** are built for Android runtimes (`com.google.ai.edge.litertlm`). There is no desktop or Termux CLI runtime for them.
- **GGUF** is the industry-standard cross-platform format supported by llama.cpp, Ollama, LM Studio, and many other tools.
- To make discovery easier, LocalGemma uses **LiteRT-style model names** in its built-in allowlist (e.g., `Gemma3-1B-IT-q4`), but downloads the equivalent GGUF conversion from HuggingFace.

## Prerequisites

- **JDK 17+**
- **CMake 3.16+**
- **C++17 compiler** (GCC, Clang, or MSVC)
- **Git**

## Installation

### Quick install

```bash
git clone --recursive https://github.com/Ravishka17/Local-Gemma.git
cd Local-Gemma
./scripts/install.sh
```

### Manual install

```bash
git clone --recursive https://github.com/Ravishka17/Local-Gemma.git
cd Local-Gemma
cmake -S . -B build/cmake -DCMAKE_BUILD_TYPE=Release
cmake --build build/cmake --parallel
./gradlew installDist
ln -s "$(pwd)/build/install/localgemma/bin/localgemma" /usr/local/bin/localgemma
```

## Quick Start

```bash
# Pull a model from the built-in allowlist
localgemma pull Gemma3-1B-IT-q4

# Chat interactively
localgemma run Gemma3-1B-IT-q4

# Start the OpenAI-compatible server
localgemma serve --model Gemma3-1B-IT-q4
```

## API

LocalGemma exposes the following endpoints:

- `GET /v1/models`
- `GET /v1/models/{modelId}`
- `POST /v1/chat/completions`
- `POST /v1/completions`

### Example: non-streaming chat

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Gemma3-1B-IT-q4",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

### Example: streaming chat

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "Gemma3-1B-IT-q4",
    "messages": [{"role": "user", "content": "Hello!"}],
    "stream": true
  }'
```

## Commands

| Command | Description |
|---------|-------------|
| `serve` | Start the OpenAI-compatible API server |
| `run <model>` | Run an interactive chat session |
| `pull <model>` | Download a model from the allowlist or a HuggingFace URL |
| `list` | List installed models |
| `rm <model>` | Remove an installed model |
| `ps` | Show installed model status |

## Supported Models

Built-in models are defined in `src/main/resources/model_allowlist.json`. Each entry uses a **LiteRT-compatible alias** (e.g., `Gemma3-1B-IT-q4`) that maps to the corresponding GGUF repo on HuggingFace. You can also pull arbitrary GGUF files directly:

```bash
localgemma pull https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf
```

## Configuration

LocalGemma stores its data in `~/.localgemma/`:

- `config.json` — server defaults, HuggingFace token, etc.
- `models/` — downloaded GGUF files
- `models.json` — installed model registry

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.
