import requests
import json

from runtime_config import require_env

api_key = require_env("GEMINI_API_KEY")
gemini_url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

headers = {"Content-Type": "application/json", "x-goog-api-key": api_key}
payload = {
    "contents": [{
        "parts": [{"text": "Hello, this is a test. Reply with 'OK'."}]
    }]
}

try:
    print("Sending request to Gemini API...")
    response = requests.post(gemini_url, headers=headers, json=payload, timeout=10)
    print(f"Status Code: {response.status_code}")
    print(f"Response: {response.text}")
except Exception as e:
    print(f"Exception: {e}")
