import pymysql
import requests
import json
import subprocess

# Connect to database
db_conn = pymysql.connect(
    host='localhost',
    user='root',
    password='smartprep_root_2024',
    database='ielts_smartprep',
    cursorclass=pymysql.cursors.DictCursor
)

def verify_writing():
    print("\n--- Verifying Writing Tasks ---")
    errors = 0
    with db_conn.cursor() as cursor:
        # We find the 4 Cambridge 19 Test Writing Task 1 prompts
        cursor.execute("""
            SELECT prompt_id, prompt_text, image_url 
            FROM writing_prompts 
            WHERE essay_type IN ('LINE_GRAPH', 'MAP', 'DIAGRAM', 'PIE_CHART')
              AND (prompt_text LIKE '%Melbourn%' OR prompt_text LIKE '%harbour%' OR prompt_text LIKE '%ethanol%' OR prompt_text LIKE '%dance%')
        """)
        rows = cursor.fetchall()
        print(f"Found {len(rows)} Task 1 prompts for Cambridge 19.")
        
        if len(rows) != 4:
            print(f"[FAIL] Expected 4 Writing Task 1 prompts, found {len(rows)}")
            errors += 1
            
        for r in rows:
            p_id = r['prompt_id']
            img_url = r['image_url']
            print(f"  Prompt ID {p_id}: image_url = {img_url}")
            
            if not img_url:
                print(f"  [FAIL] Image URL is null for prompt ID {p_id}!")
                errors += 1
                continue
                
            if not img_url.startswith("/api/v1/auth/avatar/"):
                print(f"  [FAIL] Image URL format incorrect for prompt ID {p_id}: {img_url}")
                errors += 1
                continue
                
            # Verify file exists in MinIO
            key = img_url.split("/")[-1]
            # Check using mc ls local/listening-audio/{key}
            res = subprocess.run(f"docker exec ielts-smartprep-minio-1 mc ls local/listening-audio/{key}", shell=True, capture_output=True, text=True)
            if res.returncode != 0:
                print(f"  [FAIL] Image file {key} not found in MinIO bucket 'listening-audio'!")
                errors += 1
            else:
                print(f"  [PASS] Image file {key} exists in MinIO.")
                
    return errors

def verify_listening():
    print("\n--- Verifying Listening Parts & Audios ---")
    errors = 0
    with db_conn.cursor() as cursor:
        cursor.execute("SELECT part_id, title, audio_url, audio_status, CHAR_LENGTH(transcript_text) as trans_len FROM listening_parts WHERE created_by = 'CAMBRIDGE_19'")
        rows = cursor.fetchall()
        print(f"Found {len(rows)} Listening parts for Cambridge 19.")
        
        if len(rows) != 16:
            print(f"[FAIL] Expected 16 Listening parts, found {len(rows)}")
            errors += 1
            
        for r in rows:
            title = r['title']
            audio_url = r['audio_url']
            status = r['audio_status']
            trans_len = r['trans_len'] or 0
            
            print(f"  Part '{title}': status={status}, transcript_len={trans_len}")
            
            if status != 'READY':
                print(f"  [FAIL] Audio status is not READY: {status}")
                errors += 1
                
            if trans_len < 300:
                print(f"  [FAIL] Transcript is too short or empty: {trans_len} chars")
                errors += 1
                
            # Check if file exists in MinIO
            # audio_url is like /api/v1/listening/audio/cam19_test1_part1.mp3
            key = audio_url.split("/")[-1]
            res = subprocess.run(f"docker exec ielts-smartprep-minio-1 mc ls local/listening-audio/{key}", shell=True, capture_output=True, text=True)
            if res.returncode != 0:
                print(f"  [FAIL] Audio file {key} not found in MinIO bucket 'listening-audio'!")
                errors += 1
            else:
                print(f"  [PASS] Audio file {key} exists in MinIO.")
                
    return errors

def verify_reading():
    print("\n--- Verifying Reading Passages & Questions ---")
    errors = 0
    with db_conn.cursor() as cursor:
        # Check passages
        cursor.execute("SELECT quiz_id, difficulty, CHAR_LENGTH(passage_text) as text_len FROM reading_quizzes WHERE passage_text LIKE '%Cambridge 19%' AND is_template=TRUE")
        rows = cursor.fetchall()
        print(f"Found {len(rows)} Reading quizzes for Cambridge 19.")
        
        if len(rows) != 12:
            print(f"[FAIL] Expected 12 Reading quizzes, found {len(rows)}")
            errors += 1
            
        for r in rows:
            q_id = r['quiz_id']
            text_len = r['text_len'] or 0
            print(f"  Quiz ID {q_id} (difficulty: {r['difficulty']}): passage text length = {text_len}")
            
            if text_len < 1000:
                print(f"  [FAIL] Passage text is too short or placeholder: {text_len} chars")
                errors += 1
                
            # Check questions under this quiz
            cursor.execute("""
                SELECT question_id, order_index, question_type, correct_answer, 
                       explanation, evidence_text, evidence_offset, evidence_length 
                FROM reading_questions 
                WHERE quiz_id = %s
                ORDER BY order_index ASC
            """, (q_id,))
            questions = cursor.fetchall()
            print(f"    Quiz has {len(questions)} questions.")
            
            for q in questions:
                q_id_inner = q['question_id']
                ans = q['correct_answer']
                expl = q['explanation']
                ev_text = q['evidence_text']
                ev_off = q['evidence_offset']
                ev_len = q['evidence_length']
                
                if not ans or ans == '__(answer)':
                    print(f"    [FAIL] Question order {q['order_index']} has empty/placeholder answer: '{ans}'")
                    errors += 1
                    
                if not expl or len(expl) < 20 or "placeholder" in expl.lower():
                    print(f"    [FAIL] Question order {q['order_index']} has invalid/placeholder explanation: '{expl}'")
                    errors += 1
                    
                if not ev_text:
                    print(f"    [FAIL] Question order {q['order_index']} has empty evidence text")
                    errors += 1
                    
                if ev_off is None or ev_len is None or (ev_off == 0 and ev_len == 0 and not ev_text):
                    print(f"    [FAIL] Question order {q['order_index']} has invalid evidence offset/length: offset={ev_off}, length={ev_len}")
                    errors += 1
                    
    return errors

def main():
    print("====================================================")
    print("          CAMBRIDGE 19 QA VERIFICATION RUN           ")
    print("====================================================")
    total_errors = 0
    try:
        total_errors += verify_writing()
        total_errors += verify_listening()
        total_errors += verify_reading()
        
        print("\n=================== SUMMARY ===================")
        if total_errors == 0:
            print("  [SUCCESS] All Cambridge 19 QA checks passed!")
        else:
            print(f"  [FAILURE] Verification completed with {total_errors} error(s).")
    finally:
        db_conn.close()

if __name__ == "__main__":
    main()
