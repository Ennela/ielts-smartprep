import requests
import json

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key={api_key}"

headers = {"Content-Type": "application/json"}
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
