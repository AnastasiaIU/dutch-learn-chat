# Llama 3.1 8B Fine-tuning for Dutch Language Tutoring
## A Complete Learning Guide

**Project:** Dutch Learn Chat  
**Model:** Meta-Llama-3.1-8B-Instruct  
**Training Dataset:** CEFR Dutch Language Tutoring Dataset

---

## Table of Contents
1. [Overview](#overview)
2. [What I Did](#what-i-did)
3. [Key Technologies](#key-technologies)
4. [Step-by-Step Process](#step-by-step-process)
5. [Technical Deep Dive](#technical-deep-dive)
6. [Challenges & Solutions](#challenges--solutions)
7. [Results & Next Steps](#results--next-steps)

---

## Overview

This guide documents the process of fine-tuning the **Meta-Llama-3.1-8B-Instruct** model locally on a **CEFR Dutch Language Tutoring Dataset** to create a specialized language tutor for Dutch learners at A1/A2/B1 levels.

### Why Fine-tuning?
- **Base Model Limitation:** Out-of-the-box Llama 3.1 has limited instruction-following for domain-specific tasks (Dutch tutoring)
- **Customization:** Fine-tuning adapts the model to understand and follow system prompts for language instruction
- **Cost-Efficiency:** LoRA (Parameter-Efficient Fine-Tuning) reduces trainable parameters from 8B to ~60M, making CPU/consumer GPU training feasible
- **Performance:** A fine-tuned model generates better Dutch language corrections and explanations aligned with CEFR levels

---

## What I Did

### 1. **Analyzed the Training Dataset**
   - **File:** `backend/src/main/resources/datasets/cefr_training_dataset.json`
   - **Size:** ~300 examples
   - **Structure:** Chat format with `system`, `user`, and `assistant` fields
   - **Distribution:** Balanced across A1, A2, B1 CEFR levels and task types (correction, word_explanation, conversation)

### 2. **Created a Training Pipeline Script** (`train_llama.py`)
   - Loads the CEFR dataset from JSON
   - Configures the Llama 3.1 model with LoRA for efficient fine-tuning
   - Preprocesses data into chat format using Llama's special tokens
   - Trains with optimized hyperparameters for CPU
   - Tests inference with sample Dutch prompts

### 3. **Installed Required Dependencies**
   ```
   peft           (0.19.1)   - Low-Rank Adaptation for efficient tuning
   bitsandbytes   (0.49.2)   - 8-bit quantization (fallback for GPU)
   accelerate     (1.13.0)   - Multi-GPU support & performance
   transformers   (5.8.0)    - Hugging Face model library
   torch          (2.11.0)   - Deep learning framework
   datasets       (4.8.5)    - Dataset handling
   ```

### 4. **Executed the Training Pipeline**
   - Started CPU-based training on the full Llama 3.1 8B model
   - Used LoRA to reduce trainable parameters
   - Configured for CPU compatibility (no quantization on CPU)

---

## Key Technologies

### **1. Llama 3.1 8B Model**
- **Model ID:** `unsloth/Meta-Llama-3.1-8B-Instruct-bnb-4bit`
- **Parameters:** 8 billion
- **Architecture:** Transformer-based autoregressive LLM
- **Training:** Already instruction-tuned by Meta
- **Why This Model:**
  - Strong Dutch language understanding
  - Lightweight compared to larger models (e.g., 70B)
  - Good instruction-following capability
  - Fits on my hardware with LoRA

### **2. LoRA (Low-Rank Adaptation)**
```
Traditional Fine-tuning: Update all 8B parameters
LoRA Fine-tuning: Add ~60M trainable parameters + frozen base model

Benefit: 99% fewer parameters to train = 10x faster training
Trade-off: Slightly lower performance, but good for domain-specific tasks
```

**How LoRA Works:**
- Adds small specialized "upgrade patches" to the model's attention layers (the parts that help the model focus on important words)
- Targets two specific focus mechanisms: `q_proj` (what the model looks for) and `v_proj` (what information to extract)
- Configuration:
  - `r=8`: How powerful/flexible the patches are (higher = smarter but slower; 8 is balanced)
  - `lora_alpha=16`: How much the patches influence the model (think of it as "volume control")
  - `lora_dropout=0.05`: Prevents the model from memorizing training data too much (5% safety margin during training)

### **3. HuggingFace Transformers Framework**
- **Tokenizer:** Converts text → tokens the model understands
- **Model:** Loads pre-trained weights
- **Trainer:** Handles training loop, gradient updates, checkpointing
- **Datasets:** Efficient data loading and preprocessing

### **4. Pydantic & Accelerate**
- **Accelerate:** Handles device placement (CPU/GPU) and optimization
- **Pydantic:** Data validation (though not heavily used in this pipeline)

---

## Step-by-Step Process

### **Phase 1: Dataset Preparation**

**File:** `cefr_training_dataset.json`

**Structure:**
```json
[
  {
    "level": "A1",
    "type": "correction",
    "system": "You are a Dutch language tutor for absolute beginners...",
    "user": "Corrigeer deze zin: Ik gaat naar school",
    "assistant": "Correct: Ik ga naar school. (Correct verb form)"
  },
  ...
]
```

**Processing Steps in Script:**
1. Load JSON file
2. Extract `system`, `user`, `assistant` fields
3. Group by CEFR level for analysis
4. **Limit to 100 examples for CPU** (to reduce training time from hours to minutes)
5. Create HuggingFace `Dataset` object

**Why the Format Matters:**
- **System prompt:** Tells the model its role (e.g., "tutor for A1 level")
- **User query:** The learner's request in Dutch
- **Assistant response:** The model's expected answer

### **Phase 2: Data Tokenization**

**Llama 3.1 Chat Format:**
```
<|start_header_id|>system<|end_header_id|>

You are a Dutch tutor...
<|eot_id|><|start_header_id|>user<|end_header_id|>

Hoe zeg ik hello?
<|eot_id|><|start_header_id|>assistant<|end_header_id|>

Je zegt "hallo"!
<|eot_id|>
```

**Tokenization Process:**
1. Convert text to token IDs (integers the model understands)
2. Pad sequences to fixed length (256 tokens for CPU efficiency)
3. Create attention masks (tells model which tokens are real vs. padding)
4. Create labels (same as input, used for loss calculation)
5. Mask padding tokens in labels (loss = 0 for padding)

**Why Token Masking?**
```
Without masking: Model trains on padding tokens (wastes computation)
With masking:    Model only learns from real data (efficient)
Loss formula:    ignore_index=-100 (labels set to -100 are skipped)
```

### **Phase 3: Model Loading**

**CPU-Compatible Configuration:**
```python
model = AutoModelForCausalLM.from_pretrained(
    model_id,
    device_map="cpu",
    torch_dtype=torch.float32,        # Full precision on CPU
    low_cpu_mem_usage=True,            # Memory-efficient loading
)
```

**Why These Settings?**
- `device_map="cpu"`: Explicit CPU placement
- `torch_dtype=torch.float32`: Full precision (8-bit quantization requires GPU)
- `low_cpu_mem_usage=True`: Loads model in chunks to avoid OOM

### **Phase 4: LoRA Configuration**

```python
lora_config = LoraConfig(
    r=8,                           # Low-rank dimension
    lora_alpha=16,                 # Scaling factor
    target_modules=["q_proj", "v_proj"],  # Which attention projections
    lora_dropout=0.05,             # Dropout for regularization
    bias="none",                   # Don't add bias terms
    task_type="CAUSAL_LM",         # Causal language modeling task
)
```

**LoRA Mathematics:**
```
Standard weight: W (d_in × d_out)
LoRA Update:     W → W + BA (d_in × r) × (r × d_out)
Trainable params: Only B and A matrices
```

**Applied to Q and V Projections:**
- Q (query) and V (value) matrices in multi-head attention
- These contain most model knowledge for instruction-following
- Training Q and V adapts model's understanding of queries and values

### **Phase 5: Training Configuration**

**Hyperparameters (CPU-Optimized):**
```python
num_train_epochs = 2              # 2 passes over data (3x on GPU)
per_device_train_batch_size = 2   # Small batch for CPU (4 on GPU)
gradient_accumulation_steps = 4   # Simulate larger batch
learning_rate = 2e-4              # 0.0002 (conservative for LoRA)
warmup_ratio = 0.1                # Warmup first 10% of training
weight_decay = 0.01               # L2 regularization
max_grad_norm = 1.0               # Clip gradients to prevent explosion
optim = "adamw_torch"             # CPU-compatible optimizer
```

**Why These Values?**
- **Low batch size (2):** CPU memory constraints
- **Gradient accumulation (4):** Simulates batch size 8 (2 × 4)
- **Low learning rate:** Prevents catastrophic forgetting of pre-trained weights
- **Warmup:** Stabilizes training by gradually increasing learning rate
- **Gradient clipping:** Prevents "gradient explosion" (very large weight updates)

### **Phase 6: Training Loop**

**What Happens Each Step:**
1. Load a mini-batch (2 examples)
2. Forward pass: model predicts next token given input
3. Compute loss: how far prediction is from ground truth
4. Backward pass: compute gradients of loss w.r.t. LoRA parameters
5. Accumulate gradients (4 steps)
6. Update LoRA weights: `new_weight = old_weight - lr * gradient`
7. Log metrics every 5 steps
8. Save checkpoint every epoch

**Key Metrics:**
- **Loss:** Average prediction error (lower = better)
- **Learning rate:** Decreases over time (linear schedule)
- **Steps/sec:** Training speed (depends on CPU)

### **Phase 7: Model Saving**

```python
model.save_pretrained(output_dir)        # Save LoRA weights
tokenizer.save_pretrained(output_dir)    # Save tokenizer config
```

**Output Structure:**
```
models/llama-3.1-8b-dutch-tutor/
├── adapter_config.json              # LoRA configuration
├── adapter_model.bin                # LoRA weights (~60M params)
├── tokenizer.json                   # Tokenizer vocab
├── tokenizer.model                  # Tokenizer model (SentencePiece)
├── tokenizer_config.json            # Tokenizer settings
└── training_args.bin                # Training configuration
```

### **Phase 8: Inference Testing**

```python
# Test prompt formatted in chat style
prompt = format_with_system_and_user(...)

# Generate response
outputs = model.generate(
    **inputs,
    max_new_tokens=64,              # Don't exceed 64 new tokens
    temperature=0.7,                # Randomness (0=deterministic, 1=random)
    do_sample=False,                # Greedy decoding (faster for testing)
)
```

**Generation Strategy:**
- **Temperature:** Controls creativity
  - 0.7 = balanced (slightly creative)
  - 0.0 = deterministic (always same output)
  - 1.0+ = very random
- **max_new_tokens:** Prevents rambling
- **do_sample=False:** Greedy search (pick highest probability token)

### **Why CPU Training is Slow**

**Llama 3.1 8B Model:**
- 8 billion parameters
- Each parameter needs:
  - Forward pass computation
  - Gradient computation (backward pass)
  - Weight update
  
**CPU vs. GPU:**
- **GPU:** Massive parallelization (thousands of cores), optimized matrix multiplication
- **CPU:** Sequential processing, generic instruction set
- **Speedup:** ~50-100x slower on CPU

**Estimated Times:**
- CPU: ~17 minutes per epoch (100 examples, batch size 2 with accumulation)
- GPU (RTX 4090): ~30 seconds per epoch
- TPU: ~10 seconds per epoch

### **Why LoRA Instead of Full Fine-tuning?**

**Full Fine-tuning:**
- Train all 8B parameters
- Requires ~32GB VRAM (FP32)
- Slower training
- Risk of catastrophic forgetting

**LoRA Fine-tuning:**
- Train ~60M parameters (0.75% of total)
- Requires ~2GB VRAM
- 10x faster training
- Preserves base model knowledge

**Math Behind LoRA:**
```
Traditional: W_new = W_old + ΔW (where ΔW has 8B params)
LoRA:        W_new = W_old + BA (where B,A have ~60M params total)
             
Decomposition: ΔW (d_in × d_out) ≈ B (d_in × r) × A (r × d_out)
Where r << d_in and r << d_out
```
