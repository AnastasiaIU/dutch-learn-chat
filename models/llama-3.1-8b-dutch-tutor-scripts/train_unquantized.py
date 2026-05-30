#!/usr/bin/env python3
"""
Train LoRA adapter on Llama-3.1-8B WITHOUT 4-bit quantization.
This ensures clean, properly-shaped weights for GGUF conversion.
"""

import torch
import pandas as pd
from pathlib import Path
from datasets import Dataset, DatasetDict
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    TrainingArguments,
    BitsAndBytesConfig,
)
from peft import LoraConfig, get_peft_model
from trl import SFTTrainer

# Disable CUDA warning
import warnings
warnings.filterwarnings('ignore')

print("="*80)
print("TRAINING LORA ON UNQUANTIZED LLAMA-3.1-8B")
print("="*80)

# Paths
project_dir = Path(__file__).parent.parent.parent
dataset_path = project_dir / "backend" / "src" / "main" / "resources" / "datasets" / "cefr_training_dataset.json"
output_dir = project_dir / "models" / "llama-3.1-8b-dutch-tutor-unquantized"
output_dir.mkdir(parents=True, exist_ok=True)

# Step 1: Load dataset
print(f"\n[STEP 1] Loading training data from {dataset_path}...")
import json
with open(dataset_path) as f:
    data = json.load(f)

# Convert to text format for SFTTrainer
texts = []
for item in data:
    if isinstance(item, dict):
        # Assume format: {"input": "...", "output": "..."}
        inp = item.get("input", item.get("prompt", ""))
        out = item.get("output", item.get("response", item.get("completion", "")))
        if inp and out:
            text = f"User: {inp}\nAssistant: {out}"
            texts.append({"text": text})

print(f"✓ Loaded {len(texts)} training examples")

if not texts:
    print("✗ No training data found!")
    import sys
    sys.exit(1)

# Create dataset
dataset = Dataset.from_dict({"text": [t["text"] for t in texts]})
dataset = dataset.train_test_split(test_size=0.1)
print(f"  Train: {len(dataset['train'])}, Test: {len(dataset['test'])}")

# Step 2: Load unquantized base model
print(f"\n[STEP 2] Loading unquantized Llama-3.1-8B...")
model_id = "meta-llama/Llama-3.1-8B-Instruct"

# Try the unquantized version
try:
    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        torch_dtype=torch.float16,  # Use float16 to save memory
        device_map="auto",
    )
    print(f"✓ Loaded {model_id} in float16")
except Exception as e:
    print(f"⚠ Could not load {model_id}: {e}")
    print("  Trying alternative: unsloth version...")
    try:
        model = AutoModelForCausalLM.from_pretrained(
            "unsloth/Llama-3.1-8B-Instruct",  # Non-quantized version from Unsloth
            torch_dtype=torch.float16,
            device_map="auto",
        )
        print(f"✓ Loaded unsloth Llama-3.1-8B in float16")
    except Exception as e2:
        print(f"✗ Failed to load model: {e2}")
        import sys
        sys.exit(1)

# Step 3: Load tokenizer
print(f"\n[STEP 3] Loading tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(model_id if "unsloth" not in model_id else "unsloth/Llama-3.1-8B-Instruct")
tokenizer.pad_token = tokenizer.eos_token
print(f"✓ Tokenizer loaded")

# Step 4: Configure LoRA
print(f"\n[STEP 4] Configuring LoRA...")
lora_config = LoraConfig(
    r=8,
    lora_alpha=16,
    target_modules=["q_proj", "v_proj", "k_proj", "o_proj"],  # Broader target modules
    lora_dropout=0.05,
    bias="none",
    task_type="CAUSAL_LM"
)
print(f"✓ LoRA config: r=8, alpha=16")

# Step 5: Apply LoRA
print(f"\n[STEP 5] Applying LoRA to model...")
model = get_peft_model(model, lora_config)
print(f"✓ LoRA applied")
print(f"  Trainable params: {model.get_nb_trainable_parameters()}")

# Step 6: Configure training
print(f"\n[STEP 6] Configuring training...")
training_args = TrainingArguments(
    output_dir=str(output_dir / "checkpoints"),
    num_train_epochs=3,
    per_device_train_batch_size=4,
    per_device_eval_batch_size=4,
    warmup_steps=100,
    weight_decay=0.01,
    logging_steps=10,
    eval_strategy="steps",
    eval_steps=50,
    save_strategy="steps",
    save_steps=100,
    learning_rate=2e-4,
    bf16=torch.cuda.is_available() and torch.cuda.is_bf16_supported(),
    fp16=torch.cuda.is_available() and not torch.cuda.is_bf16_supported(),
    gradient_accumulation_steps=2,
    max_grad_norm=1.0,
)
print(f"✓ Training args configured")

# Step 7: Train
print(f"\n[STEP 7] Starting training...")
trainer = SFTTrainer(
    model=model,
    tokenizer=tokenizer,
    train_dataset=dataset["train"],
    eval_dataset=dataset["test"],
    args=training_args,
    packing=False,
    max_seq_length=512,
)

print(f"Training in progress...")
trainer.train()
print(f"✓ Training complete!")

# Step 8: Save final adapter
print(f"\n[STEP 8] Saving final LoRA adapter...")
model.save_pretrained(str(output_dir / "adapter"))
tokenizer.save_pretrained(str(output_dir / "adapter"))
print(f"✓ Adapter saved to {output_dir}/adapter")

# Step 9: Save merged model
print(f"\n[STEP 9] Merging and saving full model...")
merged_model = model.merge_and_unload()
merged_model.save_pretrained(str(output_dir / "merged"))
tokenizer.save_pretrained(str(output_dir / "merged"))
print(f"✓ Merged model saved to {output_dir}/merged")

# Verify weights have correct shape
print(f"\n[STEP 10] Verifying weight shapes...")
state_dict = merged_model.state_dict()
for key in state_dict.keys():
    if 'q_proj.weight' in key:
        shape = state_dict[key].shape
        print(f"  Sample weight {key}: {shape}")
        if shape == (4096, 4096) or (len(shape) == 2 and shape[0] == 4096 and shape[1] == 4096):
            print(f"  ✓ Correct shape!")
        break

print("\n" + "="*80)
print("TRAINING COMPLETE!")
print("="*80)
print(f"Adapter: {output_dir}/adapter")
print(f"Merged model: {output_dir}/merged")
print(f"\nNext: Convert merged model to GGUF")
print(f"cd C:\\dev\\llama.cpp")
print(f'python convert_hf_to_gguf.py "{output_dir}/merged" --outfile "C:\\dev\\fontys\\dutch-learn-chat\\models\\llama-3.1-8b-dutch-tutor-gguf\\model-clean.gguf"')
