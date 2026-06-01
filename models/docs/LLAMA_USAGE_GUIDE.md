# Running a Model with Ollama

This guide explains how to run a model locally using **Ollama**.

## Step 1: Install Ollama

### Windows
1. Download from: https://ollama.ai
2. Run the installer
3. Restart your computer

### Verify Installation
```bash
ollama --version
```

## Step 2: Convert Model to GGUF Format

Ollama requires GGUF format. Use `llama.cpp` conversion tools:

### Clone llama.cpp
```bash
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
```

### Convert PyTorch to GGUF

```bash
# Run Python conversion script
python convert_hf_to_gguf.py "<path to the merged model>" --outfile "<path to the output.gguf>"
```

## Step 3: Create Modelfile

Create `Modelfile` with content:

```
FROM ./output.gguf
```

## Step 4: Load Model into Ollama

```bash
ollama create <model_name> -f Modelfile
```

## Step 5: Integrate with Backend

Set in `application.yml`:

```yaml
ai:
  provider: ollama
  model: <model_name>
  model-tag: <model_name_tag>
  ollama:
    host: http://localhost:11434
```
