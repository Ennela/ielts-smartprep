import requests
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"

headers = {"Content-Type": "application/json"}
payload = {
    "contents": [{
        "parts": [{"text": "Reply with 'OK'."}]
    }]
}

def worker(i):
    start = time.time()
    try:
        print(f"[{i}] Sending request...")
        response = requests.post(gemini_url, headers=headers, json=payload, timeout=15)
        duration = time.time() - start
        print(f"[{i}] Done: status={response.status_code}, time={duration:.2f}s")
        return i, response.status_code, response.text
    except Exception as e:
        duration = time.time() - start
        print(f"[{i}] Exception: {e}, time={duration:.2f}s")
        return i, None, str(e)

with ThreadPoolExecutor(max_workers=3) as executor:
    futures = [executor.submit(worker, i) for i in range(3)]
    for fut in as_completed(futures):
        fut.result()
