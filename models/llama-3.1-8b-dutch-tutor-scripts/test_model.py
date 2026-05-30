"""
Test the fine-tuned Llama model with proper inference settings
"""

import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer

# Load the fine-tuned model
print("Loading fine-tuned model...")
model_path = "models/llama-3.1-8b-dutch-tutor"
model = AutoPeftModelForCausalLM.from_pretrained(
    model_path,
    device_map="cpu",
    torch_dtype=torch.float32
)
tokenizer = AutoTokenizer.from_pretrained(model_path)

print(f"✓ Model loaded from {model_path}")
print(f"✓ Model type: {model.config.model_type}")
print(f"✓ Device: cpu")

# Test prompts
test_cases = [
    {
        "level": "A1",
        "system": "Je bent een Nederlandse taaldocent voor absolute beginners (A1-niveau). Gebruik alleen zeer eenvoudige woordenschat en korte zinnen (max 8 woorden). Wees bemoedigend.",
        "user": "Wat is hallo in het Nederlands?"
    },
    {
        "level": "A2",
        "system": "Je bent een Nederlandse taaldocent voor elementaire leerders (A2-niveau). Help met grammatica en woordenschat. Gebruik eenvoudige, alledaagse taal.",
        "user": "Corrigeer deze zin: Ik gaat naar het winkel"
    },
    {
        "level": "B1",
        "system": "Je bent een Nederlandse taaldocent voor intermediaire leerders (B1-niveau). Geef gedetailleerde uitleg met voorbeelden.",
        "user": "Wat is het verschil tussen 'naar' en 'in'?"
    }
]

print("\n" + "="*80)
print("INFERENCE TESTS")
print("="*80)

model.eval()
with torch.no_grad():
    for i, test in enumerate(test_cases, 1):
        print(f"\n[Test {i}] Level: {test['level']}")
        print(f"System: {test['system'][:60]}...")
        print(f"User: {test['user']}")
        print("-" * 80)
        
        # Format prompt using chat template
        messages = [
            {"role": "system", "content": test["system"]},
            {"role": "user", "content": test["user"]}
        ]
        
        # Apply chat template
        prompt = tokenizer.apply_chat_template(
            messages,
            tokenize=False,
            add_generation_prompt=True
        )
        
        # Tokenize
        inputs = tokenizer(
            prompt,
            return_tensors="pt",
            truncation=True,
            max_length=512
        )
        
        # Generate with adjusted settings
        try:
            outputs = model.generate(
                **inputs,
                max_new_tokens=100,
                min_new_tokens=10,
                do_sample=False,  # Greedy decoding
                num_beams=1,
                eos_token_id=tokenizer.eos_token_id,
                pad_token_id=tokenizer.pad_token_id,
            )
            
            # Decode response
            full_response = tokenizer.decode(
                outputs[0],
                skip_special_tokens=True
            )
            
            # Extract only the assistant's response
            if "assistant" in full_response:
                response = full_response.split("assistant")[-1].strip()
            else:
                response = full_response[len(prompt):].strip()
            
            if response:
                print(f"Response: {response}\n")
            else:
                print(f"[No response generated - model may need more data]")
                print(f"Full output: {full_response[-200:]}\n")
                
        except Exception as e:
            print(f"Error generating response: {str(e)}\n")

print("="*80)
print("✓ Inference testing complete!")
print("="*80)
print("\nModel is ready to use! Next steps:")
print("See LLAMA_USAGE_GUIDE.md for integration code")
