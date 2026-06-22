import requests
import json

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
models = ["gemini-1.5-flash", "gemini-2.5-flash", "gemini-flash-latest"]

for model in models:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
    payload = {
        "contents": [{"parts": [{"text": "Hello, respond with 'OK'"}]}]
    }
    headers = {"Content-Type": "application/json"}
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
