# Running the Fine-tuned Dutch Tutor Model with Ollama

**Model:** Fine-tuned Llama 3.1 8B for Dutch A1 tutoring  
**Location:** `models/llama-3.1-8b-dutch-tutor/`

This guide explains how to run the trained model locally using **Ollama** — a simple way to run large language models on your computer.

## Step 1: Install Ollama

### Windows
1. Download from: https://ollama.ai
2. Run the installer
3. Restart your computer

### Verify Installation
```bash
ollama --version
```

## Step 2: Merge LoRA Adapter with Base Model

The trained model is stored as a **LoRA adapter** (small patches), not a full model. You need to merge it with the base Llama model.

Navigate to the scripts directory and run:

```bash
cd models/llama-3.1-8b-dutch-tutor-scripts
python merge_model.py
```

This merges the fine-tuned adapter with the base model and saves it to `models/llama-3.1-8b-dutch-tutor-merged/`.

**Time:** ~5 minutes | **Output size:** ~16 GB

## Step 3: Convert Merged Model to GGUF Format

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

This creates `.gguf` (~8 GB, the optimized format Ollama uses).

**Time:** ~10-15 minutes

## Step 4: Create Modelfile

Create `Modelfile` in `models/llama-3.1-8b-dutch-tutor-gguf/` with content:

```
FROM ./model.gguf
```

## Step 5: Load Model into Ollama

```bash
cd models/llama-3.1-8b-dutch-tutor-gguf
ollama create dutch-tutor -f Modelfile
```

Verify it was created:
```bash
ollama list
```

You should see `dutch-tutor` in the list.

## Step 6: Integrate with Backend

Set in `application.yml`:

```yaml
ai:
  provider: ollama
  model: dutch-tutor
  model-tag: dutch-tutor
  ollama:
    host: http://localhost:11434
  temperature: 0.4
  max-tokens: 320
```
