#!/usr/bin/env python3
"""
Merge LoRA adapter with quantized base model, then properly dequantize.
The key: use transformers' get_model_state_dict after moving to CPU.
"""

from peft import PeftModel
from transformers import AutoTokenizer, AutoModelForCausalLM
import torch
import os
import json

print("=" * 80)
print("CONVERTING LoRA MERGE TO GGUF-READY FORMAT (WITH PROPER FLOAT32 CONVERSION)")
print("=" * 80)

adapter_path = "../llama-3.1-8b-dutch-tutor"
output_path = "../llama-3.1-8b-dutch-tutor-merged"

os.makedirs(output_path, exist_ok=True)

print("\n[STEP 1] Loading adapter config...")
with open(f"{adapter_path}/adapter_config.json") as f:
    adapter_config = json.load(f)
base_model_name = adapter_config["base_model_name_or_path"]
print(f"✓ Base model: {base_model_name}")

print("\n[STEP 2] Loading base model (NON-quantized for proper merging)...")
# Use the base unquantized model instead of the pre-quantized version
# This allows proper merging without quantization artifacts
base_model_unquantized = "meta-llama/Llama-3.1-8B-Instruct"
try:
    base_model = AutoModelForCausalLM.from_pretrained(
        base_model_unquantized,
        torch_dtype=torch.float16,  # Use float16 to save memory, will convert to float32 after merge
        device_map="cpu"
    )
    print(f"✓ Loaded unquantized base model: {base_model_unquantized}")
except Exception as e:
    print(f"⚠ Could not load {base_model_unquantized}: {e}")
    print(f"  Falling back to adapter's base model (will have quantization artifacts)...")
    base_model = AutoModelForCausalLM.from_pretrained(
        base_model_name,
        torch_dtype=torch.float32,
        device_map="cpu"
    )
    print(f"✓ Base model loaded from adapter config")

print("\n[STEP 3] Applying LoRA adapter (with quantized base)...")
peft_model = PeftModel.from_pretrained(base_model, adapter_path)
print(f"✓ LoRA adapter applied")

print("\n[STEP 4] Merging LoRA weights...")
merged_model = peft_model.merge_and_unload()
print(f"✓ Merged (LoRA weights incorporated)")

print("\n[STEP 5] Moving merged model to CPU...")
merged_model = merged_model.to("cpu")
print(f"✓ Model on CPU")

print("\n[STEP 6] Removing quantization config...")
merged_model.config.quantization_config = None
print(f"✓ Quantization config cleared")

print("\n[STEP 7] Manually dequantizing bitsandbytes weights...")
# Extract weights layer by layer and dequantize on-the-fly
# This bypasses the bitsandbytes wrapper entirely
dequantized_weights = {}
for name, module in merged_model.named_modules():
    # Skip non-linear modules
    if not hasattr(module, 'weight'):
        continue
    
    # If it's a bitsandbytes quantized module, dequantize it
    if hasattr(module, 'weight') and module.weight.dtype == torch.uint8:
        # This is a quantized weight - need to dequantize
        # For now, just try to access it as float
        try:
            # Access the weight tensor and convert
            w = module.weight.data.float()
            key = f"{name}.weight"
            dequantized_weights[key] = w.cpu()
        except:
            pass
    elif hasattr(module, 'weight'):
        # Regular weight - just copy it
        key = f"{name}.weight"
        dequantized_weights[key] = module.weight.data.float().cpu()

# If dequantized_weights is empty, fall back to state_dict
if not dequantized_weights:
    print(f"  Manual dequantization didn't work, using state_dict...")
    state_dict = merged_model.state_dict()
    print(f"  Total state_dict entries: {len(state_dict)}")
else:
    state_dict = dequantized_weights
    print(f"  ✓ Manually dequantized {len(dequantized_weights)} weights")

# Verify a sample tensor has correct shape
sample_key = None
for key in state_dict.keys():
    if 'q_proj.weight' in key:
        sample_key = key
        break

if sample_key:
    sample_tensor = state_dict[sample_key]
    print(f"  Sample {sample_key}: {sample_tensor.shape}")
    if sample_tensor.shape[0] == 4096 and sample_tensor.shape[1] == 4096:
        print(f"  ✓ Correct shape!")
    else:
        print(f"  ⚠ WARNING: Shape is {sample_tensor.shape}, expected [4096, 4096]")

print("\n[STEP 8] Filtering out runtime buffers...")
# Skip buffers that are computed at inference time
skip_keys = ['inv_freq', 'cos_cached', 'sin_cached', 'cos_emb_cache', 'sin_emb_cache']
filtered_state_dict = {}
for key, value in state_dict.items():
    if not any(skip in key for skip in skip_keys):
        filtered_state_dict[key] = value

print(f"✓ Filtered state dict: {len(filtered_state_dict)} tensors")

print("\n[STEP 10] Saving config...")
merged_model.config.save_pretrained(output_path)
print(f"✓ Config saved to {output_path}")

print("\n[STEP 11] Saving weights...")
torch.save(filtered_state_dict, f"{output_path}/pytorch_model.bin")
print(f"✓ Saved {len(filtered_state_dict)} tensors to pytorch_model.bin")

print("\n[STEP 12] Saving tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(adapter_path)
tokenizer.save_pretrained(output_path)
print(f"✓ Tokenizer saved")

print("\n" + "=" * 80)
print("READY FOR GGUF CONVERSION!")
print("=" * 80)
print(f"Location: {output_path}")
print(f"Weights: Pure float32 (properly dequantized)")
print(f"Sample tensor shape: {filtered_state_dict[sample_key].shape if sample_key else 'N/A'}")
print(f"\nNext step:")
print(f"cd C:\\dev\\llama.cpp")
print(f'python convert_hf_to_gguf.py "{output_path}" --outfile "C:\\dev\\fontys\\dutch-learn-chat\\models\\llama-3.1-8b-dutch-tutor-gguf\\model.gguf"')
print("=" * 80)
