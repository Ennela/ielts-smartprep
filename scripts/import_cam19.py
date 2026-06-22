import json
import os
import re
import requests
import pymysql
import time

api_key = "AIzaSyDbeZlxqjdZfzeyyAfLwdora77n5CdLSp0"
gemini_url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key={api_key}"

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

def clean_json_text(text):
    text = text.strip()
    match = re.search(r'(\{.*\}|\[.*\])', text, re.DOTALL)
    if match:
        return match.group(1)
    return text

def call_gemini(system_prompt, user_prompt, response_json=False):
    headers = {"Content-Type": "application/json"}
    payload = {
        "system_instruction": {
            "parts": [{"text": system_prompt}]
        },
        "contents": [{
            "parts": [{"text": user_prompt}]
        }],
        "generationConfig": {
            "temperature": 0.2,
            "topP": 0.95
        }
    }
    if response_json:
        payload["generationConfig"]["responseMimeType"] = "application/json"
        
    for attempt in range(6):
        try:
            response = requests.post(gemini_url, headers=headers, json=payload, timeout=90)
            if response.status_code == 200:
                result = response.json()
                return result['candidates'][0]['content']['parts'][0]['text']
            elif response.status_code == 429 or response.status_code == 503:
                print(f"  [Rate Limit 429/503 Error Details] {response.text}")
                wait_time = (2 ** attempt) * 10 + 15
                print(f"  Sleeping for {wait_time}s before retry...")
                time.sleep(wait_time)
            else:
                print(f"  [API Error {response.status_code}] {response.text}. Sleeping 5s...")
                time.sleep(5)
        except Exception as e:
            print(f"  [Exception] {e}. Sleeping 5s...")
            time.sleep(5)
    raise Exception("Failed to get response from Gemini API after 6 retries.")

# Step 1: Update Writing Task 1 Image URLs in DB
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

# Hardcoded Reading Answer Keys (exactly matching book questions 1-40)
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

reading_passage_pages = {
    (1, 1): [15, 16], (1, 2): [19, 20], (1, 3): [24, 25, 26],
    (2, 1): [39, 40], (2, 2): [43, 44], (2, 3): [47, 48, 49],
    (3, 1): [61, 62], (3, 2): [65, 66, 67], (3, 3): [69, 70, 71, 72],
    (4, 1): [83, 84], (4, 2): [87, 88, 89], (4, 3): [91, 92, 93]
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

# Step 2: Extract Reading Passages, Explanations & Evidence
def process_reading_batched():
    print("\n--- Extracting Reading Passages & Question Explanations (BATCHED) ---")
    review_data = []
    
    for test_num in [1, 2, 3, 4]:
        print(f"\nProcessing Reading Test {test_num}...")
        
        # 1. Gather all raw OCR data for this test's passages
        passage_ocr_parts = []
        for passage_num in [1, 2, 3]:
            pages = reading_passage_pages[(test_num, passage_num)]
            text = "\n".join([ocr_cache.get(str(p), "") for p in pages])
            passage_ocr_parts.append(f"--- PASSAGE {passage_num} OCR TEXT (Pages {pages}) ---\n{text}")
        passage_ocr_full = "\n\n".join(passage_ocr_parts)

        # 2. Gather all raw OCR data for this test's questions
        question_ocr_parts = []
        for passage_num in [1, 2, 3]:
            pages = reading_question_pages[(test_num, passage_num)]
            text = "\n".join([ocr_cache.get(str(p), "") for p in pages])
            question_ocr_parts.append(f"--- PASSAGE {passage_num} QUESTIONS OCR TEXT (Pages {pages}) ---\n{text}")
        question_ocr_full = "\n\n".join(question_ocr_parts)

        # 3. Create list of questions to send to Gemini
        quiz_ids = [
            42 + (test_num - 1) * 3,
            43 + (test_num - 1) * 3,
            44 + (test_num - 1) * 3
        ]
        
        db_questions = []
        questions_list = []
        
        for idx, quiz_id in enumerate(quiz_ids):
            passage_num = idx + 1
            with db_conn.cursor() as cursor:
                cursor.execute("SELECT question_id, question_text, order_index, question_type FROM reading_questions WHERE quiz_id = %s ORDER BY order_index ASC", (quiz_id,))
                rows = cursor.fetchall()
                for r in rows:
                    if passage_num == 1:
                        book_q_num = r['order_index']
                    elif passage_num == 2:
                        book_q_num = 13 + r['order_index']
                    else:
                        book_q_num = 26 + r['order_index']
                        
                    correct_ans = READING_ANSWERS[test_num].get(book_q_num, "")
                    db_questions.append({
                        "quiz_id": quiz_id,
                        "passage_num": passage_num,
                        "question_id": r['question_id'],
                        "order_index": r['order_index'],
                        "book_q_num": book_q_num,
                        "question_type": r['question_type'],
                        "correct_answer": correct_ans,
                        "question_text": r['question_text']
                    })
                    questions_list.append(f"- Q{book_q_num} (type: {r['question_type']}) -> ANSWER: {correct_ans}")

        questions_block = "\n".join(questions_list)

        system_prompt = (
            "You are a professional IELTS reading instructor and content editor.\n"
            "Given the raw OCR text of all three reading passages, the raw OCR text of their questions, and the list of questions with correct answers for this test, you will:\n"
            "1. Extract and clean the reading passage text for each passage (title, paragraphs). Remove headers, footers, page numbers, and questions.\n"
            "2. Extract the actual question texts (including details, statements, or stems) verbatim from the questions page.\n"
            "3. For MCQ questions, extract the A, B, C, D options.\n"
            "4. Determine the correct question_type and group_label for each question. The type MUST be one of: 'MCQ', 'TFNG', 'FILL_BLANK', 'YNNG', 'SENTENCE_COMPLETION', 'SUMMARY_COMPLETION', 'MATCHING_HEADINGS', 'MATCHING_INFORMATION', 'MATCHING_FEATURES', 'MATCHING_SENTENCE_ENDINGS', 'DIAGRAM_LABEL_COMPLETION', 'SHORT_ANSWER'.\n"
            "5. Generate a concise explanation (2-3 sentences) and extract the exact verbatim evidence sentence from the passage text for each question.\n"
            "Return a JSON object with this exact schema:\n"
            "{\n"
            "  \"passages\": [\n"
            "    {\n"
            "      \"passage_num\": 1,\n"
            "      \"title\": \"Cleaned Passage Title\",\n"
            "      \"passage_text\": \"Full cleaned passage text...\"\n"
            "    },\n"
            "    {\n"
            "      \"passage_num\": 2,\n"
            "      \"title\": \"Cleaned Passage Title\",\n"
            "      \"passage_text\": \"Full cleaned passage text...\"\n"
            "    },\n"
            "    {\n"
            "      \"passage_num\": 3,\n"
            "      \"title\": \"Cleaned Passage Title\",\n"
            "      \"passage_text\": \"Full cleaned passage text...\"\n"
            "    }\n"
            "  ],\n"
            "  \"questions\": [\n"
            "    {\n"
            "      \"book_q_num\": 1,\n"
            "      \"question_type\": \"TFNG\",\n"
            "      \"group_label\": \"Questions 1-7: True/False/Not Given\",\n"
            "      \"question_text\": \"Actual clean question stem...\",\n"
            "      \"explanation\": \"concise explanation...\",\n"
            "      \"evidence_sentence\": \"exact verbatim sentence from the passage text\",\n"
            "      \"options\": [\n"
            "        { \"label\": \"A\", \"content\": \"text of option A\" },\n"
            "        { \"label\": \"B\", \"content\": \"text of option B\" }\n"
            "      ]\n"
            "    }\n"
            "  ]\n"
            "}"
        )

        user_prompt = f"PASSAGES OCR TEXT:\n{passage_ocr_full}\n\nQUESTIONS OCR TEXT:\n{question_ocr_full}\n\nQUESTIONS LIST:\n{questions_block}"

        res_text = call_gemini(system_prompt, user_prompt, response_json=True)
        res_text = clean_json_text(res_text)
        data = json.loads(res_text)

        # Update DB Passages
        passage_texts = {}
        for p in data['passages']:
            p_num = p['passage_num']
            passage_texts[p_num] = p['passage_text']
            quiz_id = quiz_ids[p_num - 1]
            with db_conn.cursor() as cursor:
                cursor.execute("UPDATE reading_quizzes SET passage_text = %s, topic = %s WHERE quiz_id = %s", (p['passage_text'], p['title'], quiz_id))
                print(f"  Updated Reading Passage text for quiz_id {quiz_id} (title: {p['title']})")

        # Update DB Questions & Options
        q_map = {item['book_q_num']: item for item in data['questions']}
        for q in db_questions:
            book_q_num = q['book_q_num']
            quiz_id = q['quiz_id']
            p_num = q['passage_num']
            correct_ans = q['correct_answer']
            
            explanation = "AI explanation placeholder"
            evidence_text = ""
            offset = None
            length = None
            actual_q_text = q['question_text']
            options = []
            q_type = q['question_type']
            group_label = "Questions"

            q_ai = q_map.get(book_q_num)
            if q_ai:
                actual_q_text = q_ai.get('question_text', q['question_text'])
                explanation = q_ai['explanation']
                evidence_sentence = q_ai.get('evidence_sentence', '').strip()
                options = q_ai.get('options', [])
                q_type = q_ai.get('question_type', q['question_type'])
                group_label = q_ai.get('group_label', 'Questions')
                
                if (evidence_sentence.startswith('"') and evidence_sentence.endswith('"')) or (evidence_sentence.startswith("'") and evidence_sentence.endswith("'")):
                    evidence_sentence = evidence_sentence[1:-1].strip()
                    
                # Find offset in passage text
                full_passage_text = passage_texts.get(p_num, "")
                idx = full_passage_text.find(evidence_sentence)
                if idx != -1:
                    evidence_text = evidence_sentence
                    offset = idx
                    length = len(evidence_sentence)
                else:
                    idx_lower = full_passage_text.lower().find(evidence_sentence.lower())
                    if idx_lower != -1:
                        evidence_text = full_passage_text[idx_lower : idx_lower + len(evidence_sentence)]
                        offset = idx_lower
                        length = len(evidence_sentence)
                    else:
                        # Fallback search using correct answer
                        idx_ans = full_passage_text.lower().find(correct_ans.lower())
                        if idx_ans != -1 and len(correct_ans) > 2:
                            # Extract sentence around answer
                            start_idx = max(0, idx_ans - 60)
                            end_idx = min(len(full_passage_text), idx_ans + len(correct_ans) + 60)
                            snippet = full_passage_text[start_idx:end_idx]
                            # Try to align to sentence boundaries
                            sentences = re.split(r'(?<=[.!?])\s+', snippet)
                            for s in sentences:
                                if correct_ans.lower() in s.lower():
                                    evidence_text = s.strip()
                                    idx_exact = full_passage_text.find(evidence_text)
                                    if idx_exact != -1:
                                        offset = idx_exact
                                        length = len(evidence_text)
                                    break

            with db_conn.cursor() as cursor:
                cursor.execute("""
                    UPDATE reading_questions 
                    SET question_text = %s, correct_answer = %s, explanation = %s, evidence_text = %s, evidence_offset = %s, evidence_length = %s, question_type = %s, group_label = %s, verified = FALSE
                    WHERE question_id = %s
                """, (actual_q_text, correct_ans, explanation, evidence_text, offset, length, q_type, group_label, q['question_id']))
                
                # Update Options
                cursor.execute("DELETE FROM question_options WHERE reading_question_id = %s", (q['question_id'],))
                if options:
                    print(f"    Inserting {len(options)} options for MCQ question ID {q['question_id']}...")
                    for idx_opt, opt in enumerate(options):
                        is_correct = 1 if opt['label'].strip().upper() == correct_ans.strip().upper() else 0
                        cursor.execute("""
                            INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
                            VALUES (%s, %s, %s, %s, %s)
                        """, (q['question_id'], opt['label'], opt['content'], is_correct, idx_opt))

            review_data.append({
                "test": test_num,
                "section": "Reading",
                "passage": p_num,
                "question_id": q['question_id'],
                "question_number": book_q_num,
                "question_text": actual_q_text,
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
        print(f"Test {test_num} Reading integration complete.")
        time.sleep(5)
        
    return review_data

# Step 3: Extract Listening Transcripts & Question Explanations
def process_listening_batched(review_data):
    print("\n--- Extracting Listening Transcripts & Question Explanations (BATCHED) ---")
    
    # Get all listening parts
    db_parts = []
    with db_conn.cursor() as cursor:
        cursor.execute("SELECT part_id, part_number, title FROM listening_parts WHERE created_by = 'CAMBRIDGE_19'")
        db_parts = cursor.fetchall()
    sorted_parts = sorted(db_parts, key=lambda x: x['part_id'])
    
    for test_num in [1, 2, 3, 4]:
        print(f"\nProcessing Listening Test {test_num}...")
        
        # 1. Gather all raw OCR data for this test's transcripts
        part_ocr_parts = []
        for part_num in [1, 2, 3, 4]:
            pages = listening_part_pages[(test_num, part_num)]
            text = "\n".join([ocr_cache.get(str(p), "") for p in pages])
            part_ocr_parts.append(f"--- PART {part_num} TRANSCRIPT OCR TEXT (Pages {pages}) ---\n{text}")
        part_ocr_full = "\n\n".join(part_ocr_parts)

        # 2. Gather all raw OCR data for this test's questions
        question_ocr_parts = []
        for part_num in [1, 2, 3, 4]:
            pages = listening_question_pages[test_num][part_num]
            text = "\n".join([ocr_cache.get(str(p), "") for p in pages])
            question_ocr_parts.append(f"--- PART {part_num} QUESTIONS OCR TEXT (Pages {pages}) ---\n{text}")
        question_ocr_full = "\n\n".join(question_ocr_parts)

        # 3. Get all questions and answers from database for this test's parts
        part_ids = [
            1 + (test_num - 1) * 4,
            2 + (test_num - 1) * 4,
            3 + (test_num - 1) * 4,
            4 + (test_num - 1) * 4
        ]
        
        db_questions = []
        questions_list = []
        
        for idx, part_id in enumerate(part_ids):
            part_num = idx + 1
            # Fetch target part from database to map correctly
            target_idx = (test_num - 1) * 4 + (part_num - 1)
            part = sorted_parts[target_idx]
            
            with db_conn.cursor() as cursor:
                cursor.execute("SELECT question_id, question_text, order_index, question_type, correct_answer FROM listening_questions WHERE part_id = %s ORDER BY order_index ASC", (part['part_id'],))
                rows = cursor.fetchall()
                for r in rows:
                    book_q_num = (part_num - 1) * 10 + r['order_index']
                    db_questions.append({
                        "part_id": part['part_id'],
                        "part_num": part_num,
                        "question_id": r['question_id'],
                        "order_index": r['order_index'],
                        "book_q_num": book_q_num,
                        "question_type": r['question_type'],
                        "correct_answer": r['correct_answer'],
                        "question_text": r['question_text']
                    })
                    questions_list.append(f"- Q{book_q_num} (type: {r['question_type']}) -> ANSWER: {r['correct_answer']}")

        questions_block = "\n".join(questions_list)

        system_prompt = (
            "You are a professional IELTS listening instructor.\n"
            "Given the raw OCR text of all four listening parts' transcripts, their questions OCR, and the correct answers for all 40 questions of this test, you will:\n"
            "1. Extract and clean the transcript for each part. Format with capitalized speaker names (e.g. 'JOHN: ...'). Remove page numbers, headers, and noise.\n"
            "2. Extract the actual question texts (including details, statements, or stems) verbatim from the questions page.\n"
            "3. For MCQ questions, extract the A, B, C, D choices.\n"
            "4. Determine the correct question_type and group_label for each question. The type MUST be one of: 'MCQ', 'TFNG', 'FILL_BLANK', 'YNNG', 'SENTENCE_COMPLETION', 'SUMMARY_COMPLETION', 'MATCHING_HEADINGS', 'MATCHING_INFORMATION', 'MATCHING_FEATURES', 'MATCHING_SENTENCE_ENDINGS', 'DIAGRAM_LABEL_COMPLETION', 'SHORT_ANSWER'.\n"
            "5. Generate a concise explanation (2-3 sentences) explaining why each answer is correct based on the transcript.\n"
            "Return a JSON object with this exact schema:\n"
            "{\n"
            "  \"parts\": [\n"
            "    {\n"
            "      \"part_num\": 1,\n"
            "      \"transcript_text\": \"Full cleaned transcript...\"\n"
            "    },\n"
            "    {\n"
            "      \"part_num\": 2,\n"
            "      \"transcript_text\": \"Full cleaned transcript...\"\n"
            "    },\n"
            "    {\n"
            "      \"part_num\": 3,\n"
            "      \"transcript_text\": \"Full cleaned transcript...\"\n"
            "    },\n"
            "    {\n"
            "      \"part_num\": 4,\n"
            "      \"transcript_text\": \"Full cleaned transcript...\"\n"
            "    }\n"
            "  ],\n"
            "  \"questions\": [\n"
            "    {\n"
            "      \"book_q_num\": 1,\n"
            "      \"question_type\": \"FILL_BLANK\",\n"
            "      \"group_label\": \"Questions 1-10: Note Completion\",\n"
            "      \"question_text\": \"Actual clean question stem...\",\n"
            "      \"explanation\": \"concise explanation...\",\n"
            "      \"options\": [\n"
            "        { \"label\": \"A\", \"content\": \"text of option A\" },\n"
            "        { \"label\": \"B\", \"content\": \"text of option B\" }\n"
            "      ]\n"
            "    }\n"
            "  ]\n"
            "}"
        )

        user_prompt = f"TRANSCRIPTS OCR TEXT:\n{part_ocr_full}\n\nQUESTIONS OCR TEXT:\n{question_ocr_full}\n\nQUESTIONS LIST:\n{questions_block}"

        res_text = call_gemini(system_prompt, user_prompt, response_json=True)
        res_text = clean_json_text(res_text)
        data = json.loads(res_text)

        # Update DB Parts
        part_texts = {}
        for p in data['parts']:
            p_num = p['part_num']
            part_texts[p_num] = p['transcript_text']
            part_id = part_ids[p_num - 1]
            with db_conn.cursor() as cursor:
                cursor.execute("UPDATE listening_parts SET transcript_text = %s, audio_status = 'READY' WHERE part_id = %s", (p['transcript_text'], part_id))
                print(f"  Updated Listening transcript for part_id {part_id}")

        # Update DB Questions & Options
        q_map = {item['book_q_num']: item for item in data['questions']}
        for q in db_questions:
            book_q_num = q['book_q_num']
            part_id = q['part_id']
            p_num = q['part_num']
            correct_ans = q['correct_answer']
            
            explanation = "AI explanation placeholder"
            actual_q_text = q['question_text']
            options = []
            q_type = q['question_type']
            group_label = "Questions"

            q_ai = q_map.get(book_q_num)
            if q_ai:
                actual_q_text = q_ai.get('question_text', q['question_text'])
                explanation = q_ai['explanation']
                options = q_ai.get('options', [])
                q_type = q_ai.get('question_type', q['question_type'])
                group_label = q_ai.get('group_label', 'Questions')

            with db_conn.cursor() as cursor:
                cursor.execute("""
                    UPDATE listening_questions 
                    SET question_text = %s, explanation = %s, question_type = %s, group_label = %s, verified = FALSE
                    WHERE question_id = %s
                """, (actual_q_text, explanation, q_type, group_label, q['question_id']))
                
                # Update Options
                cursor.execute("DELETE FROM question_options WHERE listening_question_id = %s", (q['question_id'],))
                if options:
                    print(f"    Inserting {len(options)} options for MCQ question ID {q['question_id']}...")
                    for idx_opt, opt in enumerate(options):
                        is_correct = 1 if opt['label'].strip().upper() == correct_ans.strip().upper() else 0
                        cursor.execute("""
                            INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
                            VALUES (%s, %s, %s, %s, %s)
                        """, (q['question_id'], opt['label'], opt['content'], is_correct, idx_opt))

            review_data.append({
                "test": test_num,
                "section": "Listening",
                "part": p_num,
                "question_id": q['question_id'],
                "question_number": book_q_num,
                "question_text": actual_q_text,
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
        print(f"Test {test_num} Listening integration complete.")
        time.sleep(5)
        
    return review_data

def main():
    try:
        update_writing_images()
        review_data = process_reading_batched()
        review_data = process_listening_batched(review_data)
        
        # Write final review file
        with open(review_path, "w", encoding="utf-8") as f:
            json.dump(review_data, f, ensure_ascii=False, indent=2)
        print(f"\nSaved final review details to {review_path}")
        print("\n=== All Cambridge 19 resources successfully integrated! ===")
    finally:
        db_conn.close()

if __name__ == "__main__":
    main()
