import json
import re

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"

with open(cache_path, "r", encoding="utf-8") as f:
    cache = json.load(f)

# Reading passage start pages
passage_pages = {
    "Test 1 Passage 1": 15,
    "Test 1 Passage 2": 20,
    "Test 1 Passage 3": 25,
    "Test 2 Passage 1": 40,
    "Test 2 Passage 2": 45,
    "Test 2 Passage 3": 50,
    "Test 3 Passage 1": 64,
    "Test 3 Passage 2": 69,
    "Test 3 Passage 3": 74,
    "Test 4 Passage 1": 83,
    "Test 4 Passage 2": 87,
    "Test 4 Passage 3": 91,
}

print("Reading Passage Starts:")
for name, page_num in passage_pages.items():
    text = cache.get(str(page_num), "")
    clean = re.sub(r'\s+', ' ', text).strip()
    print(f"{name} (Page {page_num}):")
    print(f"  {clean[:300]}")
    print("-" * 50)
