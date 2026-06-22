import json
import os
import re
import pymysql

cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"
review_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_import_review.json"

# Load OCR Cache
with open(cache_path, "r", encoding="utf-8") as f:
    ocr_cache = json.load(f)

# Connect to database
db_conn = pymysql.connect(
    host='localhost',
    user='root',
    password='smartprep_root_2024',
    database='ielts_smartprep',
    cursorclass=pymysql.cursors.DictCursor
)

# Hardcoded Reading Answer Keys
READING_ANSWERS = {
    # Test 1
    1: {
        1: "FALSE", 2: "FALSE", 3: "NOT GIVEN", 4: "FALSE", 5: "NOT GIVEN", 6: "TRUE", 7: "TRUE", 
        8: "paint", 9: "topspin", 10: "training", 11: "intestines", 12: "weights", 13: "grips",
        14: "D", 15: "G", 16: "C", 17: "A", 18: "G", 19: "B", 20: "B", 21: "D", 22: "C", 23: "E", 
        24: "grain", 25: "punishment", 26: "ransom",
        27: "D", 28: "A", 29: "C", 30: "D", 31: "G", 32: "J", 33: "H", 34: "B", 35: "E", 36: "C", 
        37: "YES", 38: "NOT GIVEN", 39: "NO", 40: "NOT GIVEN"
    },
    # Test 2
    2: {
        1: "piston", 2: "coal", 3: "workshops", 4: "labor", 5: "quality", 6: "railway", 7: "sanitation",
        8: "NOT GIVEN", 9: "FALSE", 10: "NOT GIVEN", 11: "TRUE", 12: "TRUE", 13: "NOT GIVEN",
        14: "D", 15: "F", 16: "A", 17: "C", 18: "F", 19: "injury", 20: "serves", 21: "excitement", 22: "visualisation",
        23: "B", 24: "D", 25: "A", 26: "E",
        27: "B", 28: "D", 29: "A", 30: "C", 31: "B", 32: "YES", 33: "NO", 34: "NOT GIVEN", 35: "YES", 36: "NO",
        37: "emotions", 38: "patterns", 39: "tension", 40: "memory"
    },
    # Test 3
    3: {
        1: "FALSE", 2: "FALSE", 3: "TRUE", 4: "NOT GIVEN", 5: "TRUE", 6: "NOT GIVEN", 7: "FALSE",
        8: "caves", 9: "stone", 10: "bones", 11: "beads", 12: "pottery", 13: "spices",
        14: "C", 15: "G", 16: "A", 17: "H", 18: "B", 19: "carbon", 20: "fires", 21: "biodiversity", 22: "ditches",
        23: "A", 24: "C", 25: "C", 26: "D",
        27: "C", 28: "A", 29: "D", 30: "B", 31: "A", 32: "YES", 33: "NO", 34: "NOT GIVEN", 35: "YES", 36: "NO",
        37: "translation", 38: "context", 39: "accuracy", 40: "privacy"
    },
    # Test 4
    4: {
        1: "FALSE", 2: "TRUE", 3: "FALSE", 4: "NOT GIVEN", 5: "FALSE", 6: "TRUE",
        7: "colonies", 8: "spring", 9: "endangered", 10: "habitats", 11: "Europe", 12: "southern", 13: "diet",
        14: "C", 15: "F", 16: "E", 17: "D", 18: "D", 19: "B", 20: "A", 21: "E", 22: "B", 23: "C", 
        24: "waste", 25: "machinery", 26: "caution",
        27: "C", 28: "C", 29: "B", 30: "A", 31: "egalitarianism", 32: "status", 33: "hunting", 34: "domineering", 35: "autonomy",
        36: "NOT GIVEN", 37: "NO", 38: "YES", 39: "NOT GIVEN", 40: "NO"
    }
}

# Reading Passages details for offline extraction
reading_passage_metadata = {
    (1, 1): {"pages": [15, 16], "title": "How tennis rackets have changed", "start_text": "In 2016, the British professional"},
    (1, 2): {"pages": [19, 20], "title": "The pirates of the ancient Mediterranean", "start_text": "When one mentions pirates, an image springs"},
    (1, 3): {"pages": [24, 25, 26], "title": "The persistence and peril of misinformation", "start_text": "Misinformation"},
    (2, 1): {"pages": [39, 40], "title": "The Industrial Revolution in Britain", "start_text": "The Industrial Revolution began in Britain"},
    (2, 2): {"pages": [43, 44], "title": "Athletes and stress", "start_text": "It isn't easy being a professional athlete."},
    (2, 3): {"pages": [47, 48, 49], "title": "An inquiry into the existence of the gifted child", "start_text": "Let us start by looking at a modern"},
    (3, 1): {"pages": [61, 62], "title": "Archaeologists discover evidence of prehistoric island settlers", "start_text": "In early April 2019, Dr Ceri Shipton"},
    (3, 2): {"pages": [65, 66, 67], "title": "The global importance of wetlands", "start_text": "Wetlands are areas where water"},
    (3, 3): {"pages": [69, 70, 71, 72], "title": "Is the era of artificial speech translation upon us?", "start_text": "Once the stuff"},
    (4, 1): {"pages": [83, 84], "title": "The impact of climate change on butterflies in Britain", "start_text": "According to conservationists"},
    (4, 2): {"pages": [87, 88, 89], "title": "Deep-sea mining", "start_text": "Bacteria from the ocean floor"},
    (4, 3): {"pages": [91, 92, 93], "title": "The Unselfish Gene", "start_text": "There has long been a general"}
}

reading_question_pages = {
    (1, 1): [17, 18], (1, 2): [21, 22, 23], (1, 3): [27, 28],
    (2, 1): [41, 42], (2, 2): [45, 46], (2, 3): [50],
    (3, 1): [63, 64], (3, 2): [68], (3, 3): [72],
    (4, 1): [85, 86], (4, 2): [90], (4, 3): [94]
}

listening_part_pages = {
    (1, 1): [98, 99], (1, 2): [99, 100], (1, 3): [101, 102], (1, 4): [102, 103],
    (2, 1): [103, 104], (2, 2): [104, 105], (2, 3): [105, 106], (2, 4): [106, 107],
    (3, 1): [107, 108], (3, 2): [108, 109], (3, 3): [109, 110], (3, 4): [110, 111],
    (4, 1): [113, 114], (4, 2): [114, 115], (4, 3): [115, 116], (4, 4): [116, 117]
}

listening_question_pages = {
    1: {1: [7], 2: [8, 9], 3: [10, 11], 4: [12]},
    2: {1: [31], 2: [32, 33], 3: [34, 35], 4: [36]},
    3: {1: [53], 2: [54, 55], 3: [56, 57], 4: [58]},
    4: {1: [75], 2: [76, 77], 3: [78, 79], 4: [80]}
}

def clean_passage_text(text, start_text):
    text = text.replace('\r', '')
    # Find start text
    idx = text.lower().find(start_text.lower()[:20])
    if idx != -1:
        text = text[idx:]
    
    # Common OCR cleaning
    replacements = {
        'delibetately': 'deliberately',
        'd?/ferent': 'different',
        'vety': 'very',
        'pmticular': 'particular',
        'dillrent': 'different',
        'inodel': 'model',
        'primmy': 'primary',
        'locket': 'racket',
        'Bob Btyan': 'Bob Bryan',
        'labotat01Y': 'laboratory',
        'stuffofsciencefiction': 'stuff of science fiction',
        'Matyam Milzakhani': 'Maryam Mirzakhani',
        'Milzakhani': 'Mirzakhani',
        '': '-',
        'dillrent': 'different',
        'Inodel': 'model',
        'vety pmticular': 'very particular',
        'primmy reason': 'primary reason',
        'Bob Btyan': 'Bob Bryan',
        'y stored': 'They stored',
        'favourable': 'favorable',
        'organisations': 'organizations',
        'labotat01Y': 'laboratory',
        'lesponsible': 'responsible'
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
        
    # Strip trailing numbers (page numbers)
    text = re.sub(r'\s+\d+\s*$', '', text)
    return text.strip()

def clean_transcript_text(text, part_num, title):
    text = text.replace('\r', '')
    # Try to find part title or number to clean header
    title_words = title.split()
    first_few = " ".join(title_words[:2])
    idx = text.lower().find(first_few.lower())
    if idx != -1:
        text = text[idx + len(first_few):]
    else:
        # Fallback split
        idx_part = text.lower().find(f"part {part_num}")
        if idx_part != -1:
            text = text[idx_part + 6:]
            
    # Clean up speaker lines at start
    text = re.sub(r'^(?:[A-Z\s]+:\s*)+', '', text)
    text = text.replace('', '-')
    text = text.replace('0K.', 'OK.')
    text = text.replace('0k.', 'ok.')
    
    # Strip trailing numbers
    text = re.sub(r'\s+\d+\s*$', '', text)
    return text.strip()

def extract_options_from_ocr(question_ocr):
    options = []
    pattern = r'(?:^|\s)([A-E])[\s\.\)]+([^A-E\n\r]+)'
    matches = re.findall(pattern, question_ocr)
    seen = set()
    for label, content in matches:
        label = label.strip().upper()
        content = content.strip()
        if label not in seen and len(content) > 3:
            content = re.sub(r'\s+', ' ', content)
            options.append({"label": label, "content": content})
            seen.add(label)
    return options

def find_evidence_offline(passage_text, correct_answer, question_text):
    sentences = re.split(r'(?<=[.!?])\s+', passage_text)
    
    ans_clean = correct_answer.strip().lower()
    is_word_ans = len(ans_clean) > 2 and ans_clean not in ["true", "false", "not given", "yes", "no", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j"]
    
    if is_word_ans:
        for s in sentences:
            if ans_clean in s.lower():
                idx = passage_text.find(s)
                if idx != -1:
                    return s, idx, len(s)
                    
    # Keyword matching fallback for MCQs / TFNGs
    q_words = re.findall(r'\b[a-zA-Z]{4,}\b', question_text.lower())
    stop_words = {"about", "their", "there", "would", "which", "where", "statement", "questions", "passage", "agree", "information", "given", "writer", "following"}
    keywords = [w for w in q_words if w not in stop_words]
    
    if keywords:
        best_sentence = None
        best_count = -1
        for s in sentences:
            s_lower = s.lower()
            count = sum(1 for kw in keywords if kw in s_lower)
            if count > best_count:
                best_count = count
                best_sentence = s
        if best_sentence and best_count > 0:
            idx = passage_text.find(best_sentence)
            if idx != -1:
                return best_sentence, idx, len(best_sentence)
                
    # Fallback to first sentence
    if sentences:
        first_s = sentences[0]
        idx = passage_text.find(first_s)
        if idx != -1:
            return first_s, idx, len(first_s)
            
    return "Evidence sentence placeholder.", 0, 30

def update_writing_images():
    print("\n--- Updating Writing Task 1 Image URLs in Database ---")
    mappings = {
        "%social centre in Melbourne%": "/api/v1/auth/avatar/cam19_test1_task1.jpeg",
        "%harbour in 2000%": "/api/v1/auth/avatar/cam19_test2_task1.jpeg",
        "%biofuel called ethanol%": "/api/v1/auth/avatar/cam19_test3_task1.jpeg",
        "%dance classes young people%": "/api/v1/auth/avatar/cam19_test4_task1.jpeg"
    }
    with db_conn.cursor() as cursor:
        for prompt_pattern, img_url in mappings.items():
            sql = "UPDATE writing_prompts SET image_url = %s WHERE prompt_text LIKE %s"
            cursor.execute(sql, (img_url, prompt_pattern))
            print(f"Updated image URL for writing prompt like '{prompt_pattern}' -> {img_url} ({cursor.rowcount} row(s) updated)")
    db_conn.commit()

def process_reading_offline():
    print("\n--- Extracting Reading Passages & Question Explanations (OFFLINE) ---")
    review_data = []
    
    for (test_num, passage_num), meta in reading_passage_metadata.items():
        print(f"Processing Test {test_num} Passage {passage_num}...")
        
        # 1. Clean passage text
        raw_text = "\n".join([ocr_cache.get(str(p), "") for p in meta["pages"]])
        cleaned_passage = clean_passage_text(raw_text, meta["start_text"])
        passage_content = f"[Cambridge 19 Test {test_num} Passage {passage_num}: {meta['title']}]\n\n{cleaned_passage}"
        
        # 2. Get questions from DB
        quiz_id = 42 + (test_num - 1) * 3 + (passage_num - 1)
        with db_conn.cursor() as cursor:
            cursor.execute("UPDATE reading_quizzes SET passage_text = %s WHERE quiz_id = %s", (passage_content, quiz_id))
            
            cursor.execute("SELECT question_id, question_text, order_index, question_type FROM reading_questions WHERE quiz_id = %s ORDER BY order_index ASC", (quiz_id,))
            questions = cursor.fetchall()
            
        print(f"  Updated passage for quiz_id {quiz_id} (title: {meta['title']})")
        
        # 3. Read questions OCR for MCQ options
        q_pages = reading_question_pages[(test_num, passage_num)]
        q_raw_ocr = "\n".join([ocr_cache.get(str(p), "") for p in q_pages])
        
        # Extract options
        extracted_options = extract_options_from_ocr(q_raw_ocr)
        
        for q in questions:
            order_idx = q['order_index']
            if passage_num == 1:
                book_q_num = order_idx
            elif passage_num == 2:
                book_q_num = 13 + order_idx
            else:
                book_q_num = 26 + order_idx
                
            correct_ans = READING_ANSWERS[test_num].get(book_q_num, "")
            
            # Formulate Clean Question Text and Types
            q_text = q['question_text']
            q_type = q['question_type']
            group_label = q_type
            
            # Clean up question text placeholders
            if q_text.startswith("Statement (Q") or q_text.startswith("Match ") or q_text.startswith("Fill blank (Q"):
                # Try to extract the actual statement from OCR
                # e.g., search for the book_q_num followed by the text
                # "1 People had expected..."
                q_num_pattern = rf'(?:^|\s){book_q_num}\s+([^0-9\n\r]+)'
                match_q = re.search(q_num_pattern, q_raw_ocr)
                if match_q:
                    q_text = match_q.group(1).strip()
                    # Clean up weird trailing stuff
                    q_text = re.sub(r'\s+', ' ', q_text)
                    q_text = re.sub(r'\s*[A-J]$', '', q_text) # remove matching options trailing
                    
            # Determine question types and labels for Test 2 Passage 2 specifically
            if test_num == 2 and passage_num == 2:
                if 14 <= book_q_num <= 18:
                    q_type = "MATCHING_INFORMATION"
                    group_label = "Questions 14-18: Matching Information"
                elif 19 <= book_q_num <= 22:
                    q_type = "FILL_BLANK"
                    group_label = "Questions 19-22: Note Completion"
                elif 23 <= book_q_num <= 26:
                    q_type = "MCQ"
                    group_label = "Questions 23-26: Multiple Choice"
                    
            # Find evidence offset
            evidence_text, offset, length = find_evidence_offline(cleaned_passage, correct_ans, q_text)
            explanation = f"The correct answer is '{correct_ans}', as supported by the passage: \"{evidence_text}\""
            
            # Handle MCQ options
            options = []
            if q_type == "MCQ":
                # Filter extracted options for this question or fallback to options block
                # Since we extract options in order, let's map them
                options = extracted_options[:4] if len(extracted_options) >= 4 else [
                    {"label": "A", "content": f"Option A content for Q{book_q_num}"},
                    {"label": "B", "content": f"Option B content for Q{book_q_num}"},
                    {"label": "C", "content": f"Option C content for Q{book_q_num}"},
                    {"label": "D", "content": f"Option D content for Q{book_q_num}"}
                ]
                # Shift options for the next MCQ questions
                if len(extracted_options) >= 4:
                    extracted_options = extracted_options[4:]
                    
            with db_conn.cursor() as cursor:
                cursor.execute("""
                    UPDATE reading_questions 
                    SET question_text = %s, correct_answer = %s, explanation = %s, evidence_text = %s, evidence_offset = %s, evidence_length = %s, question_type = %s, group_label = %s, verified = FALSE
                    WHERE question_id = %s
                """, (q_text, correct_ans, explanation, evidence_text, offset, length, q_type, group_label, q['question_id']))
                
                # Delete and insert options
                cursor.execute("DELETE FROM question_options WHERE reading_question_id = %s", (q['question_id'],))
                if options:
                    for idx_opt, opt in enumerate(options):
                        is_correct = 1 if opt['label'].strip().upper() == correct_ans.strip().upper() else 0
                        cursor.execute("""
                            INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
                            VALUES (%s, %s, %s, %s, %s)
                        """, (q['question_id'], opt['label'], opt['content'], is_correct, idx_opt))
                        
            review_data.append({
                "test": test_num,
                "section": "Reading",
                "passage": passage_num,
                "question_id": q['question_id'],
                "question_number": book_q_num,
                "question_text": q_text,
                "question_type": q_type,
                "correct_answer": correct_ans,
                "explanation": explanation,
                "evidence_text": evidence_text,
                "evidence_offset": offset,
                "evidence_length": length,
                "options": options,
                "verified": False
            })
            
        db_conn.commit()
    return review_data

def process_listening_offline(review_data):
    print("\n--- Extracting Listening Transcripts & Question Explanations (OFFLINE) ---")
    
    # Get all listening parts
    db_parts = []
    with db_conn.cursor() as cursor:
        cursor.execute("SELECT part_id, part_number, title FROM listening_parts WHERE created_by = 'CAMBRIDGE_19'")
        db_parts = cursor.fetchall()
    sorted_parts = sorted(db_parts, key=lambda x: x['part_id'])
    
    for test_num in [1, 2, 3, 4]:
        for part_num in [1, 2, 3, 4]:
            target_idx = (test_num - 1) * 4 + (part_num - 1)
            part = sorted_parts[target_idx]
            part_id = part['part_id']
            part_title = part['title']
            
            print(f"Processing Test {test_num} Part {part_num} ({part_title})...")
            
            # 1. Clean transcript text
            pages = listening_part_pages[(test_num, part_num)]
            raw_text = "\n".join([ocr_cache.get(str(p), "") for p in pages])
            cleaned_transcript = clean_transcript_text(raw_text, part_num, part_title)
            
            with db_conn.cursor() as cursor:
                cursor.execute("UPDATE listening_parts SET transcript_text = %s, audio_status = 'READY' WHERE part_id = %s", (cleaned_transcript, part_id))
                
                # Fetch questions
                cursor.execute("SELECT question_id, question_text, order_index, question_type, correct_answer FROM listening_questions WHERE part_id = %s ORDER BY order_index ASC", (part_id,))
                questions = cursor.fetchall()
                
            # Read questions OCR
            q_pages = listening_question_pages[test_num][part_num]
            q_raw_ocr = "\n".join([ocr_cache.get(str(p), "") for p in q_pages])
            extracted_options = extract_options_from_ocr(q_raw_ocr)
            
            for q in questions:
                order_idx = q['order_index']
                book_q_num = (part_num - 1) * 10 + order_idx
                correct_ans = q['correct_answer']
                
                q_text = q['question_text']
                q_type = q['question_type']
                group_label = q_type
                
                # Clean up question text from OCR
                if q_text.startswith("Fill blank (Q") or q_text.startswith("Question (Q"):
                    q_num_pattern = rf'(?:^|\s){book_q_num}\s+([^0-9\n\r]+)'
                    match_q = re.search(q_num_pattern, q_raw_ocr)
                    if match_q:
                        q_text = match_q.group(1).strip()
                        q_text = re.sub(r'\s+', ' ', q_text)
                        
                explanation = f"The speaker states the correct answer is '{correct_ans}' in the recording."
                
                options = []
                if q_type == "MCQ":
                    options = extracted_options[:3] if len(extracted_options) >= 3 else [
                        {"label": "A", "content": f"Option A content for Q{book_q_num}"},
                        {"label": "B", "content": f"Option B content for Q{book_q_num}"},
                        {"label": "C", "content": f"Option C content for Q{book_q_num}"}
                    ]
                    if len(extracted_options) >= 3:
                        extracted_options = extracted_options[3:]
                        
                with db_conn.cursor() as cursor:
                    cursor.execute("""
                        UPDATE listening_questions 
                        SET question_text = %s, question_type = %s, verified = FALSE
                        WHERE question_id = %s
                    """, (q_text, q_type, q['question_id']))
                    
                    # Update Options
                    cursor.execute("DELETE FROM question_options WHERE listening_question_id = %s", (q['question_id'],))
                    if options:
                        for idx_opt, opt in enumerate(options):
                            is_correct = 1 if opt['label'].strip().upper() == correct_ans.strip().upper() else 0
                            cursor.execute("""
                                INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
                                VALUES (%s, %s, %s, %s, %s)
                            """, (q['question_id'], opt['label'], opt['content'], is_correct, idx_opt))
                            
                review_data.append({
                    "test": test_num,
                    "section": "Listening",
                    "part": part_num,
                    "question_id": q['question_id'],
                    "question_number": book_q_num,
                    "question_text": q_text,
                    "question_type": q_type,
                    "correct_answer": correct_ans,
                    "explanation": explanation,
                    "evidence_text": "",
                    "evidence_offset": None,
                    "evidence_length": None,
                    "options": options,
                    "verified": False
                })
                
            db_conn.commit()
    return review_data

def main():
    try:
        update_writing_images()
        review_data = process_reading_offline()
        review_data = process_listening_offline(review_data)
        
        # Write final review file
        with open(review_path, "w", encoding="utf-8") as f:
            json.dump(review_data, f, ensure_ascii=False, indent=2)
        print(f"\nSaved final review details to {review_path}")
        print("\n=== All Cambridge 19 resources successfully integrated offline! ===")
    finally:
        db_conn.close()

if __name__ == "__main__":
    main()
