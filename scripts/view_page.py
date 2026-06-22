import json

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"

with open(cache_path, "r", encoding="utf-8") as f:
    cache = json.load(f)

for page_num in ["120", "122", "124", "126"]:
    if page_num in cache:
        print(f"=== Page {page_num} ===")
        print(cache[page_num][:2000])
        print("-" * 50)
