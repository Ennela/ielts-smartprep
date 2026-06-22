import requests
import json

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"

system_prompt = (
    "You are a professional IELTS content editor. Extract the main reading passage text from the raw OCR text.\n"
    "Guidelines:\n"
    "1. Extract ONLY the reading passage content (title, headings, paragraphs).\n"
    "2. DO NOT include questions, instructions (e.g. 'You should spend about 20 minutes...'), footers, headers, page numbers, or line numbers.\n"
    "3. Clean up obvious OCR spelling or layout alignment errors (e.g. merge broken column splits into continuous paragraphs).\n"
    "4. Return a JSON object with 'title' and 'passage_text'."
)

# Load mock OCR text for Page 15 and 16
with open("scripts/cam19_ocr_cache.json", "r", encoding="utf-8") as f:
    ocr_cache = json.load(f)
ocr_text = ocr_cache.get("15", "") + "\n" + ocr_cache.get("16", "")

headers = {"Content-Type": "application/json"}
payload = {
    "system_instruction": {
        "parts": [{"text": system_prompt}]
    },
    "contents": [{
        "parts": [{"text": f"OCR TEXT:\n{ocr_text}"}]
    }],
    "generationConfig": {
        "temperature": 0.2,
        "topP": 0.95,
        "responseMimeType": "application/json"
    }
}

try:
    print("Sending request to Gemini API with system prompt & JSON constraint...")
    response = requests.post(gemini_url, headers=headers, json=payload, timeout=20)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text[:500]}...")
except Exception as e:
    print(f"Exception: {e}")
