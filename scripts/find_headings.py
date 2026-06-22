import json
import re

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"

with open(cache_path, "r", encoding="utf-8") as f:
    cache = json.load(f)

patterns = {
    "READING PASSAGE 1": re.compile(r'reading\s+passage\s+1', re.IGNORECASE),
    "READING PASSAGE 2": re.compile(r'reading\s+passage\s+2', re.IGNORECASE),
    "READING PASSAGE 3": re.compile(r'reading\s+passage\s+3', re.IGNORECASE),
    "WRITING TASK 1": re.compile(r'writing\s+task\s+1', re.IGNORECASE),
    "WRITING TASK 2": re.compile(r'writing\s+task\s+2', re.IGNORECASE),
    "PART 1": re.compile(r'part\s+1', re.IGNORECASE),
    "PART 2": re.compile(r'part\s+2', re.IGNORECASE),
    "PART 3": re.compile(r'part\s+3', re.IGNORECASE),
    "PART 4": re.compile(r'part\s+4', re.IGNORECASE),
    "Audioscripts": re.compile(r'audioscripts', re.IGNORECASE),
    "Answer keys": re.compile(r'answer\s+keys', re.IGNORECASE)
}

print("Searching for exact pattern matches across pages:")
for name, pattern in patterns.items():
    matches = []
    for page, text in cache.items():
        if pattern.search(text):
            matches.append(int(page))
    print(f"Pattern '{name}': pages {sorted(matches)}")
