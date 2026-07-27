import pymysql

from runtime_config import mysql_connection_config

connection = pymysql.connect(
    **mysql_connection_config(),
    cursorclass=pymysql.cursors.DictCursor
)

try:
    with connection.cursor() as cursor:
        for table in ['reading_quizzes', 'reading_questions', 'listening_parts', 'listening_questions', 'writing_prompts']:
            print(f"\n=== Schema for table: {table} ===")
            cursor.execute(f"DESCRIBE {table}")
            for row in cursor.fetchall():
                print(f"  {row['Field']}: {row['Type']} (Null: {row['Null']}, Key: {row['Key']})")
            
finally:
    connection.close()
