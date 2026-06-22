import json
import re

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"

with open(cache_path, "r", encoding="utf-8") as f:
    cache = json.load(f)

print("Listing first 100 characters of some pages:")
for page_num in sorted(list(cache.keys()), key=int):
    text = cache[page_num]
    # Clean whitespace
    clean = re.sub(r'\s+', ' ', text).strip()
    
    # Check for keywords loosely
    found = []
    if re.search(r'\btest\b', clean, re.IGNORECASE):
        found.append("TEST")
    if "passage" in clean.lower():
        found.append("PASSAGE")
    if "writing" in clean.lower():
        found.append("WRITING")
    if "audioscript" in clean.lower():
        found.append("AUDIOSCRIPT")
    if "answer key" in clean.lower() or "answer keys" in clean.lower() or "keys" in clean.lower():
        found.append("KEY")
        
    if found:
        print(f"Page {page_num}: {found} -> {clean[:120]}")
