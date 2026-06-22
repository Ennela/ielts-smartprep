import pymysql

connection = pymysql.connect(
    host='localhost',
    user='root',
    password='smartprep_root_2024',
    database='ielts_smartprep',
    cursorclass=pymysql.cursors.DictCursor
)

try:
    with connection.cursor() as cursor:
        # Check reading quizzes with actual content
        cursor.execute("SELECT COUNT(*) as cnt FROM reading_quizzes WHERE passage_text NOT LIKE '[Cambridge 19%' AND is_template = TRUE")
        print("Updated Passages:", cursor.fetchone()['cnt'], "/ 12")
        
        # Check listening parts with transcripts
        cursor.execute("SELECT COUNT(*) as cnt FROM listening_parts WHERE transcript_text IS NOT NULL AND created_by = 'CAMBRIDGE_19'")
        print("Updated Transcripts:", cursor.fetchone()['cnt'], "/ 16")
        
        # Check reading questions with updated answers
        cursor.execute("SELECT COUNT(*) as cnt FROM reading_questions WHERE correct_answer != '__(answer)'")
        print("Updated Question Answers:", cursor.fetchone()['cnt'], "/ 563")
        
        # Check reading questions with explanations
        cursor.execute("SELECT COUNT(*) as cnt FROM reading_questions WHERE explanation IS NOT NULL AND explanation != 'AI explanation placeholder'")
        print("Updated Question Explanations:", cursor.fetchone()['cnt'], "/ 160")
        
finally:
    connection.close()
