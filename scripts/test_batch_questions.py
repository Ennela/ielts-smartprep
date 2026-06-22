import requests
import json

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"

with open("scripts/cam19_ocr_cache.json", "r", encoding="utf-8") as f:
    ocr_cache = json.load(f)
passage_text = ocr_cache.get("15", "") + "\n" + ocr_cache.get("16", "")

system_prompt = (
    "You are a professional IELTS reading instructor.\n"
    "Given the Reading Passage and a list of questions with their correct answers, generate an explanation and evidence sentence for each question.\n"
    "Return a JSON array of objects, where each object has:\n"
    "- 'order_index': (integer) the order index of the question\n"
    "- 'explanation': (string) a concise explanation (2-3 sentences max) explaining why the answer is correct\n"
    "- 'evidence_sentence': (string) the exact, verbatim sentence (or portion of sentence) from the Reading Passage that directly proves this answer. The evidence sentence must match a substring inside the passage text EXACTLY."
)

user_prompt = f"""
READING PASSAGE:
{passage_text[:3000]}  # Use first half for shorter test

QUESTIONS:
- Q1 (order_index: 1): Statement about racket development -> ANSWER: FALSE
- Q2 (order_index: 2): Statement about racket development -> ANSWER: FALSE
- Q3 (order_index: 3): Statement about racket development -> ANSWER: NOT GIVEN
"""

headers = {"Content-Type": "application/json"}
payload = {
    "system_instruction": {
        "parts": [{"text": system_prompt}]
    },
    "contents": [{
        "parts": [{"text": user_prompt}]
    }],
    "generationConfig": {
        "temperature": 0.2,
        "topP": 0.95,
        "responseMimeType": "application/json"
    }
}

try:
    print("Sending batch request to Gemini API...")
    response = requests.post(gemini_url, headers=headers, json=payload, timeout=20)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}")
except Exception as e:
    print(f"Exception: {e}")
