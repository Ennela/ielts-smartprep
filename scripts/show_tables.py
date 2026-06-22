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
        for table in ['mock_tests', 'mock_test_sections', 'mock_test_listening_parts', 'mock_test_reading_quizzes', 'mock_test_writing_prompts']:
            print(f"\n=== Schema for table: {table} ===")
            cursor.execute(f"DESCRIBE {table}")
            for row in cursor.fetchall():
                print(f"  {row['Field']}: {row['Type']} (Null: {row['Null']}, Key: {row['Key']})")
            
finally:
    connection.close()
