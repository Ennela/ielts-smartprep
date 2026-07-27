import requests
import json

from runtime_config import require_env

api_key = require_env("GEMINI_API_KEY")
models = ["gemini-1.5-flash", "gemini-2.5-flash", "gemini-flash-latest"]

for model in models:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    payload = {
        "contents": [{"parts": [{"text": "Hello, respond with 'OK'"}]}]
    }
    headers = {"Content-Type": "application/json", "x-goog-api-key": api_key}
    try:
        res = requests.post(url, json=payload, headers=headers, timeout=10)
        print(f"Model: {model}")
        print(f"  Status: {res.status_code}")
        if res.status_code == 200:
            print(f"  Response: {res.json()['candidates'][0]['content']['parts'][0]['text'].strip()}")
        else:
            print(f"  Error: {res.text}")
    except Exception as e:
        print(f"Model {model} failed: {e}")
