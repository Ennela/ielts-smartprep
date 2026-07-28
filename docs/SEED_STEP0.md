# SEED — BƯỚC 0: Khảo sát repo trước khi sinh nội dung

> Khảo sát bằng cách **đọc code thật**. Chưa sinh một dòng nội dung nào, chưa sửa file nào.
> Đường dẫn tính từ `ielts-smartprep/`. Ngày khảo sát: **2026-07-26**.
> Tài liệu này phục vụ Prompt B (sinh 8 đề Reading / 6 đề Listening / 30 đề Writing).
> Liên quan: [SKILLS_STATUS.md](SKILLS_STATUS.md) — audit 3 luồng, có các lỗi P0 ảnh hưởng tới việc dùng được nội dung sau khi seed.
> **Cập nhật 2026-07-28:** số `V41` đã được dùng cho soft delete + FK safety. Migration schema nội dung mô tả dưới đây phải là **V42**. DB dev thật vẫn ở V40; chưa apply V41.

---

## Khảo sát repo

**Stack**
- Backend: Java 17, Spring Boot 3.2.5, Maven. ORM là **JPA/Hibernate**, không phải Prisma.
- DB: MySQL 8, quản lý schema bằng **Flyway** (source V1→V41; DB dev đã xác minh ở V40). `ddl-auto: none` ở base, **`validate`** ở dev và prod ([application-dev.yml:13](../backend/src/main/resources/application-dev.yml)).
- Frontend: React + Vite, **npm** (`package-lock.json`), test bằng **Vitest**.
- Scripts: **Python** (30 file trong `scripts/`) + 1 PowerShell. Không có `requirements.txt` ở bất kỳ đâu.
- Microservice TTS: FastAPI + thư viện `edge_tts`, `python:3.10-slim`.
- Hạ tầng: Redis, MinIO (bucket `listening-audio`), Nginx.

**Schema liên quan** (23 bảng; mọi khoá chính đều `BIGINT AUTO_INCREMENT`)

| Bảng | Cột quan trọng | Nhận xét |
|---|---|---|
| `reading_quizzes` | `passage_text` MEDIUMTEXT, `is_template`, `user_id` NULL-able, `module_type`, `content_status`, `source`, `created_by` | 1 row = **1 passage**, không phải 1 đề. Không có `title`, không có `slug`. |
| `reading_questions` | `question_type` ENUM(12), `correct_answer`, `explanation`, `evidence_text/offset/length`, `word_limit`, `group_id/label/context`, `options_json` (JSON native) | **Gần như đủ** cho Prompt B. Đây là trục khoẻ nhất. |
| `listening_parts` | `part_number`, `title`, `audio_url`, `transcript_text` **TEXT phẳng**, `audio_status`, `duration_seconds` | 1 row = 1 part. Không có ảnh, không có cấu trúc transcript. |
| `listening_questions` | **chỉ** `question_type` ENUM(12), `question_text`, `correct_answer`, `order_index`, `verified` | **Nghèo nhất.** Cả V1→V40 chỉ ALTER đúng 2 lần. |
| `question_options` | `reading_question_id` NULL, `listening_question_id` NULL, `label`, `content`, `is_correct`, `order_index` | Dùng chung cho **cả hai** kỹ năng — chỗ lưu phương án MCQ chuẩn. |
| `writing_prompts` | `prompt_text`, `essay_type`, `image_url`, `visual_data` **TEXT** | Chỉ có 4 cột nội dung. |
| `mock_tests` + 3 bảng nối + `mock_test_sections` | `mock_test_id`, `title`; join có `passage_order`/`part_order`/`prompt_order` | Cơ chế **duy nhất** gom nhiều thành phần thành một "đề". |

**Seed hiện có** — bằng **Flyway INSERT thẳng, không idempotent**. 7 file: V6 (8 listening part, hardcode `part_id` 1..8), V9, V30 (16 part cam19), V31, V32 (12 reading template), V33 (4 mock test), V10/V37 (chỉ UPDATE nên idempotent). Không có thư mục `seeds/` ngoài Flyway.

Đường nhập Cambridge 19 hiện tại là **đường lai**: Flyway tạo khung rỗng → `scripts/import_cam19.py` UPDATE nội dung thật vào → `scripts/upload_cam19_audio.ps1` đẩy mp3 vào MinIO bằng `docker cp` + `mc cp`. `scripts/import_cambridge.py` là **hình mẫu tốt nhất trong repo**: có `argparse --dry-run|--confirm` (dòng 1377-1383), có kiểm tra idempotency theo `title` (dòng 1239).

**edge-tts service** — đúng **một** endpoint:
```
GET /synthesize?text=<1..5000 ký tự>&voice=<regex ^[a-z]{2,3}-[A-Z]{2}-[A-Za-z0-9]+Neural$>&rate=<±N%, |N|≤50>
→ 200 audio/mpeg (MP3 thô)
→ 400 invalid_text|invalid_voice|invalid_rate · 413 query > 20000 byte · 429 (30 req/60s theo IP, kèm Retry-After) · 502 synthesis_failed
```
Không có endpoint batch (mỗi request đúng 1 đoạn text), **không có auth**, và **không có bất kỳ khái niệm khoảng lặng nào**. Voice bank thực tế đang dùng là 6 voice, khớp 100% giữa [TtsService.java:33-40](../backend/src/main/java/com/smartprep/service/TtsService.java) và `EDGE_TTS_ALLOWED_VOICES` ([docker-compose.yml:64](../docker-compose.yml)):

`en-US-GuyNeural` · `en-US-AriaNeural` · `en-GB-RyanNeural` · `en-GB-SoniaNeural` · `en-AU-WilliamNeural` · `en-AU-NatashaNeural`

⚠️ Voice bank Prompt B đề xuất có `en-GB-LibbyNeural`, `en-GB-MaisieNeural`, `en-GB-ThomasNeural` — **cả ba đều không nằm trong allowlist**, edge-tts sẽ trả 400. Phải hoặc đổi voice, hoặc sửa `EDGE_TTS_ALLOWED_VOICES`.

**Lưu trữ audio** — bucket MinIO `listening-audio`, phục vụ qua proxy backend `/api/v1/listening/audio/{key}` (permitAll, có Range). [ListeningController.java:217-240](../backend/src/main/java/com/smartprep/controller/ListeningController.java) có 3 tầng fallback: MinIO → **classpath `static/api/v1/listening/audio/{fileName}`** → MP3 câm 1 giây (HTTP 200). Đường classpath **đã có code nhưng thư mục chưa tồn tại** — đây là chỗ đặt file mp3 build sẵn tốt nhất.

**CI** — một workflow `.github/workflows/ci-cd.yml`, 2 job trên ubuntu-latest: backend `mvn --batch-mode test`; frontend `npm ci` → `npm run lint` → `npm test` → `npm audit`. Không có bước build, không có DB service container, không chạy `scripts/`. Test `@Tag("integration")` bị loại bởi `test.tag-expression=!integration` ([pom.xml:25](../backend/pom.xml)).

**Validation lib đang dùng** — Backend: Bean Validation (`jakarta.validation`) + Jackson, đã có sẵn qua `spring-boot-starter-validation` và `spring-boot-starter-test`. **Không có** thư viện JSON Schema. Frontend: **không có** zod/yup/ajv là direct dependency (zod và ajv chỉ là transitive dev dep của eslint — dùng chúng là phụ thuộc không khai báo).

**Convention** — DB `snake_case`, field Java `camelCase` không `@Column(name=)`, JSON ra FE `camelCase` mặc định (không có `PropertyNamingStrategy` nào). Migration `V<N>__snake_case.sql`; `V41` đã dành cho content soft delete → **migration schema seed kế tiếp là V42**. Không có `.editorconfig`, không có checkstyle/spotless; chỉ có `frontend/.prettierrc` giới hạn `src/**`.

---

## Trường còn thiếu so với yêu cầu nội dung

**Blocking** = không sửa thì nội dung sinh ra **bị mất khi seed**.

### Reading — trục khoẻ nhất, gần như đủ

| Cần lưu | Bảng/trường tương ứng | Có sẵn? | Đề xuất |
|---|---|---|---|
| Passage text | `reading_quizzes.passage_text` MEDIUMTEXT | ✅ | Dùng nguyên |
| Evidence câu văn gốc | `reading_questions.evidence_text/offset/length` | ✅ | Dùng nguyên. **Lưu ý:** offset tính trên chuỗi *đã có* tiền tố `"A. "` |
| Explanation | `reading_questions.explanation` | ✅ | Dùng nguyên |
| Word limit theo nhóm | `reading_questions.word_limit` INT | ⚠️ một phần | Per-question chứ không per-group → seeder lặp lại giá trị cho mọi câu trong nhóm. Chấp nhận được |
| **"AND/OR A NUMBER"** | — | ❌ **KHÔNG** | **Blocking.** `ADD COLUMN allow_number BOOLEAN` — không có thì không phân biệt được `NO MORE THAN TWO WORDS` với `... AND/OR A NUMBER` |
| Instruction nhóm + khung note/table | `group_id` / `group_label` / `group_context` | ✅ | Dùng nguyên |
| Options MCQ / Matching | `question_options` + `options_json` (JSON native) | ✅ | Chốt quy ước: `question_options` cho MCQ, `options_json` cho hộp phương án dùng chung |
| Đoạn A/B/C cho Matching Headings | quy ước text `"A. …"`, parse regex ở [PassageViewer.jsx:16](../frontend/src/components/reading/PassageViewer.jsx) | ⚠️ một phần | Không cần cột mới — seeder tuân thủ đúng định dạng `A. <text>\n\nB. <text>` là UI hiển thị đúng |
| 9 dạng câu hỏi | enum `QuestionType` + ENUM MySQL | ✅ | **Đủ 9/9.** Nếu muốn tách `NOTE_/TABLE_/FORM_COMPLETION` riêng thì phải ALTER cả enum Java lẫn ENUM MySQL |
| `word_count`, `rhetorical_structure` | — | ❌ | Không blocking (`word_count` suy ra được). Thêm nếu muốn giữ metadata QA |

### Listening — thiếu gần hết

| Cần lưu | Bảng/trường tương ứng | Có sẵn? | Đề xuất |
|---|---|---|---|
| **Transcript theo segment** (speaker / voice / text / `pause_after_ms`) | `listening_parts.transcript_text` là **một cột TEXT phẳng** | ❌ **KHÔNG** | **Blocking, nặng nhất.** Bảng mới `listening_transcript_segments` hoặc cột `transcript_json`. Không có thì 24 part bị ép dẹt thành 1 chuỗi, mất voice từng segment và toàn bộ khoảng lặng |
| Evidence câu văn gốc | — | ❌ **KHÔNG** | **Blocking.** Nguyên tắc 1.2 của bạn hiện **không áp dụng được cho Listening**. Trớ trêu: LLM prompt đã sinh sẵn `answerEvidence` ([ListeningPromptBuilder.java:242](../backend/src/main/java/com/smartprep/service/ai/ListeningPromptBuilder.java)) rồi vứt đi vì không có cột |
| Explanation | — | ❌ **KHÔNG** | **Blocking.** 240 câu mất giải thích |
| Word limit + allow_number | — | ❌ **KHÔNG** | **Blocking.** Part 1/4 gần như toàn dạng completion có `ONE WORD AND/OR A NUMBER` |
| Group structure (form/table, hộp A-H) | — | ❌ **KHÔNG** | **Blocking.** LLM đang sinh `noteContext`/`matchingOptions` rồi vứt |
| Ảnh map/plan Part 2 | — | ❌ **KHÔNG** | **Blocking.** Ma trận Prompt B có **map/plan ở cả 6 đề** — không có cột là mất 6 part |
| Options MCQ | `question_options.listening_question_id` | ✅ | Dùng nguyên |
| 4 part = 1 đề | `mock_test_listening_parts` | ⚠️ một phần | Lưu được, nhưng xem "Điểm cần bạn quyết" #2 |

### Writing — thiếu toàn bộ phần giá trị học tập

| Cần lưu | Bảng/trường tương ứng | Có sẵn? | Đề xuất |
|---|---|---|---|
| Chart JSON | `writing_prompts.visual_data` **TEXT** | ⚠️ một phần | Có chỗ lưu nhưng MySQL **không validate** → seeder phải tự validate. Cân nhắc `MODIFY ... JSON` |
| Model answer band 8 | — | ❌ **KHÔNG** | **Blocking.** 30 bài mẫu bị vứt hoàn toàn |
| Dàn ý (outline) | — | ❌ **KHÔNG** | **Blocking** |
| 10-15 từ vựng theo đề | bảng `vocabulary` **không tái dụng được** (`user_id NOT NULL` + `UNIQUE(user_id, word)`) | ❌ **KHÔNG** | **Blocking** |
| Checklist 4 tiêu chí riêng từng đề | — | ❌ **KHÔNG** | **Blocking.** Rubric hiện hardcode chung cho mọi đề ([WritingGradingService.java:280](../backend/src/main/java/com/smartprep/service/ai/WritingGradingService.java)) |
| `fallback_scoring` | — | ❌ **KHÔNG** | **Blocking, và phá mục tiêu.** Không có cột này thì Writing **vẫn phải gọi Gemini** để chấm — trái thẳng với "app chạy không cần LLM lúc runtime" |
| `min_words` theo đề | hardcode 150/250 ở 4 nơi | ❌ | Không blocking (suy từ `essay_type`) |

### Chung

| Cần lưu | Có sẵn? | Đề xuất |
|---|---|---|
| **`slug` / khoá tự nhiên để upsert idempotent** | ❌ **KHÔNG** ở bất kỳ bảng nào | `UNIQUE KEY` duy nhất trong cả schema là `vocabulary(user_id, word)`. Seed cũ phải dò row bằng `passage_text LIKE '%Test 1 Passage 1%'` ([V33:55](../backend/src/main/resources/db/migration/V33__seed_cambridge19_mock_tests.sql)). **Không có slug thì seeder không thể idempotent** — yêu cầu bắt buộc của Prompt B |

---

## Đề xuất migration

Một migration duy nhất, tương thích ngược (mọi cột đều NULL-able hoặc có DEFAULT). Gộp luôn các gap đã nêu ở [SKILLS_STATUS.md](SKILLS_STATUS.md) mục 6 — **bản V42 này thay thế bản V41 phác trong tài liệu đó**.

```sql
-- V42__seed_content_schema.sql
-- Bổ sung schema để lưu được bộ đề IELTS nguyên gốc (8 Reading / 6 Listening / 30 Writing)
-- Mọi cột đều nullable hoặc có DEFAULT → dữ liệu hiện có không vỡ.

-- ── 1. Khoá tự nhiên cho seeder idempotent ────────────────────────────────
-- MySQL cho phép nhiều NULL trong UNIQUE → hàng cũ (slug NULL) không xung đột.
ALTER TABLE reading_quizzes  ADD COLUMN seed_key VARCHAR(120) NULL,
                             ADD UNIQUE KEY uk_reading_quizzes_seed_key (seed_key);
ALTER TABLE listening_parts  ADD COLUMN seed_key VARCHAR(120) NULL,
                             ADD UNIQUE KEY uk_listening_parts_seed_key (seed_key);
ALTER TABLE writing_prompts  ADD COLUMN seed_key VARCHAR(120) NULL,
                             ADD UNIQUE KEY uk_writing_prompts_seed_key (seed_key);
ALTER TABLE mock_tests       ADD COLUMN seed_key VARCHAR(120) NULL,
                             ADD UNIQUE KEY uk_mock_tests_seed_key (seed_key);
-- ví dụ giá trị: 'reading-r01-p1', 'listening-l03-part2', 'writing-t1-07', 'test-reading-r01'

-- ── 2. Reading: chỉ còn thiếu allow_number + metadata QA ──────────────────
ALTER TABLE reading_questions ADD COLUMN allow_number BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE reading_quizzes   ADD COLUMN title VARCHAR(255) NULL,
                              ADD COLUMN word_count INT NULL,
                              ADD COLUMN rhetorical_structure VARCHAR(64) NULL;

-- ── 3. Listening: đưa lên ngang bằng reading_questions ────────────────────
ALTER TABLE listening_questions
    ADD COLUMN evidence_text  TEXT NULL,
    ADD COLUMN explanation    TEXT NULL,
    ADD COLUMN word_limit     INT NULL,
    ADD COLUMN allow_number   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN group_id       INT NULL,
    ADD COLUMN group_label    VARCHAR(255) NULL,
    ADD COLUMN group_context  TEXT NULL,
    ADD COLUMN options_json   JSON NULL;

-- Ảnh cho map/plan/diagram labelling (Part 2) + lời dẫn đầu part
ALTER TABLE listening_parts
    ADD COLUMN image_url            VARCHAR(512) NULL,
    ADD COLUMN section_instructions TEXT NULL;

-- ── 4. Transcript có cấu trúc segment (điểm chặn nặng nhất) ───────────────
CREATE TABLE listening_transcript_segments (
    segment_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    part_id        BIGINT NOT NULL,
    order_index    INT NOT NULL,
    speaker        VARCHAR(64)  NOT NULL,   -- 'narrator', 'A', 'B', ...
    voice          VARCHAR(64)  NOT NULL,   -- phải nằm trong EDGE_TTS_ALLOWED_VOICES
    text           TEXT         NOT NULL,
    pause_after_ms INT          NOT NULL DEFAULT 0,
    FOREIGN KEY (part_id) REFERENCES listening_parts(part_id) ON DELETE CASCADE,
    UNIQUE KEY uk_lts_part_order (part_id, order_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- listening_parts.transcript_text giữ nguyên làm bản phẳng để hiển thị/AI đọc.

-- ── 5. Writing: phần giá trị học tập + chấm không cần LLM ─────────────────
ALTER TABLE writing_prompts
    ADD COLUMN model_answer          MEDIUMTEXT NULL,
    ADD COLUMN model_answer_band     DECIMAL(2,1) NULL,
    ADD COLUMN outline_json          JSON NULL,   -- [{section, points[]}]
    ADD COLUMN topic_vocab_json      JSON NULL,   -- [{word, pos, meaning_vi, example}]
    ADD COLUMN checklist_json        JSON NULL,   -- {task_response[], coherence_cohesion[], lexical_resource[], grammatical_range[]}
    ADD COLUMN fallback_scoring_json JSON NULL,   -- theo contract mục 6.3 Prompt B
    ADD COLUMN min_words             INT NULL;

-- visual_data đang là TEXT → nâng thành JSON để DB gác cổng.
-- CHẠY SAU KHI đã kiểm tra 64 hàng hiện có đều là JSON hợp lệ (hoặc NULL).
ALTER TABLE writing_prompts MODIFY COLUMN visual_data JSON NULL;

-- ── 6. Phân biệt Academic / General Training ở cấp đề ─────────────────────
ALTER TABLE mock_tests      ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC';
ALTER TABLE writing_prompts ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC';
```

**Bắt buộc đi kèm** — vì `ddl-auto: validate`, mọi cột mới phải có **cả** migration **lẫn** field entity Java. Thiếu entity thì seeder qua JPA không ghi được; thiếu migration thì **app không khởi động**. Cần sửa: `ReadingQuestion`, `ReadingQuiz`, `ListeningQuestion`, `ListeningPart`, `WritingPrompt`, `MockTest` + entity mới `ListeningTranscriptSegment`.

---

## Điểm cần bạn quyết trước khi sang Bước 1

### 1. Bản quyền — nghiêm trọng nhất, ảnh hưởng trực tiếp mục đích repo

Prompt B mục 7 yêu cầu `docs/CONTENT.md` tuyên bố *"100% nguyên gốc, không lấy từ sách/đề thi thật"*. **Hiện chưa tuyên bố được như vậy**, vì trong git đang có:

| Đang commit | Là gì |
|---|---|
| `scripts/cam19_ocr_cache.json` | Cache OCR toàn bộ sách Cambridge IELTS 19 |
| `frontend/public/cam19_import_review.json` (**4.1 MB**) + bản trong `scripts/` | Nội dung Cambridge 19 đã trích xuất |
| **166 file ảnh** `frontend/public/images/temp_all/`, `temp_render/` (`page_N.png`) | Ảnh chụp trang sách |
| `V30`–`V33` | Seed mang tên riêng và answer key của Cambridge 19 |
| ~20 script trong `scripts/` | Công cụ trích xuất từ sách |

Tin tốt: git root là `ielts-smartprep/`, nên thư mục `IETLS 19 cam/` (chứa PDF + 24 file mp3 gốc) nằm **ngoài** repo và **không** bị track — đã kiểm bằng `git ls-files`.

Cần bạn chọn: **(a)** gỡ toàn bộ artifact Cambridge khỏi working tree *và lịch sử git* rồi mới sinh nội dung mới; **(b)** chỉ gỡ khỏi HEAD, chấp nhận còn trong history; **(c)** giữ nguyên, không tuyên bố "100% nguyên gốc". Tôi khuyến nghị **(a)** vì đây là repo public đi phỏng vấn — nhưng viết lại history là thao tác phá huỷ nên tôi sẽ không tự làm.

### 2. Mô hình "một đề" — và một sự thật khó chịu

Có thể lưu 8 đề Reading và 6 đề Listening bằng `mock_tests` + bảng nối. Nhưng **luồng runtime không đọc bảng nối**: `ReadingAssemblyService.java:45` và `ListeningQueryService.java:53` đều **bốc ngẫu nhiên** từ pool. Nghĩa là seed xong 8 đề cố định, app vẫn sẽ ghép passage lung tung — trừ khi sửa code.

Ba phương án: **(a)** tái dùng `mock_tests` + thêm cột `skill_scope` đánh dấu "đề chỉ Reading", sửa 2 service đọc theo đề; **(b)** thêm bảng riêng `reading_tests` / `listening_test_templates`; **(c)** không gom đề, giữ pool phẳng và để app ghép ngẫu nhiên (chấp nhận sai lệch với Prompt B). Tôi nghiêng về **(a)** — ít bảng mới nhất và tái dùng được `mock_test_sections` sẵn có.

⚠️ Bẫy dù chọn gì: `@OrderColumn` của Hibernate là **0-based**, mà `V14:88` seed 1-based còn `V33:28` seed 0-based. Seeder mới **bắt buộc** 0-based, nếu không list sẽ có phần tử `null` ở index 0.

### 3. Cơ chế seed — đề xuất bỏ Flyway cho phần dữ liệu

Bằng chứng thực nghiệm rằng seed-qua-Flyway đã hỏng: dump `backups/ielts_smartprep_20260719_225537.sql` cho thấy **toàn bộ Cambridge 19 đã bị xoá khỏi DB** (grep `Hinchingbrooke`/`CAMBRIDGE_19` = 0 kết quả, `mock_tests` chỉ còn 1 hàng) **nhưng `flyway_schema_history` vẫn ghi V30–V33 đã chạy** → Flyway sẽ **không bao giờ** tạo lại. Dữ liệu mất mà không tự phục hồi được.

Đề xuất **ranh giới cứng: Flyway chỉ làm schema, dữ liệu đề chỉ do `scripts/seed.py` ghi.** Cụ thể: nội dung nằm ở `scripts/seed_data/*.json`; `seed.py` dùng `INSERT ... ON DUPLICATE KEY UPDATE` trên `seed_key`, delete-then-insert cho question/option, tất cả trong một transaction; copy khung `argparse --dry-run|--confirm` của `import_cambridge.py:1377`.

**Không dùng Admin API để seed** — `AdminListeningService.createPart:118` **luôn** gọi `generateAudioAsync` và không nhận `audioUrl`, nên không trỏ được vào mp3 đã dựng sẵn.

Đồng thời cần quyết: **V30–V33 xử lý thế nào?** Chúng vẫn nằm trong repo nên **sẽ chạy trên database mới** và tái tạo đúng đống placeholder rác. Không sửa được file cũ (Flyway khoá checksum) → phải thêm migration mới xoá dữ liệu của chúng.

### 4. Audio — cần thêm dependency, và một xung đột thiết kế

Trong repo và trên máy này **không có gì để ghép audio**: không ffmpeg, không pydub, không thư viện audio nào trong `pom.xml`, không mp3 nào được track. Máy dev có Python 3.12 nhưng `import edge_tts` và `import pydub` đều `ModuleNotFoundError`, ffmpeg không có trên PATH.

Prompt B cần chèn khoảng lặng 30 giây — **edge-tts không làm được việc đó**. Ba lựa chọn:

| Phương án | Dependency mới | Đánh giá |
|---|---|---|
| Port thuật toán strip ID3 + nối byte thô từ `TtsService.java:206-256` sang Python | **Không** | Khả thi nhưng mong manh: phải tự tạo mp3 silence **cùng bitrate/sample-rate/channel**, nếu lệch thì audio hỏng |
| `pip install pydub` | **Có** (pydub **+ ffmpeg**) | Sạch nhất về code (`AudioSegment.silent(30000)`) |
| ffmpeg trực tiếp (`concat` + `anullsrc`) | **Có** (ffmpeg) | Chuẩn công nghiệp, nhưng thêm yêu cầu hệ thống |

Tôi khuyến nghị **ffmpeg**, nhưng theo nguyên tắc 9 của bạn tôi **hỏi trước khi thêm**.

Xung đột thiết kế: Prompt B yêu cầu *"ghép toàn bộ 4 part thành 1 mp3/đề"*, nhưng schema là **1 part = 1 row = 1 `audio_url`** và `MockTestService.java:527` lấy `audioUrl` theo từng part. Nếu 4 row cùng trỏ một file, thí sinh sẽ nghe lại từ đầu ở mỗi part. Đề xuất: **giữ 4 file riêng**, thêm file ghép chỉ khi cần bản "nghe liền mạch".

Về nơi đặt file: khuyến nghị `backend/src/main/resources/static/api/v1/listening/audio/*.mp3` — code đọc **đã có sẵn**, `audio_url` giữ đúng định dạng cũ, không sửa một dòng Java, không cần MinIO chạy. Đánh đổi: 24 part × ~7 phút ≈ **100-150 MB vào git và vào jar** → có thể cần Git LFS. Nếu bạn thấy nặng, phương án hai là MinIO theo mẫu `upload_cam19_audio.ps1`.

### 5. Voice bank không khớp

3 trong 6 voice Prompt B đề xuất (`en-GB-LibbyNeural`, `en-GB-MaisieNeural`, `en-GB-ThomasNeural`) **không nằm trong `EDGE_TTS_ALLOWED_VOICES`** → edge-tts trả 400. Chọn: dùng 6 voice đang có, hay mở rộng allowlist trong `docker-compose.yml`? Dù chọn gì tôi cũng sẽ chạy `edge-tts --list-voices` xác nhận trước khi hardcode.

### 6. Validator viết bằng gì — đề xuất Java JUnit 5

Đặt ở `backend/src/test/java/com/smartprep/seed/`, đọc `backend/src/main/resources/seed/*.json` qua classpath. Lý do: **tự chạy trong `ci-cd.yml:33` mà không phải sửa một dòng workflow**, và **zero dependency mới** (Jackson + JUnit5 + AssertJ đã có). Mọi phương án khác đều tốn hạ tầng: Node cần tạo `package.json` ở root và thêm step CI; Python không khai báo dependency ở đâu trong repo.

Kéo theo một quyết định: nếu validator là Java đọc classpath, nội dung nên nằm ở `backend/src/main/resources/seed/` chứ không phải `scripts/seed_data/` như mục 3. Tôi đề xuất **đặt ở `backend/src/main/resources/seed/`** và cho `seed.py` đọc từ đó — một nguồn sự thật duy nhất.

### 7. Nội dung seed xong vẫn chưa dùng được hết

Từ [SKILLS_STATUS.md](SKILLS_STATUS.md): frontend hiện **không render** `SHORT_ANSWER` và `DIAGRAM_LABEL_COMPLETION` (hiện "Unsupported question type"), MCQ Listening trong mock test render 0 lựa chọn, và bộ chấm **không enforce `word_limit`**, không hỗ trợ đáp án thay thế. Nghĩa là đề đúng chuẩn Prompt B vẫn có phần không làm được cho tới khi sửa. Bạn muốn sửa trước, hay seed trước rồi sửa sau?

---

## Tóm tắt: cần duyệt những gì

1. Bản quyền — phương án (a)/(b)/(c) cho artifact Cambridge 19.
2. Mô hình "một đề" — (a) mở rộng `mock_tests`, (b) bảng mới, hay (c) giữ pool phẳng.
3. Duyệt migration V42 ở trên (≈22 cột + 1 bảng mới + 4 unique key), và cách xử lý V30–V33.
4. Cho phép thêm **ffmpeg** (hoặc pydub) không.
5. Voice bank: dùng 6 voice có sẵn, hay mở rộng allowlist.
6. Validator bằng Java JUnit 5 + nội dung đặt ở `backend/src/main/resources/seed/`.
7. Thứ tự: sửa lỗi render/chấm trước, hay seed trước.

---

## ĐÃ CHỐT (2026-07-26)

| # | Quyết định |
|---|---|
| 1 | Gỡ artifact Cambridge khỏi **toàn bộ history**, không chỉ HEAD. `filter-repo` → **xoá repo GitHub và push lại** (force-push không đủ). Squash V1→V40 thành `V1__baseline.sql`, *với điều kiện* không có DB production |
| 2 | **Không** tái dùng `mock_tests`. Thêm bảng cha `practice_tests` + `practice_test_id`/`order_index`/`seed_key` trên `reading_quizzes` và `listening_parts`. Kèm sửa `ReadingAssemblyService`/`ListeningQueryService` để đọc đề cố định |
| 3 | Flyway = schema, dữ liệu = seeder riêng. Nhưng seeder viết bằng **Java** (`SeedRunner` là `CommandLineRunner` dưới profile `seed`), **không** phải `scripts/seed.py` — tái dùng JPA/Jackson/entity sẵn có, zero dep mới |
| 4 | **ffmpeg: duyệt** (gọi qua `subprocess`). **pydub: từ chối** (chỉ là lớp bọc, bên dưới vẫn cần ffmpeg). Build script gọi **thẳng thư viện `edge_tts`**, không qua HTTP `/synthesize` — né cả 3 giới hạn (5000 ký tự, 20000 byte query, 30 req/60s). ffmpeg là dep **máy dev**, không phải CI. **Bốn file mp3 mỗi đề, không ghép thành một** |
| 5 | Xung đột voice tự biến mất vì build không đi qua HTTP service. Vẫn phải chạy `edge-tts --list-voices` xác nhận voice còn tồn tại; voice nào mất thì **báo, không đoán tên gần giống** |
| 6 | Validator Java JUnit 5 — duyệt, thêm 2 ràng buộc: **không `@SpringBootTest`** (CI không có DB container) và dùng **`@ParameterizedTest` quét thư mục** thay vì hardcode tên file. Thay JSON Schema lib bằng Java `record` + Bean Validation; chính các record đó làm input model cho `SeedRunner` |
| 7 | **Sửa trước**, nhưng theo một danh sách đóng (gate list) chứ không phải sửa hết mọi finding |

### Trả lời 3 câu hỏi còn lại

**Q3 — Bảng Writing tên là `writing_prompts`** ([V3:1](../backend/src/main/resources/db/migration/V3__create_writing_tables.sql)). Toàn bộ cột nội dung hiện có: `prompt_id`, `prompt_text` TEXT, `essay_type` VARCHAR(50), `created_at`, `image_url` VARCHAR(512) (V7), `visual_data` TEXT (V26), `source`/`created_by`/`imported_at` (V38), `content_status` (V39). ⇒ `model_answer` / `outline_json` / `topic_vocab_json` / `checklist_json` / `fallback_scoring_json` đều là **thêm cột vào `writing_prompts`**, không cần bảng mới. Riêng `chart_data_json` **không cần thêm** — `visual_data` đã là chỗ đó, chỉ cần `MODIFY ... JSON`.

**Q2 — Repo ĐANG PUBLIC.** `gh repo view` trả `"visibility":"PUBLIC"`, 0 fork, 0 star, 0 issue, 1 PR đã merge, push gần nhất 2026-07-09. File `frontend/public/cam19_import_review.json` (**4.18 MB**) đang đọc được công khai tại `github.com/Ennela/ielts-smartprep/blob/main/frontend/public/cam19_import_review.json`. **Bản thân app thì không có dấu hiệu đã deploy**: `.github/workflows/ci-cd.yml` không có bước deploy/push image nào (grep `deploy|ssh|scp|registry|ghcr|render|fly|heroku|vercel` = 0 kết quả), README không nhắc domain nào. ⇒ Phơi nhiễm bản quyền là **qua GitHub, không phải qua app đã deploy**. Gate #5 (xoá khỏi `public/` + redeploy) **không gấp**; gate #6 (filter-repo + tạo lại repo) **mới là việc gấp**. Tin tốt: **0 fork** nghĩa là chưa ai clone qua fork, và chi phí xoá-tạo-lại repo gần như bằng 0 (mất đúng 1 bản ghi PR đã merge).

**Q1 — Nhiều khả năng KHÔNG có DB production**, nhưng đây là suy luận từ bằng chứng, cần bạn xác nhận dứt điểm: không có bước deploy nào trong CI; `.env` chỉ tồn tại local và bị `.gitignore` chặn; dump `backups/ielts_smartprep_20260719_225537.sql` là MySQL trong Docker local; `docker-compose.yml` bind cổng ra `0.0.0.0` kiểu môi trường dev. Nếu bạn xác nhận không có DB production nào ngoài máy này ⇒ **squash V1→V40 thành baseline là an toàn**.
