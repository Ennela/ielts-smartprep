import json
import requests
import re
import time

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"
with open(cache_path, "r", encoding="utf-8") as f:
    ocr_cache = json.load(f)

def call_gemini(ocr_text):
    system_prompt = (
        "You are an IELTS grader. Extract the reading answer key from the OCR text.\n"
        "Format the output as a JSON object where the keys are string question numbers from '1' to '40' and values are the correct answers (e.g. 'FALSE', 'grain', 'C', 'B', 'E').\n"
        "Clean up OCR noise."
    )
    headers = {"Content-Type": "application/json"}
    payload = {
        "system_instruction": {
            "parts": [{"text": system_prompt}]
        },
        "contents": [{
            "parts": [{"text": ocr_text}]
        }],
        "generationConfig": {
            "temperature": 0.1,
            "responseMimeType": "application/json"
        }
    }
    response = requests.post(gemini_url, headers=headers, json=payload, timeout=60)
    if response.status_code == 200:
        return response.json()['candidates'][0]['content']['parts'][0]['text']
    else:
        raise Exception(f"API Error {response.status_code}: {response.text}")

pages = {2: 122}
results = {}
for test, page in pages.items():
    print(f"--- Test {test} (Page {page}) ---")
    try:
        txt = ocr_cache.get(str(page), "")
        res = call_gemini(txt)
        print(res)
    except Exception as e:
        print(f"Error: {e}")


