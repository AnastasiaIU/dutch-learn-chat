"""
Llama 3.1 8B Fine-tuning Script for Dutch Language Tutoring (CPU-Compatible)
=============================================================================
This script fine-tunes the Meta Llama-3.1-8B-Instruct model on the CEFR training dataset
for Dutch language tutoring at A1/A2/B1 levels.

CPU-Optimized Version: Uses standard PyTorch without quantization for compatibility.

Requirements:
- torch
- transformers
- datasets
- peft (for LoRA - Parameter Efficient Fine-Tuning)
- accelerate
"""

import json
import os
import warnings
from pathlib import Path
from typing import Dict

import torch
from datasets import Dataset
from peft import LoraConfig, get_peft_model
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    Trainer,
    TrainingArguments,
)

# Suppress warnings
warnings.filterwarnings("ignore")

# =====================
# Configuration
# =====================
MODEL_ID = "unsloth/Meta-Llama-3.1-8B-Instruct-bnb-4bit"
DATASET_PATH = "backend/src/main/resources/datasets/cefr_training_dataset.json"
OUTPUT_DIR = "models/llama-3.1-8b-dutch-tutor"
DEVICE = "cpu"

# Hyperparameters (reduced for CPU)
BATCH_SIZE = 2
LEARNING_RATE = 2e-4
NUM_EPOCHS = 2
MAX_SEQ_LENGTH = 256
LORA_R = 8
LORA_ALPHA = 16
LORA_DROPOUT = 0.05

print(f"Device: {DEVICE}")
print(f"GPU Available: {torch.cuda.is_available()}")


# =====================
# 1. Load Dataset
# =====================
def load_dataset_from_json(filepath: str) -> Dataset:
    """Load CEFR training dataset from JSON file."""
    print(f"\n[1] Loading dataset from {filepath}...")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"   Loaded {len(data)} examples")
    
    # Group by level for analysis
    level_counts = {}
    type_counts = {}
    for item in data:
        level = item.get("level", "unknown")
        item_type = item.get("type", "unknown")
        level_counts[level] = level_counts.get(level, 0) + 1
        type_counts[item_type] = type_counts.get(item_type, 0) + 1
    
    print(f"   Distribution by level: {level_counts}")
    print(f"   Distribution by type: {type_counts}")
    
    # Limit dataset for CPU training
    if len(data) > 100:
        print(f"   Limiting dataset to 100 examples for CPU training...")
        data = data[:100]
    
    return Dataset.from_dict({
        "system": [item["system"] for item in data],
        "user": [item["user"] for item in data],
        "assistant": [item["assistant"] for item in data],
        "level": [item["level"] for item in data],
        "type": [item["type"] for item in data],
    })


# =====================
# 2. Format Data for Chat
# =====================
def format_chat_example(example: Dict[str, str]) -> str:
    """Format example as chat conversation for instruction-following."""
    system_msg = f"<|start_header_id|>system<|end_header_id|>\n\n{example['system']}<|eot_id|>"
    user_msg = f"<|start_header_id|>user<|end_header_id|>\n\n{example['user']}<|eot_id|>"
    assistant_msg = f"<|start_header_id|>assistant<|end_header_id|>\n\n{example['assistant']}<|eot_id|>"
    
    return f"{system_msg}\n{user_msg}\n{assistant_msg}"


def preprocess_function(examples: Dict, tokenizer, max_length: int = MAX_SEQ_LENGTH):
    """Preprocess examples for training."""
    # Format each example as a chat conversation
    texts = [format_chat_example({
        "system": system,
        "user": user,
        "assistant": assistant
    }) for system, user, assistant in zip(
        examples["system"],
        examples["user"],
        examples["assistant"]
    )]
    
    # Tokenize
    encodings = tokenizer(
        texts,
        max_length=max_length,
        padding="max_length",
        truncation=True,
        return_tensors="pt"
    )
    
    # For causal LM, labels are the same as input_ids
    encodings["labels"] = encodings["input_ids"].clone()
    
    # Mask padding tokens in labels (we don't want to train on padding)
    encodings["labels"][encodings["input_ids"] == tokenizer.pad_token_id] = -100
    
    return encodings


# =====================
# 3. Load Model & Tokenizer
# =====================
def load_model_and_tokenizer(model_id: str):
    """Load model and tokenizer (CPU-compatible)."""
    print(f"\n[2] Loading model and tokenizer: {model_id}...")
    
    # Load tokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    # Add padding token if not present
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token
    
    print(f"   Tokenizer loaded (vocab size: {len(tokenizer)})")
    
    print("   Loading model on CPU (this may take a minute)...")
    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        device_map="cpu",
        torch_dtype=torch.float32,
        low_cpu_mem_usage=True,
    )
    
    print(f"   Model loaded: {model.config.model_type}")
    print(f"   Model parameters: {model.num_parameters():,}")
    
    return model, tokenizer


# =====================
# 4. Setup LoRA
# =====================
def setup_lora(model):
    """Setup LoRA (Low-Rank Adaptation) for efficient fine-tuning."""
    print(f"\n[3] Setting up LoRA configuration...")
    
    # LoRA config
    lora_config = LoraConfig(
        r=LORA_R,
        lora_alpha=LORA_ALPHA,
        target_modules=["q_proj", "v_proj"],
        lora_dropout=LORA_DROPOUT,
        bias="none",
        task_type="CAUSAL_LM",
    )
    
    # Get PEFT model
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()
    
    return model


# =====================
# 5. Train
# =====================
def train_model(model, tokenizer, dataset, output_dir: str):
    """Fine-tune the model."""
    print(f"\n[4] Preparing training...")
    
    # Create output directory
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    # Preprocess dataset
    print("   Preprocessing dataset...")
    tokenized_dataset = dataset.map(
        lambda x: preprocess_function(x, tokenizer, MAX_SEQ_LENGTH),
        batched=True,
        remove_columns=dataset.column_names,
        desc="Tokenizing"
    )
    
    # Training arguments (optimized for CPU)
    training_args = TrainingArguments(
        output_dir=output_dir,
        num_train_epochs=NUM_EPOCHS,
        per_device_train_batch_size=BATCH_SIZE,
        gradient_accumulation_steps=4,
        learning_rate=LEARNING_RATE,
        lr_scheduler_type="linear",
        weight_decay=0.01,
        warmup_ratio=0.1,
        max_grad_norm=1.0,
        logging_steps=5,
        save_strategy="epoch",
        save_total_limit=2,
        seed=42,
        optim="adamw_torch",
        fp16=False,
        use_cpu=True,
    )
    
    # Trainer
    trainer = Trainer(
        model=model,
        
        args=training_args,
        train_dataset=tokenized_dataset,
    )
    
    # Train
    print(f"\n[5] Starting training on {DEVICE}...")
    print("=" * 60)
    print("Note: CPU training will be slow. For production, use GPU.")
    print("=" * 60)
    trainer.train()
    print("=" * 60)
    
    # Save final model
    print(f"\n[6] Saving model to {output_dir}...")
    model.save_pretrained(output_dir)
    tokenizer.save_pretrained(output_dir)
    
    print(f"\n✓ Training complete!")
    print(f"  Model saved to: {output_dir}")
    
    return model, tokenizer


# =====================
# 7. Test Inference
# =====================
def test_inference(model, tokenizer):
    """Test the fine-tuned model with sample prompts."""
    print(f"\n[7] Testing inference...")
    print("=" * 60)
    
    model.eval()
    
    # Test prompts
    test_prompts = [
        {
            "system": "You are a Dutch language tutor for beginners (A1 level). Use very simple vocabulary and short sentences. Be encouraging.",
            "user": "Hoe zeg ik 'hello' in het Nederlands?",
        },
        {
            "system": "You are a Dutch language tutor for intermediate learners (A2 level). Help with grammar and vocabulary.",
            "user": "Corrigeer deze zin: Ik gaat naar het winkel",
        },
    ]
    
    with torch.no_grad():
        for i, test_prompt in enumerate(test_prompts, 1):
            # Format as chat
            system_msg = f"<|start_header_id|>system<|end_header_id|>\n\n{test_prompt['system']}<|eot_id|>"
            user_msg = f"<|start_header_id|>user<|end_header_id|>\n\n{test_prompt['user']}<|eot_id|>"
            prompt = f"{system_msg}\n{user_msg}\n<|start_header_id|>assistant<|end_header_id|>\n\n"
            
            # Tokenize
            inputs = tokenizer(prompt, return_tensors="pt")
            
            # Generate
            outputs = model.generate(
                **inputs,
                max_new_tokens=64,
                temperature=0.7,
                top_p=0.9,
                do_sample=False,
            )
            
            # Decode
            response = tokenizer.decode(outputs[0], skip_special_tokens=True)
            
            print(f"\nTest {i}:")
            print(f"  System: {test_prompt['system']}")
            print(f"  User: {test_prompt['user']}")
            print(f"  Assistant: {response[len(prompt):].strip()}")
    
    print("\n" + "=" * 60)


# =====================
# Main Execution
# =====================
def main():
    """Main training pipeline."""
    print("\n" + "=" * 60)
    print("Llama 3.1 8B Fine-tuning for Dutch Language Tutoring")
    print("(CPU-Compatible Version)")
    print("=" * 60)
    
    # Check if dataset exists
    if not os.path.exists(DATASET_PATH):
        raise FileNotFoundError(f"Dataset not found at {DATASET_PATH}")
    
    # Load dataset
    dataset = load_dataset_from_json(DATASET_PATH)
    
    # Load model and tokenizer
    model, tokenizer = load_model_and_tokenizer(MODEL_ID)
    
    # Setup LoRA
    model = setup_lora(model)
    
    # Train
    model, tokenizer = train_model(model, tokenizer, dataset, OUTPUT_DIR)
    
    # Test inference
    test_inference(model, tokenizer)
    
    print("\n" + "=" * 60)
    print("✓ Training pipeline complete!")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
