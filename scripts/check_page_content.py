import json

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"

with open(cache_path, "r", encoding="utf-8") as f:
    cache = json.load(f)

for p in [29, 30, 31, 32]:
    print(f"=== Page {p} ===")
    print(cache.get(str(p), "")[:500])
    print("-" * 50)
