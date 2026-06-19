# LocalGemma — A CLI for running local GGUF models with an OpenAI-compatible API

LocalGemma is a lightweight Kotlin/JVM command-line tool that lets you run GGUF models locally using [llama.cpp](https://github.com/ggerganov/llama.cpp) and exposes an OpenAI-compatible HTTP API. Think of it as a minimal, hackable alternative to Ollama.

## Prerequisites

- **JDK 17+**
- **CMake 3.16+**
- **C++17 compiler** (GCC, Clang, or MSVC)
- **Git**

## Installation

### Quick install

```bash
git clone --recursive https://github.com/techjarves/localgemma.git
cd localgemma
./scripts/install.sh
```

### Manual install

```bash
git clone --recursive https://github.com/techjarves/localgemma.git
cd localgemma
cmake -S . -B build/cmake -DCMAKE_BUILD_TYPE=Release
cmake --build build/cmake --parallel
./gradlew installDist
ln -s "$(pwd)/build/install/localgemma/bin/localgemma" /usr/local/bin/localgemma
```

## Quick Start

```bash
# Pull a model from the built-in allowlist
localgemma pull gemma-2b-it-q4

# Chat interactively
localgemma run gemma-2b-it-q4

# Start the OpenAI-compatible server
localgemma serve --model gemma-2b-it-q4
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
    "model": "gemma-2b-it-q4",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

### Example: streaming chat

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-2b-it-q4",
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

Built-in models are defined in `src/main/resources/model_allowlist.json`. You can also pull arbitrary GGUF files from HuggingFace:

```bash
localgemma pull https://huggingface.co/bartowski/gemma-2b-it-GGUF/resolve/main/gemma-2b-it-Q4_K_M.gguf
```

## Configuration

LocalGemma stores its data in `~/.localgemma/`:

- `config.json` — server defaults, HuggingFace token, etc.
- `models/` — downloaded GGUF files
- `models.json` — installed model registry

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.
