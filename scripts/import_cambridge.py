import os
import sys
import re
import json
import time
import argparse
import shutil
import pymysql
import fitz  # PyMuPDF
from PIL import Image
import google.generativeai as genai
from dotenv import load_dotenv

# Find and load .env file from project root
dotenv_path = None
curr_dir = os.path.abspath(os.path.dirname(__file__))
while True:
    test_path = os.path.join(curr_dir, '.env')
    if os.path.exists(test_path):
        dotenv_path = test_path
        break
    parent = os.path.dirname(curr_dir)
    if parent == curr_dir:
        break
    curr_dir = parent

if dotenv_path:
    load_dotenv(dotenv_path)
    print(f"Loaded environment from: {dotenv_path}")
else:
    print("Warning: .env file not found. Using system environment variables.")

# Gemini / OpenRouter Config
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")

def configure_ai_client():
    if not GEMINI_API_KEY and not OPENROUTER_API_KEY:
        print("Error: Neither GEMINI_API_KEY nor OPENROUTER_API_KEY is set. Please add one to your .env file.")
        return False

    if GEMINI_API_KEY:
        try:
            genai.configure(api_key=GEMINI_API_KEY)
        except Exception as e:
            print(f"Warning: Failed to configure google-generativeai: {e}")
    return True

def parse_jdbc_mysql_url(jdbc_url):
    if not jdbc_url:
        return {}
    match = re.match(r"^jdbc:mysql://([^:/?#]+)(?::(\d+))?/([^?]+)", jdbc_url.strip())
    if not match:
        return {}
    return {
        "host": match.group(1),
        "port": match.group(2),
        "database": match.group(3)
    }

# Database Config
SPRING_DATASOURCE = parse_jdbc_mysql_url(os.getenv("SPRING_DATASOURCE_URL"))
DB_HOST = os.getenv("MYSQL_HOST") or os.getenv("DB_HOST") or "localhost"
DB_USER = os.getenv("MYSQL_USER") or os.getenv("SPRING_DATASOURCE_USERNAME") or "root"
DB_PASSWORD = (
    os.getenv("MYSQL_PASSWORD")
    or os.getenv("SPRING_DATASOURCE_PASSWORD")
    or os.getenv("MYSQL_ROOT_PASSWORD")
)
DB_NAME = os.getenv("MYSQL_DATABASE") or SPRING_DATASOURCE.get("database") or "ielts_smartprep"
DB_PORT = int(os.getenv("MYSQL_PORT") or os.getenv("DB_PORT") or SPRING_DATASOURCE.get("port") or 3306)

IMPORT_CREATED_BY = "import_script"
REQUIRED_IMPORT_COLUMNS = {
    "mock_tests": {"source", "created_by", "imported_at"},
    "reading_quizzes": {"source", "created_by", "imported_at"},
    "listening_parts": {"source", "imported_at"},
    "writing_prompts": {"source", "created_by", "imported_at"},
}

def has_database_credentials():
    if DB_PASSWORD:
        return True
    print(
        "Error: Database password is not configured. "
        "Set MYSQL_PASSWORD, SPRING_DATASOURCE_PASSWORD, or MYSQL_ROOT_PASSWORD before using --confirm."
    )
    return False

def ensure_required_import_columns(cursor):
    missing = []
    for table_name, required_columns in REQUIRED_IMPORT_COLUMNS.items():
        cursor.execute(f"SHOW COLUMNS FROM {table_name}")
        existing_columns = {row["Field"] for row in cursor.fetchall()}
        table_missing = sorted(required_columns - existing_columns)
        if table_missing:
            missing.append(f"{table_name}: {', '.join(table_missing)}")

    if missing:
        raise RuntimeError(
            "Missing import metadata columns. Run Flyway migration "
            "V38__add_import_metadata_fields.sql before --confirm. Missing: "
            + "; ".join(missing)
        )

def truncate_source(value):
    return value[:100] if value else value

def remove_temp_dir(temp_dir, base_dir):
    temp_abs = os.path.abspath(temp_dir)
    base_abs = os.path.abspath(base_dir)
    try:
        is_under_base = os.path.commonpath([temp_abs, base_abs]) == base_abs
    except ValueError:
        is_under_base = False

    if os.path.basename(temp_abs).startswith("temp_") and is_under_base:
        shutil.rmtree(temp_abs)
    else:
        print(f"Warning: Refusing to remove unexpected temp directory: {temp_abs}")

# Helper to normalize Enums
VALID_TOPICS = ["ENVIRONMENT", "TECHNOLOGY", "HISTORY", "HEALTH", "EDUCATION", "SCIENCE", "SOCIETY"]
VALID_QUESTION_TYPES = [
    "MCQ", "TFNG", "FILL_BLANK", "YNNG", "SENTENCE_COMPLETION",
    "SUMMARY_COMPLETION", "MATCHING_HEADINGS", "MATCHING_INFORMATION",
    "MATCHING_FEATURES", "MATCHING_SENTENCE_ENDINGS", "DIAGRAM_LABEL_COMPLETION",
    "SHORT_ANSWER"
]
VALID_ESSAY_TYPES = [
    "OPINION", "DISCUSSION", "CAUSE_AND_EFFECT", "PROBLEM_AND_SOLUTION",
    "ADVANTAGES_DISADVANTAGES", "TWO_PART_QUESTION", "LINE_GRAPH",
    "BAR_CHART", "PIE_CHART", "TABLE", "MAP", "DIAGRAM", "LETTER"
]

def map_to_topic_enum(topic_str):
    if not topic_str:
        return "SOCIETY"
    topic_upper = topic_str.upper().strip()
    for t in VALID_TOPICS:
        if t in topic_upper or topic_upper in t:
            return t
    return "SOCIETY"

def map_to_question_type(q_type_str):
    if not q_type_str:
        return "MCQ"
    q_type_upper = q_type_str.upper().strip()
    mapping = {
        "MULTIPLE_CHOICE": "MCQ",
        "TRUE_FALSE_NOT_GIVEN": "TFNG",
        "YES_NO_NOT_GIVEN": "YNNG",
        "MATCHING": "MATCHING_INFORMATION",
        "GAP_FILL": "FILL_BLANK",
        "COMPLETION": "FILL_BLANK",
        "NOTE_COMPLETION": "FILL_BLANK"
    }
    if q_type_upper in mapping:
        return mapping[q_type_upper]
    for qt in VALID_QUESTION_TYPES:
        if qt == q_type_upper or qt.replace("_", "") == q_type_upper.replace("_", ""):
            return qt
    return "MCQ"

def map_to_essay_type(essay_type_str):
    if not essay_type_str:
        return "OPINION"
    essay_upper = essay_type_str.upper().strip()
    mapping = {
        "AGREE_DISAGREE": "OPINION",
        "AGREE OR DISAGREE": "OPINION",
        "DISCUSSION_AND_OPINION": "DISCUSSION",
        "LINE": "LINE_GRAPH",
        "BAR": "BAR_CHART",
        "PIE": "PIE_CHART",
        "FLOW_CHART": "DIAGRAM",
        "PROCESS": "DIAGRAM"
    }
    if essay_upper in mapping:
        return mapping[essay_upper]
    for et in VALID_ESSAY_TYPES:
        if et in essay_upper or essay_upper in et:
            return et
    return "OPINION"

# Clean json text from Gemini response markdown formatting
def clean_json_text(text):
    text = text.strip()
    # Remove markdown code block if present
    match = re.search(r'```json\s*(\{.*\}|\[.*\])\s*```', text, re.DOTALL)
    if match:
        return match.group(1)
    match_any = re.search(r'(\{.*\}|\[.*\])', text, re.DOTALL)
    if match_any:
        return match_any.group(1)
    return text

# Call Gemini / OpenRouter API with retries and exponential backoff
def call_gemini_with_retry(prompt, images=None, response_json=False):
    import requests
    import base64

    openrouter_key = os.getenv("OPENROUTER_API_KEY")
    gemini_key = os.getenv("GEMINI_API_KEY")

    # If GEMINI_API_KEY is not set or we prefer OpenRouter, use it.
    use_openrouter = openrouter_key is not None

    for attempt in range(6):
        try:
            if use_openrouter:
                content_parts = []
                if images:
                    for img_path in images:
                        if os.path.exists(img_path):
                            ext = os.path.splitext(img_path)[1].lower()
                            mime_type = "image/png"
                            if ext in [".jpg", ".jpeg"]:
                                mime_type = "image/jpeg"
                            with open(img_path, "rb") as f:
                                base64_image = base64.b64encode(f.read()).decode('utf-8')
                            content_parts.append({
                                "type": "image_url",
                                "image_url": {
                                    "url": f"data:{mime_type};base64,{base64_image}"
                                }
                            })
                        else:
                            print(f"Warning: Image file not found for OpenRouter: {img_path}")

                content_parts.append({
                    "type": "text",
                    "text": prompt
                })

                messages = [
                    {
                        "role": "user",
                        "content": content_parts
                    }
                ]

                headers = {
                    "Authorization": f"Bearer {openrouter_key}",
                    "Content-Type": "application/json"
                }

                payload = {
                    "model": "google/gemini-2.5-flash",
                    "messages": messages,
                    "max_tokens": 8000
                }
                if response_json:
                    payload["response_format"] = { "type": "json_object" }

                resp = requests.post(
                    'https://openrouter.ai/api/v1/chat/completions',
                    headers=headers,
                    json=payload,
                    timeout=90
                )

                if resp.status_code == 200:
                    res_json = resp.json()
                    return res_json['choices'][0]['message']['content']
                elif resp.status_code == 402:
                    print(f"  [OpenRouter 402 Credit Error] Attempting with reduced max_tokens...")
                    payload["max_tokens"] = 3000
                    resp2 = requests.post(
                        'https://openrouter.ai/api/v1/chat/completions',
                        headers=headers,
                        json=payload,
                        timeout=90
                    )
                    if resp2.status_code == 200:
                        return resp2.json()['choices'][0]['message']['content']
                    else:
                        raise Exception(f"OpenRouter API error {resp2.status_code}: {resp2.text}")
                else:
                    raise Exception(f"OpenRouter API error {resp.status_code}: {resp.text}")
            else:
                model = genai.GenerativeModel('gemini-2.5-flash')
                contents = []
                if images:
                    for img_path in images:
                        if os.path.exists(img_path):
                            contents.append(Image.open(img_path))
                        else:
                            print(f"Warning: Image file not found for Gemini: {img_path}")
                contents.append(prompt)

                config = {}
                if response_json:
                    config["response_mime_type"] = "application/json"

                response = model.generate_content(
                    contents,
                    generation_config=config
                )
                return response.text
        except Exception as e:
            err_msg = str(e).lower()
            if "exhausted" in err_msg or "429" in err_msg or "503" in err_msg or "rate limit" in err_msg:
                wait_time = (2 ** attempt) * 10 + 15
                print(f"  [API Rate Limit/Error] Retrying in {wait_time}s... Error: {e}")
                time.sleep(wait_time)
            elif "403" in err_msg and "leaked" in err_msg and openrouter_key:
                print(f"  [Gemini API Error] Key reported as leaked! Switching to OpenRouter...")
                use_openrouter = True
                time.sleep(2)
            else:
                print(f"  [API Error] Retrying in 5s... Error: {e}")
                time.sleep(5)

    raise Exception("Failed to get response from API after 6 attempts.")

# Step 1: Detect PDF mode (Text vs Scan/Vision)
def detect_pdf_mode(pdf_path):
    print(f"\nAnalyzing PDF file: {pdf_path}")
    try:
        doc = fitz.open(pdf_path)
        num_pages = len(doc)
        print(f"Total pages: {num_pages}")

        # Test first page
        first_page = doc[0]
        text = first_page.get_text()
        alnum_chars = len([c for c in text if c.isalnum()])

        print(f"Character count on Page 1: {alnum_chars}")
        is_text_mode = alnum_chars > 100

        doc.close()
        return is_text_mode, alnum_chars
    except Exception as e:
        print(f"Error opening PDF: {e}")
        return False, 0

# Step 2: Render PDF pages to PNG at 200 DPI
def render_pdf_pages(pdf_path, temp_dir, page_indices):
    os.makedirs(temp_dir, exist_ok=True)
    rendered_images = {}

    doc = fitz.open(pdf_path)
    for p_idx in page_indices:
        if p_idx < 0 or p_idx >= len(doc):
            print(f"Warning: Page index {p_idx} is out of bounds for PDF (0-{len(doc)-1})")
            continue

        out_name = f"page_{p_idx}.png"
        out_path = os.path.join(temp_dir, out_name)

        # Check if already rendered
        if os.path.exists(out_path):
            rendered_images[p_idx] = out_path
            continue

        print(f"Rendering page {p_idx} to {out_name}...")
        page = doc[p_idx]
        pix = page.get_pixmap(dpi=200)
        pix.save(out_path)
        rendered_images[p_idx] = out_path

    doc.close()
    return rendered_images

# Crop chart area for Writing Task 1
def crop_writing_chart(pdf_path, page_idx, bbox, output_path):
    """
    bbox is [ymin, xmin, ymax, xmax] in normalized 0-1000 range
    """
    try:
        doc = fitz.open(pdf_path)
        page = doc[page_idx]

        # Get page dimensions
        p_rect = page.rect
        width = p_rect.width
        height = p_rect.height

        ymin, xmin, ymax, xmax = bbox

        # Denormalize to page pixels
        fitz_rect = fitz.Rect(
            xmin * width / 1000,
            ymin * height / 1000,
            xmax * width / 1000,
            ymax * height / 1000
        )

        print(f"Cropping Writing Task 1 visual chart: BBox {bbox} -> Rect {fitz_rect}...")
        pix = page.get_pixmap(dpi=200, clip=fitz_rect)
        pix.save(output_path)
        doc.close()
        print(f"Saved cropped chart to {output_path}")
        return True
    except Exception as e:
        print(f"Error cropping writing chart: {e}")
        return False

# Step 3: TOC-based Auto Detection
def auto_detect_mapping(pdf_path, temp_dir):
    print("\nAttempting to auto-detect page ranges using Table of Contents...")
    doc = fitz.open(pdf_path)
    num_pages = len(doc)

    # Typically TOC is in page indices 2, 3, 4, or 5
    toc_pages = [idx for idx in [2, 3, 4, 5] if idx < num_pages]
    rendered = render_pdf_pages(pdf_path, temp_dir, toc_pages)
    toc_images = [rendered[idx] for idx in toc_pages if idx in rendered]

    prompt = (
        "You are analyzing a Table of Contents (TOC) page of a Cambridge IELTS practice tests book.\n"
        "Please read the TOC images carefully and detect the page ranges for Test 1, Test 2, Test 3, and Test 4.\n"
        "For each test, identify the book page numbers for:\n"
        "- Listening questions\n"
        "- Reading Passage 1, Passage 2, Passage 3\n"
        "- Writing Task 1, Writing Task 2\n"
        "- Audio Scripts / Transcripts (Listening transcripts at the back of the book)\n"
        "- Answer Keys (the answers at the back of the book)\n\n"
        "To map book page numbers to actual PDF page indices, locate the book page number printed on the TOC page itself, "
        "and compare it to the page index. For example, if book page 3 is page index 2, the offset is -1.\n"
        "Return a JSON object containing the 0-indexed PDF page ranges (as lists of integer page indices) matching this schema:\n"
        "{\n"
        "  \"book_title\": \"Cambridge IELTS XX\",\n"
        "  \"tests\": {\n"
        "    \"1\": {\n"
        "      \"listening\": {\n"
        "        \"part1\": { \"pages\": [index1, index2] },\n"
        "        \"part2\": { \"pages\": [index2, index3] },\n"
        "        \"part3\": { \"pages\": [index3, index4] },\n"
        "        \"part4\": { \"pages\": [index4, index5] },\n"
        "        \"transcript_pages\": [idx_start, idx_end] \n"
        "      },\n"
        "      \"reading\": {\n"
        "        \"passage1\": { \"pages\": [idx_start, idx_end], \"questions_pages\": [idx_start, idx_end] },\n"
        "        \"passage2\": { \"pages\": [idx_start, idx_end], \"questions_pages\": [idx_start, idx_end] },\n"
        "        \"passage3\": { \"pages\": [idx_start, idx_end], \"questions_pages\": [idx_start, idx_end] }\n"
        "      },\n"
        "      \"writing\": {\n"
        "        \"task1\": { \"pages\": [idx] },\n"
        "        \"task2\": { \"pages\": [idx] }\n"
        "      }\n"
        "    },\n"
        "    \"2\": { ... },\n"
        "    \"3\": { ... },\n"
        "    \"4\": { ... }\n"
        "  },\n"
        "  \"answer_key_pages\": [idx_start, idx_end]\n"
        "}"
    )

    try:
        response_text = call_gemini_with_retry(prompt, images=toc_images, response_json=True)
        cleaned_json = clean_json_text(response_text)
        mapping = json.loads(cleaned_json)
        print(f"Auto-detection successful. Book title: {mapping.get('book_title')}")
        doc.close()
        return mapping
    except Exception as e:
        print(f"Auto-detection failed: {e}. Defaulting to template mapping.")
        doc.close()
        return None

# Step 4: Extract Answer Keys from Answer Key pages
def extract_answer_keys(pdf_path, temp_dir, answer_pages, is_text_mode):
    print("\n--- Extracting Correct Answer Keys ---")

    rendered_paths = []
    text_content = ""

    if is_text_mode:
        doc = fitz.open(pdf_path)
        for p in answer_pages:
            if p >= 0 and p < len(doc):
                text_content += f"--- Page {p} ---\n" + doc[p].get_text() + "\n"
        doc.close()
    else:
        rendered = render_pdf_pages(pdf_path, temp_dir, answer_pages)
        rendered_paths = [rendered[p] for p in answer_pages if p in rendered]

    prompt = (
        "You are an IELTS grader. Extract the correct answer keys for Test 1, Test 2, Test 3, and Test 4 from the provided Answer Key page details.\n"
        "For each test, extract both Listening answers (40 questions) and Reading answers (40 questions).\n"
        "Return a JSON object matching this schema:\n"
        "{\n"
        "  \"test1\": {\n"
        "    \"listening\": {\n"
        "      \"1\": \"correct_answer\",\n"
        "      \"2\": \"correct_answer\",\n"
        "      ...\n"
        "    },\n"
        "    \"reading\": {\n"
        "      \"1\": \"correct_answer\",\n"
        "      \"2\": \"correct_answer\",\n"
        "      ...\n"
        "    }\n"
        "  },\n"
        "  ...\n"
        "}"
    )

    if is_text_mode:
        user_prompt = f"ANSWER KEY TEXT CONTENT:\n{text_content}"
        res_text = call_gemini_with_retry(user_prompt + "\n" + prompt, response_json=True)
    else:
        res_text = call_gemini_with_retry(prompt, images=rendered_paths, response_json=True)

    cleaned_json = clean_json_text(res_text)
    return json.loads(cleaned_json)

# Step 5: Extract Reading Passage
def extract_reading_passage(pdf_path, temp_dir, passage_num, pages, questions_pages, is_text_mode, answers_dict):
    print(f"  Extracting Reading Passage {passage_num}...")

    text_content = ""
    images = []

    all_pages = pages + questions_pages

    if is_text_mode:
        doc = fitz.open(pdf_path)
        for p in all_pages:
            if p >= 0 and p < len(doc):
                text_content += f"--- Page {p} ---\n" + doc[p].get_text() + "\n"
        doc.close()
    else:
        rendered = render_pdf_pages(pdf_path, temp_dir, all_pages)
        images = [rendered[p] for p in all_pages if p in rendered]

    answers_str = json.dumps(answers_dict, ensure_ascii=False)

    prompt = (
        f"You are a professional IELTS Reading creator. Extract Reading Passage {passage_num}.\n"
        "Given the page details and the correct answers for the questions, you will:\n"
        "1. Extract and clean the reading passage text (title, paragraphs). Remove page numbers, headers, and questions.\n"
        "2. Extract the actual question texts verbatim from the questions page.\n"
        "3. For MCQ questions, extract the option contents.\n"
        "4. Determine the correct question_type and group_label for each question. The type MUST be one of: 'MCQ', 'TFNG', 'FILL_BLANK', 'YNNG', 'SENTENCE_COMPLETION', 'SUMMARY_COMPLETION', 'MATCHING_HEADINGS', 'MATCHING_INFORMATION', 'MATCHING_FEATURES', 'MATCHING_SENTENCE_ENDINGS', 'DIAGRAM_LABEL_COMPLETION', 'SHORT_ANSWER'.\n"
        "5. Generate a concise explanation (2-3 sentences) and extract the exact verbatim evidence sentence from the passage text for each question.\n\n"
        f"CORRECT ANSWERS for these questions: {answers_str}\n\n"
        "Return a JSON object with this exact schema:\n"
        "{\n"
        "  \"title\": \"Cleaned Passage Title\",\n"
        "  \"topic\": \"one of ENVIRONMENT, TECHNOLOGY, HISTORY, HEALTH, EDUCATION, SCIENCE, SOCIETY\",\n"
        "  \"passage_text\": \"Full cleaned passage text...\",\n"
        "  \"questions\": [\n"
        "    {\n"
        "      \"order_index\": 1, // 1-indexed relative to this passage\n"
        "      \"question_type\": \"TFNG\",\n"
        "      \"group_label\": \"Questions 1-5: True/False/Not Given\",\n"
        "      \"group_id\": 1, // Questions in the same block share the same group_id\n"
        "      \"question_text\": \"Actual clean question stem...\",\n"
        "      \"correct_answer\": \"FALSE\", // must match the answers provided\n"
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

    if is_text_mode:
        user_prompt = f"READING CONTENT:\n{text_content}\n\n"
        res_text = call_gemini_with_retry(user_prompt + prompt, response_json=True)
    else:
        res_text = call_gemini_with_retry(prompt, images=images, response_json=True)

    cleaned_json = clean_json_text(res_text)
    try:
        return json.loads(cleaned_json)
    except Exception as je:
        debug_dir = os.path.join(os.path.dirname(pdf_path), "debug_failed")
        os.makedirs(debug_dir, exist_ok=True)
        debug_path = os.path.join(debug_dir, f"failed_reading_p{passage_num}.txt")
        with open(debug_path, "w", encoding="utf-8") as df:
            df.write(res_text)
        print(f"    [DEBUG] Saved failed raw response to {debug_path}")
        raise je

# Step 6: Extract Listening Part
def extract_listening_part(pdf_path, temp_dir, part_num, q_pages, script_pages, is_text_mode, answers_dict):
    print(f"  Extracting Listening Part {part_num}...")

    text_content = ""
    images = []

    all_pages = q_pages + script_pages

    if is_text_mode:
        doc = fitz.open(pdf_path)
        for p in all_pages:
            if p >= 0 and p < len(doc):
                text_content += f"--- Page {p} ---\n" + doc[p].get_text() + "\n"
        doc.close()
    else:
        rendered = render_pdf_pages(pdf_path, temp_dir, all_pages)
        images = [rendered[p] for p in all_pages if p in rendered]

    answers_str = json.dumps(answers_dict, ensure_ascii=False)

    prompt = (
        f"You are a professional IELTS Listening instructor. Extract Listening Part {part_num}.\n"
        "Given the page details and the correct answers, you will:\n"
        "1. Extract and clean the transcript. Format with capitalized speaker names (e.g. 'JOHN: ...'). Remove page numbers, headers, and noise.\n"
        "2. Extract the actual question texts verbatim from the questions page.\n"
        "3. For MCQ questions, extract the option contents.\n"
        "4. Determine the correct question_type and group_label for each question. The type MUST be one of: 'MCQ', 'TFNG', 'FILL_BLANK', 'YNNG', 'SENTENCE_COMPLETION', 'SUMMARY_COMPLETION', 'MATCHING_HEADINGS', 'MATCHING_INFORMATION', 'MATCHING_FEATURES', 'MATCHING_SENTENCE_ENDINGS', 'DIAGRAM_LABEL_COMPLETION', 'SHORT_ANSWER'.\n"
        "5. Generate a concise explanation (2-3 sentences) explaining why each answer is correct based on the transcript.\n\n"
        f"CORRECT ANSWERS for these questions: {answers_str}\n\n"
        "Return a JSON object with this exact schema:\n"
        "{\n"
        "  \"title\": \"Cleaned Part Title\",\n"
        "  \"topic\": \"clean topic description\",\n"
        "  \"transcript_text\": \"Full cleaned transcript...\",\n"
        "  \"questions\": [\n"
        "    {\n"
        "      \"order_index\": 1, // 1-indexed relative to this part\n"
        "      \"question_type\": \"FILL_BLANK\",\n"
        "      \"group_label\": \"Questions 1-10: Note Completion\",\n"
        "      \"group_id\": 1, \n"
        "      \"question_text\": \"Actual clean question stem...\",\n"
        "      \"correct_answer\": \"answer\", \n"
        "      \"explanation\": \"explanation based on transcript\",\n"
        "      \"options\": [\n"
        "        { \"label\": \"A\", \"content\": \"text of option A\" },\n"
        "        { \"label\": \"B\", \"content\": \"text of option B\" }\n"
        "      ]\n"
        "    }\n"
        "  ]\n"
        "}"
    )

    if is_text_mode:
        user_prompt = f"LISTENING CONTENT:\n{text_content}\n\n"
        res_text = call_gemini_with_retry(user_prompt + prompt, response_json=True)
    else:
        res_text = call_gemini_with_retry(prompt, images=images, response_json=True)

    cleaned_json = clean_json_text(res_text)
    try:
        return json.loads(cleaned_json)
    except Exception as je:
        debug_dir = os.path.join(os.path.dirname(pdf_path), "debug_failed")
        os.makedirs(debug_dir, exist_ok=True)
        debug_path = os.path.join(debug_dir, f"failed_listening_part{part_num}.txt")
        with open(debug_path, "w", encoding="utf-8") as df:
            df.write(res_text)
        print(f"    [DEBUG] Saved failed raw response to {debug_path}")
        raise je

# Step 7: Extract Writing Prompt
def extract_writing_prompt(pdf_path, temp_dir, task_num, pages, is_text_mode, pdf_basename, test_num):
    print(f"  Extracting Writing Task {task_num}...")

    text_content = ""
    images = []

    if is_text_mode:
        doc = fitz.open(pdf_path)
        for p in pages:
            if p >= 0 and p < len(doc):
                text_content += f"--- Page {p} ---\n" + doc[p].get_text() + "\n"
        doc.close()
    else:
        rendered = render_pdf_pages(pdf_path, temp_dir, pages)
        images = [rendered[p] for p in pages if p in rendered]

    prompt = (
        f"You are an IELTS Writing instructor. Extract Writing Task {task_num}.\n"
        "1. Extract the verbatim writing prompt text (usually starting with 'You should spend about 20/40 minutes...').\n"
        "2. Determine the essay type.\n"
        "3. For Task 1, if there is a visual chart/diagram/map/graph on the page, detect the bounding box of that visual element (not the text around it). "
        "The bounding box MUST be in normalized coordinates [ymin, xmin, ymax, xmax] from 0 to 1000 relative to the page height and width.\n\n"
        "Return a JSON object with this exact schema:\n"
        "{\n"
        "  \"prompt_text\": \"Full writing prompt text...\",\n"
        "  \"essay_type\": \"one of OPINION, DISCUSSION, CAUSE_AND_EFFECT, PROBLEM_AND_SOLUTION, ADVANTAGES_DISADVANTAGES, TWO_PART_QUESTION, LINE_GRAPH, BAR_CHART, PIE_CHART, TABLE, MAP, DIAGRAM, LETTER\",\n"
        "  \"chart_bbox\": [ymin, xmin, ymax, xmax] // only for Task 1 if there is a visual chart, otherwise null\n"
        "}"
    )

    if is_text_mode:
        user_prompt = f"WRITING CONTENT:\n{text_content}\n\n"
        res_text = call_gemini_with_retry(user_prompt + prompt, response_json=True)
    else:
        res_text = call_gemini_with_retry(prompt, images=images, response_json=True)

    cleaned_json = clean_json_text(res_text)
    try:
        data = json.loads(cleaned_json)
    except Exception as je:
        debug_dir = os.path.join(os.path.dirname(pdf_path), "debug_failed")
        os.makedirs(debug_dir, exist_ok=True)
        debug_path = os.path.join(debug_dir, f"failed_writing_task{task_num}.txt")
        with open(debug_path, "w", encoding="utf-8") as df:
            df.write(res_text)
        print(f"    [DEBUG] Saved failed raw response to {debug_path}")
        raise je

    image_url = None
    # If Task 1 has a chart, crop it!
    if task_num == 1 and data.get("chart_bbox") and len(pages) > 0:
        bbox = data["chart_bbox"]
        target_page = pages[0]
        # Create output directories
        out_dir = r"d:\sources\repos\proj\IELST\ielts-smartprep\frontend\public\images"
        os.makedirs(out_dir, exist_ok=True)

        img_filename = f"{pdf_basename}_test{test_num}_task1.png"
        out_path = os.path.join(out_dir, img_filename)

        success = crop_writing_chart(pdf_path, target_page, bbox, out_path)
        if success:
            image_url = f"/images/{img_filename}"
            print(f"  Cropped and saved Task 1 image: {image_url}")

    data["image_url"] = image_url
    return data

# Main Processing function
def process_pdf(pdf_path, folder_path, dry_run):
    pdf_basename = os.path.splitext(os.path.basename(pdf_path))[0]
    temp_dir = os.path.join(folder_path, f"temp_{pdf_basename}")

    # Detect mode
    is_text_mode, _ = detect_pdf_mode(pdf_path)
    print(f"Detection Result: {'TEXT' if is_text_mode else 'VISION (SCAN)'} branch selected.")

    # Load or detect mapping
    mapping_path = os.path.join(folder_path, f"{pdf_basename}_mapping.json")
    mapping = None
    if os.path.exists(mapping_path):
        print(f"Loading mapping from file: {mapping_path}")
        with open(mapping_path, "r", encoding="utf-8") as f:
            mapping = json.load(f)
    else:
        mapping = auto_detect_mapping(pdf_path, temp_dir)
        if mapping:
            # Save mapping to file for future use
            with open(mapping_path, "w", encoding="utf-8") as f:
                json.dump(mapping, f, ensure_ascii=False, indent=2)
            print(f"Saved auto-detected mapping to: {mapping_path}")

    if not mapping:
        print("Error: Could not retrieve a page mapping configuration. Skipping PDF.")
        return

    print(f"\n--- Extracting correct answer keys from book ---")
    answer_pages = mapping.get("answer_key_pages", [])
    if not answer_pages:
        print("Warning: No 'answer_key_pages' found in mapping config.")
        answers = {}
    else:
        try:
            answers = extract_answer_keys(pdf_path, temp_dir, answer_pages, is_text_mode)
            print("Successfully extracted answer keys!")
        except Exception as e:
            print(f"Failed to extract answers: {e}. Proceeding without answers.")
            answers = {}

    book_title = mapping.get("book_title", pdf_basename)

    extracted_data = {
        "book_title": book_title,
        "pdf_basename": pdf_basename,
        "tests": {}
    }

    failed_log_path = os.path.join(folder_path, "failed_pages.log")
    if os.path.exists(failed_log_path):
        os.remove(failed_log_path)

    def log_failure(message):
        with open(failed_log_path, "a", encoding="utf-8") as f:
            f.write(message + "\n")
        print(f"  [LOGGED FAILURE] {message}")

    # Process each Test
    tests = mapping.get("tests", {})
    for test_num_str, test_cfg in tests.items():
        test_num = int(test_num_str)
        print(f"\n==========================================")
        print(f"Processing Test {test_num}...")
        print(f"==========================================")

        extracted_data["tests"][test_num] = {
            "reading": [],
            "listening": [],
            "writing": []
        }

        test_answers = answers.get(f"test{test_num}", {})
        reading_answers = test_answers.get("reading", {})
        listening_answers = test_answers.get("listening", {})

        # 1. Reading
        print("\n--- Reading Section ---")
        reading_cfg = test_cfg.get("reading", {})
        for passage_num_str in ["passage1", "passage2", "passage3"]:
            passage_num = int(passage_num_str[-1])
            pass_cfg = reading_cfg.get(passage_num_str, {})
            pages = pass_cfg.get("pages", [])
            q_pages = pass_cfg.get("questions_pages", [])

            if not pages or not q_pages:
                print(f"  Skipping Passage {passage_num} (missing page config)")
                continue

            try:
                # Filter answers for this passage (e.g. Q1-13, Q14-26, Q27-40)
                # Passage 1: questions 1 to 13
                # Passage 2: questions 14 to 26
                # Passage 3: questions 27 to 40
                p_answers = {}
                start_q = 1 if passage_num == 1 else (14 if passage_num == 2 else 27)
                end_q = 13 if passage_num == 1 else (26 if passage_num == 2 else 40)
                for q in range(start_q, end_q + 1):
                    p_answers[str(q)] = reading_answers.get(str(q), "")

                data = extract_reading_passage(pdf_path, temp_dir, passage_num, pages, q_pages, is_text_mode, p_answers)
                data["passage_num"] = passage_num
                extracted_data["tests"][test_num]["reading"].append(data)
                time.sleep(3) # avoid rate limits
            except Exception as e:
                log_failure(f"Reading Test {test_num} Passage {passage_num} failed: {e}")

        # 2. Listening
        print("\n--- Listening Section ---")
        listening_cfg = test_cfg.get("listening", {})
        transcript_pages = listening_cfg.get("transcript_pages", [])

        # Listening parts are divided by question pages
        for part_num in [1, 2, 3, 4]:
            part_cfg = listening_cfg.get(f"part{part_num}", {})
            pages = part_cfg.get("pages", [])

            if not pages:
                print(f"  Skipping Listening Part {part_num} (missing page config)")
                continue

            try:
                p_answers = {}
                start_q = (part_num - 1) * 10 + 1
                end_q = part_num * 10
                for q in range(start_q, end_q + 1):
                    p_answers[str(q)] = listening_answers.get(str(q), "")

                data = extract_listening_part(pdf_path, temp_dir, part_num, pages, transcript_pages, is_text_mode, p_answers)
                data["part_number"] = part_num
                extracted_data["tests"][test_num]["listening"].append(data)
                time.sleep(3) # avoid rate limits
            except Exception as e:
                log_failure(f"Listening Test {test_num} Part {part_num} failed: {e}")

        # 3. Writing
        print("\n--- Writing Section ---")
        writing_cfg = test_cfg.get("writing", {})
        for task_num in [1, 2]:
            task_cfg = writing_cfg.get(f"task{task_num}", {})
            pages = task_cfg.get("pages", [])

            if not pages:
                print(f"  Skipping Writing Task {task_num} (missing page config)")
                continue

            try:
                data = extract_writing_prompt(pdf_path, temp_dir, task_num, pages, is_text_mode, pdf_basename, test_num)
                data["task_number"] = task_num
                extracted_data["tests"][test_num]["writing"].append(data)
                time.sleep(3) # avoid rate limits
            except Exception as e:
                log_failure(f"Writing Test {test_num} Task {task_num} failed: {e}")

    # Clean up rendered images temp directory
    if os.path.exists(temp_dir):
        print(f"\nCleaning up temporary rendered images in {temp_dir}...")
        remove_temp_dir(temp_dir, folder_path)

    return extracted_data

# Validate extracted counts and fields
def validate_extracted_data(data):
    print("\n==========================================")
    print("VALIDATION REPORT")
    print("==========================================")

    is_valid = True
    tests = data.get("tests", {})

    for test_num, test_data in tests.items():
        print(f"\nTest {test_num}:")

        # 1. Reading Quiz validation
        readings = test_data.get("reading", [])
        print(f"  Reading Passages: {len(readings)}/3 extracted")
        if len(readings) != 3:
            is_valid = False
            print("    [ERROR] Reading passages count must be exactly 3!")

        total_reading_qs = 0
        for r in readings:
            q_count = len(r.get("questions", []))
            total_reading_qs += q_count
            print(f"    Passage {r['passage_num']} ({r['title'][:30]}...): {q_count} questions")
            # Passage validations
            if not r.get("passage_text") or len(r.get("passage_text", "")) < 200:
                print(f"      [WARNING] Passage text is empty or unusually short!")
            for q in r.get("questions", []):
                if not q.get("correct_answer"):
                    print(f"      [ERROR] Q{q.get('order_index')} correct answer is empty!")
                    is_valid = False

        print(f"    Total Reading Questions: {total_reading_qs}/40")
        if total_reading_qs != 40:
            print("    [WARNING] Total reading questions count is not 40!")

        # 2. Listening validation
        listenings = test_data.get("listening", [])
        print(f"  Listening Parts: {len(listenings)}/4 extracted")
        if len(listenings) != 4:
            is_valid = False
            print("    [ERROR] Listening parts count must be exactly 4!")

        total_listening_qs = 0
        for l in listenings:
            q_count = len(l.get("questions", []))
            total_listening_qs += q_count
            print(f"    Part {l['part_number']} ({l['title'][:30]}...): {q_count} questions")
            if not l.get("transcript_text") or len(l.get("transcript_text", "")) < 200:
                print(f"      [WARNING] Transcript is empty or unusually short!")
            for q in l.get("questions", []):
                if not q.get("correct_answer"):
                    print(f"      [ERROR] Q{q.get('order_index')} correct answer is empty!")
                    is_valid = False

        print(f"    Total Listening Questions: {total_listening_qs}/40")
        if total_listening_qs != 40:
            print("    [WARNING] Total listening questions count is not 40!")

        # 3. Writing validation
        writings = test_data.get("writing", [])
        print(f"  Writing Tasks: {len(writings)}/2 extracted")
        if len(writings) != 2:
            is_valid = False
            print("    [ERROR] Writing tasks count must be exactly 2!")
        for w in writings:
            print(f"    Task {w['task_number']} Type: {w.get('essay_type')}")
            if w['task_number'] == 1 and not w.get("image_url"):
                print("      [WARNING] Task 1 visual chart image was not cropped!")
            if not w.get("prompt_text") or len(w.get("prompt_text", "")) < 50:
                print("      [WARNING] Writing prompt text is extremely short or empty!")

    return is_valid

# Save data to MySQL database
def insert_data_to_db(data):
    if not has_database_credentials():
        return False

    print("\n--- Connecting to MySQL Database for Import ---")
    try:
        conn = pymysql.connect(
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME,
            port=DB_PORT,
            cursorclass=pymysql.cursors.DictCursor
        )
        cursor = conn.cursor()
        print("Connected to database successfully.")
    except Exception as e:
        print(f"Database connection failed: {e}")
        return False

    try:
        ensure_required_import_columns(cursor)
    except Exception as e:
        conn.close()
        print(f"Database schema check failed: {e}")
        return False

    book_title = data["book_title"]
    pdf_basename = data["pdf_basename"]

    try:
        # We wrap the whole insertion of this PDF in a transaction
        for test_num, test_data in data["tests"].items():
            mock_test_title = f"{book_title} Test {test_num}"
            source_tag = truncate_source(f"{book_title} Test {test_num}")
            print(f"\nImporting {mock_test_title}...")

            # Idempotency check: check if mock test already exists
            cursor.execute("SELECT mock_test_id FROM mock_tests WHERE title = %s", (mock_test_title,))
            existing_mt = cursor.fetchone()
            if existing_mt:
                print(f"  Mock test '{mock_test_title}' already exists. Skipping this test to avoid duplicates.")
                continue

            # 1. Insert Mock Test
            cursor.execute("""
                INSERT INTO mock_tests (title, description, difficulty, source, created_by, imported_at)
                VALUES (%s, %s, %s, %s, %s, NOW())
            """, (mock_test_title, f"Practice test from {book_title} Book.", "PASSAGE_2", source_tag, IMPORT_CREATED_BY))
            mock_test_id = cursor.lastrowid

            # 2. Seeding Mock Test Sections
            cursor.execute("""
                INSERT INTO mock_test_sections (mock_test_id, section_type, duration_seconds, section_order)
                VALUES (%s, 'LISTENING', 2400, 1), (%s, 'READING', 3600, 2), (%s, 'WRITING', 3600, 3)
            """, (mock_test_id, mock_test_id, mock_test_id))

            # 3. Reading Quizzes
            reading_quiz_ids = []
            for r in test_data.get("reading", []):
                p_num = r["passage_num"]
                topic_enum = map_to_topic_enum(r.get("topic"))
                diff_enum = f"PASSAGE_{p_num}" # Passage 1 -> PASSAGE_1, etc.

                cursor.execute("""
                    INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, total_questions, is_template, module_type, source, created_by, imported_at)
                    VALUES (NULL, %s, %s, %s, %s, TRUE, 'ACADEMIC', %s, %s, NOW())
                """, (topic_enum, diff_enum, r["passage_text"], len(r["questions"]), source_tag, IMPORT_CREATED_BY))
                quiz_id = cursor.lastrowid
                reading_quiz_ids.append(quiz_id)

                # Insert Reading Questions
                for q in r.get("questions", []):
                    q_type = map_to_question_type(q.get("question_type"))
                    # Determine options json or word limit
                    options_json_str = None
                    if q.get("options_json"):
                        options_json_str = json.dumps(q.get("options_json"))
                    elif q.get("options") and q_type != "MCQ": # matching options
                        options_json_str = json.dumps([opt["content"] for opt in q["options"]])

                    word_limit = q.get("word_limit")

                    cursor.execute("""
                        INSERT INTO reading_questions (quiz_id, question_type, question_text, correct_answer, order_index, group_label, group_id, explanation, evidence_text, verified, options_json, word_limit)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, FALSE, %s, %s)
                    """, (quiz_id, q_type, q["question_text"], q["correct_answer"], q["order_index"], q["group_label"], q.get("group_id", 1), q.get("explanation"), q.get("evidence_sentence"), options_json_str, word_limit))
                    question_id = cursor.lastrowid

                    # Insert Options if MCQ
                    if q_type == "MCQ" and q.get("options"):
                        for idx_opt, opt in enumerate(q["options"]):
                            is_correct = 1 if opt["label"].strip().upper() == q["correct_answer"].strip().upper() else 0
                            cursor.execute("""
                                INSERT INTO question_options (reading_question_id, label, content, is_correct, order_index)
                                VALUES (%s, %s, %s, %s, %s)
                            """, (question_id, opt["label"], opt["content"], is_correct, idx_opt))

            # Link Reading Quizzes to Mock Test
            for order, q_id in enumerate(reading_quiz_ids):
                cursor.execute("""
                    INSERT INTO mock_test_reading_quizzes (mock_test_id, quiz_id, passage_order)
                    VALUES (%s, %s, %s)
                """, (mock_test_id, q_id, order))

            # 4. Listening Parts
            listening_part_ids = []
            for l in test_data.get("listening", []):
                part_num = l["part_number"]

                cursor.execute("""
                    INSERT INTO listening_parts (part_number, title, topic, audio_url, audio_status, transcript_text, created_by, source, imported_at)
                    VALUES (%s, %s, %s, '', 'PENDING', %s, %s, %s, NOW())
                """, (part_num, l["title"], l.get("topic", "IELTS Listening"), l["transcript_text"], IMPORT_CREATED_BY, source_tag))
                part_id = cursor.lastrowid
                listening_part_ids.append(part_id)

                # Insert Listening Questions
                for q in l.get("questions", []):
                    q_type = map_to_question_type(q.get("question_type"))

                    cursor.execute("""
                        INSERT INTO listening_questions (part_id, question_type, question_text, correct_answer, order_index, group_label, explanation, verified)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, FALSE)
                    """, (part_id, q_type, q["question_text"], q["correct_answer"], q["order_index"], q["group_label"], q.get("explanation")))
                    question_id = cursor.lastrowid

                    # Insert Options if MCQ
                    if q_type == "MCQ" and q.get("options"):
                        for idx_opt, opt in enumerate(q["options"]):
                            is_correct = 1 if opt["label"].strip().upper() == q["correct_answer"].strip().upper() else 0
                            cursor.execute("""
                                INSERT INTO question_options (listening_question_id, label, content, is_correct, order_index)
                                VALUES (%s, %s, %s, %s, %s)
                            """, (question_id, opt["label"], opt["content"], is_correct, idx_opt))

            # Link Listening Parts to Mock Test
            for order, p_id in enumerate(listening_part_ids):
                cursor.execute("""
                    INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order)
                    VALUES (%s, %s, %s)
                """, (mock_test_id, p_id, order))

            # 5. Writing Prompts
            writing_prompt_ids = []
            for w in test_data.get("writing", []):
                essay_type_enum = map_to_essay_type(w.get("essay_type"))

                cursor.execute("""
                    INSERT INTO writing_prompts (prompt_text, essay_type, image_url, source, created_by, imported_at)
                    VALUES (%s, %s, %s, %s, %s, NOW())
                """, (w["prompt_text"], essay_type_enum, w.get("image_url"), source_tag, IMPORT_CREATED_BY))
                prompt_id = cursor.lastrowid
                writing_prompt_ids.append(prompt_id)

            # Link Writing Prompts to Mock Test
            for order, pr_id in enumerate(writing_prompt_ids):
                cursor.execute("""
                    INSERT INTO mock_test_writing_prompts (mock_test_id, prompt_id, prompt_order)
                    VALUES (%s, %s, %s)
                """, (mock_test_id, pr_id, order))

            print(f"  Successfully imported {mock_test_title} and all related materials!")

        conn.commit()
        conn.close()
        print("\nAll database changes committed successfully!")
        return True
    except Exception as e:
        conn.rollback()
        conn.close()
        print(f"\n[CRITICAL ERROR] Database insertion failed. Transaction rolled back. Error: {e}")
        return False

# Main flow
def main():
    parser = argparse.ArgumentParser(description="Import Cambridge IELTS resources into Database using Gemini Vision.")
    parser.add_argument("--folder", required=True, help="Path to folder containing Cambridge PDF(s).")
    mode_group = parser.add_mutually_exclusive_group(required=True)
    mode_group.add_argument("--dry-run", action="store_true", help="Analyze and print summary without inserting into database.")
    mode_group.add_argument("--confirm", action="store_true", help="Insert records into the database.")

    args = parser.parse_args()

    folder_path = os.path.abspath(args.folder)
    if not os.path.isdir(folder_path):
        print(f"Error: Specified folder does not exist: {folder_path}")
        sys.exit(1)

    # Find PDF files
    pdf_files = sorted(os.path.join(folder_path, f) for f in os.listdir(folder_path) if f.lower().endswith(".pdf"))
    if not pdf_files:
        print(f"Error: No PDF files found in folder {folder_path}")
        sys.exit(1)

    if args.confirm and not has_database_credentials():
        sys.exit(1)

    if not configure_ai_client():
        sys.exit(1)

    print(f"Found {len(pdf_files)} PDF file(s) for import.")

    for pdf_path in pdf_files:
        print(f"\n==========================================")
        print(f"STARTING PIPELINE FOR: {os.path.basename(pdf_path)}")
        print(f"==========================================")

        # Process the PDF
        extracted_data = process_pdf(pdf_path, folder_path, args.dry_run)

        if not extracted_data:
            print(f"Could not extract data for {pdf_path}. Skipping.")
            continue

        # Validate
        is_valid = validate_extracted_data(extracted_data)

        # Save output json log for review
        log_out_path = os.path.join(folder_path, f"{extracted_data['pdf_basename']}_extracted_preview.json")
        with open(log_out_path, "w", encoding="utf-8") as f:
            json.dump(extracted_data, f, ensure_ascii=False, indent=2)
        print(f"\nSaved preview json details to: {log_out_path}")

        if not is_valid:
            print("\n[WARNING] Validation checks encountered issues! Please check the output reports.")

        # Dry-run report
        if args.dry_run:
            print("\n[DRY RUN SUMMARY] Extracted data successfully. No database modifications were performed.")
        elif args.confirm:
            print("\n[CONFIRMATION] Proceeding to insert data into MySQL database...")
            success = insert_data_to_db(extracted_data)
            if success:
                print(f"\nImport for {os.path.basename(pdf_path)} completed successfully!")
            else:
                print(f"\nImport for {os.path.basename(pdf_path)} FAILED.")

    print("\nPipeline finished.")

if __name__ == "__main__":
    main()
