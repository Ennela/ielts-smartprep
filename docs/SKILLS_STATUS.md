# SKILLS_STATUS — Trạng thái 3 luồng Reading / Listening / Writing

> Rà soát bằng cách **đọc code thật**, không suy đoán, không dựa vào `docs/ARCHITECTURE.md` hay `docs/AUDIT.md` (2 tài liệu đó chỉ dùng để định hướng; mọi khẳng định ở đây đều được mở lại file gốc để xác minh).
> Mỗi phát hiện P0/P1 đã qua **một vòng phản biện độc lập**: người thẩm định mở đúng `file:dòng` và cố bác bỏ. Verdict được ghi rõ khi kết luận bị điều chỉnh.
> Đường dẫn tính từ `ielts-smartprep/`. Ngày rà soát: **2026-07-26**.
>
> **Quy ước trạng thái**
> - **XONG** — có code thật, chạy đúng.
> - **LỖI** — có code nhưng cho kết quả sai hoặc hỏng.
> - **THIẾU** — chưa cài đặt.
> - **GIẢ LẬP** — mock data, TODO, hardcode, hoặc endpoint/dữ liệu trả về đồ giả.

---

## 0. Tóm tắt điều hành

**Kết luận ngắn gọn:** phần *kiến trúc* của cả 3 luồng đã đúng — chấm điểm 100% ở backend, endpoint lấy đề không kèm đáp án, timer dựa trên deadline server. Nhưng phần *nội dung* và một số nhánh phụ thì chưa dùng được: **toàn bộ đề Cambridge 19 đã seed là dữ liệu giả**, và có **một endpoint AI trả thẳng vị trí đáp án cho thí sinh đang thi**.

### Việc phải làm trước khi sinh nội dung

| # | Vấn đề | Mức | Vì sao chặn việc sinh data |
|---|---|---|---|
| 1 | `POST /listening/ai-analyze/{questionId}` trả về vị trí đáp án trong transcript, không kiểm tra chủ sở hữu lẫn đã-nộp-hay-chưa | **P0** | Lỗ hổng lộ đáp án — sinh bao nhiêu đề cũng vô nghĩa |
| 2 | Seed Cambridge 19 Reading: passage là placeholder, nhiều `correct_answer = '__(answer)'`, MCQ/Matching không có options | **P0** | Đây chính là "sinh data vào schema đang lỗi" — phải xoá/làm lại |
| 3 | Seed Cambridge 19 Listening: 80/160 câu không thể trả lời trên UI; transcript NULL; audio phải upload tay | **P0** | Như trên |
| 4 | Schema thiếu: đáp án thay thế, multi-select MCQ, ảnh cho diagram/map, nhóm câu hỏi Listening | **P0** | **Sinh đề đầy đủ chuẩn Cambridge sẽ mất dữ liệu nếu chưa migrate** |
| 5 | 2 bảng quy đổi band Listening lệch nhau giữa standalone và mock test | **P0** | Cùng bài, 2 đường nộp cho 2 điểm khác nhau |
| 6 | IDOR `GET /listening/{testId}/result` — xem được bài + đáp án + transcript của user khác | **P0** | Đã có trong `docs/AUDIT.md`, nhắc lại vì thuộc luồng Listening |
| 7 | `/reading/submit-full` và `/listening/submit` cho nộp lại vô hạn, mà response nộp bài chứa đủ đáp án → **oracle** | **P0** | Nộp bừa lấy đáp án rồi nộp lại lấy band 9.0 — mọi điểm Reading full-test và Listening đều không đáng tin |

Tổng số phát hiện trong tài liệu này: **7 P0 · 20 P1 · 22 P2**.

---

## 1. READING

| Bước trong luồng | Trạng thái | file:line | Mô tả |
|---|---|---|---|
| 1. DB schema / migration | **GIẢ LẬP** | [V32__seed_cambridge19_reading.sql:79](../backend/src/main/resources/db/migration/V32__seed_cambridge19_reading.sql) | *Cấu trúc* bảng đầy đủ và đúng (V2 tạo bảng, V8 thêm `options_json`/`word_limit`/`group_*`, V13 template, V16 `question_options`, V24 evidence, V25 `module_type`). Nhưng *dữ liệu* seed là đồ giả — xem P0 #1 bên dưới. |
| 2. Entity model + repository | XONG | [ReadingQuestion.java:24](../backend/src/main/java/com/smartprep/model/entity/ReadingQuestion.java) | `ReadingQuiz` + `ReadingQuestion` map đủ cột; `ReadingQuizRepository.findByQuizIdAndUserUserId` chống truy cập chéo user. |
| 3. Service lấy đề | XONG | [ReadingAssemblyService.java:70](../backend/src/main/java/com/smartprep/service/ReadingAssemblyService.java) | `startTemplateQuiz` clone template thành quiz riêng của user (copy đủ options/evidence); `assembleMockTest` ghép 3 passage theo PASSAGE_1/2/3. |
| 4. API endpoint lấy đề | XONG | [ReadingController.java:76](../backend/src/main/java/com/smartprep/controller/ReadingController.java) | Response **không chứa `correctAnswer`**, `mapOptions(showCorrect=false)` set `isCorrect=null` ([ReadingQueryService.java:112](../backend/src/main/java/com/smartprep/service/ReadingQueryService.java)). `GET /{quizId}/result` chặn khi chưa nộp. |
| 5. FE api client | XONG | [readingApi.ts:22](../frontend/src/api/readingApi.ts) | Đủ hàm. Lỗi nhỏ: `getHistory` gửi `page/size` nhưng BE trả `List` không phân trang — param bị bỏ qua. |
| 6. FE render trang làm bài | **LỖI** | [QuestionPanel.jsx:214](../frontend/src/components/reading/QuestionPanel.jsx) | Render tốt MCQ / TFNG / YNNG / SENTENCE + SUMMARY_COMPLETION / 4 dạng MATCHING. Nhưng `switch` **không có case** `FILL_BLANK`, `SHORT_ANSWER`, `DIAGRAM_LABEL_COMPLETION` → rơi vào `default: "Unsupported question type"`, user không có ô nhập. |
| 7. Lưu đáp án tạm (chưa nộp) | XONG | [ReadingContext.jsx:46](../frontend/src/context/ReadingContext.jsx) | Mỗi `SET_ANSWER` ghi `localStorage` key `reading_quiz_draft_{quizId}`, đọc lại khi load, xoá khi có kết quả. **F5 không mất bài.** Full test dùng `reading_full_draft_{quizIds}`. |
| 8. Timer (persist khi F5) | XONG (có 1 lỗi phụ) | [useExamTimer.js:42](../frontend/src/hooks/useExamTimer.js) | Server-authoritative: `ExamAttempt` có `deadline` do server đặt, FE chỉ tính `remaining` từ deadline. F5 giữ nguyên giờ. **Lỗi phụ P1:** attempt được tra chỉ theo `(userId, skillType)` — xem P1 #5. |
| 9. Submit | XONG | [ReadingSubmitRequest.java:16](../backend/src/main/java/com/smartprep/dto/request/ReadingSubmitRequest.java) | Client chỉ gửi `answers` + `attemptId` + `autoSubmitted`, **không gửi điểm**. `submitQuiz` chặn nộp lại; `submitFullQuiz` thì không (P2). |
| 10. Chấm điểm | **LỖI** | [IeltsScoringUtils.java:199](../backend/src/main/java/com/smartprep/service/util/IeltsScoringUtils.java) | Chấm ở backend, nhưng thuật toán thiếu: không hỗ trợ đáp án thay thế, không bỏ dấu câu, không enforce `word_limit`. Bảng band 1-passage lỗi độc lập với tổng số câu — xem P1 #2, #6. |
| 11. Lưu kết quả | XONG | [ReadingGradingService.java:93](../backend/src/main/java/com/smartprep/service/ReadingGradingService.java) | Lưu `quiz.score`, `ScoreHistory` (kèm `timeSpentSeconds`, `autoSubmitted`, `moduleType`) và snapshot `UserAnswer` đầy đủ cho từng câu. |
| 12. Trang kết quả + lịch sử | XONG | [ReadingResultPage.jsx:474](../frontend/src/pages/ReadingResultPage.jsx) | Band ring, đúng/sai từng câu, **highlight evidence trong passage** (chỉ sau khi nộp). `ReadingHistoryPage` → `/history/{historyId}/review` đọc `user_answers`. |
| 13. Reading trong Mock Test | **LỖI** | [MockTestService.java:283](../backend/src/main/java/com/smartprep/service/MockTestService.java) | Đường đi khác hẳn standalone: không clone template, timer do client gửi, đáp án 3 kỹ năng gộp 1 map phẳng, **không ghi `score_history`**. |

### Phát hiện Reading

**P0 #1 — Seed Cambridge 19 Reading toàn placeholder** · GIẢ LẬP · [V32:79](../backend/src/main/resources/db/migration/V32__seed_cambridge19_reading.sql)
Dòng 2 tự ghi `-- Passage texts are placeholders`; `passage_text` chỉ là `'[Cambridge 19 Test 1 Passage 1: How tennis rackets have changed]'`; Test 2-4 có hàng loạt `FILL_BLANK` với `correct_answer = '__(answer)'`; MCQ (kể cả đáp án `'E'` ở dòng 43) và MATCHING **không hề có options**.
*Thẩm định (CONFIRMED):* đã kiểm 5 đường có thể cứu — không migration nào bổ sung options sau V32; V39 dòng 8 `UPDATE` tất cả thành `PUBLISHED` nên không có filter nào chặn; `findQuizzesForAdmin('ADMIN')` trả về chúng ở `GET /reading/templates`; `cloneTemplateForUser` copy nguyên đáp án giả sang quiz user; V33 link thẳng 12 quiz này vào 4 mock test. Người dùng phải gõ đúng chuỗi `__(answer)` mới được tính đúng.

**P1 #2 — Chấm fill-in thiếu 3 quy tắc IELTS cơ bản** · THIẾU · [IeltsScoringUtils.java:235](../backend/src/main/java/com/smartprep/service/util/IeltsScoringUtils.java)
```java
private static String normalizeCompletionText(String text) {
    return text.toLowerCase()
            .replaceAll("^(the|a|an)\\s+", "")
            .replaceAll("\\s+", " ")
            .trim();
}
```
Sau đó so `normCorrect.equals(normAnswer)`. Hệ quả: (a) đáp án Cambridge dạng `'coal / charcoal'` — user gõ `coal` bị chấm **sai**; (b) user gõ `'paint.'` thừa dấu chấm bị chấm **sai**; (c) `word_limit` (cột đã có từ V8) chỉ hiển thị badge *"Maximum N words"*, **không được enforce** ở cả FE lẫn BE — gõ 5 từ cho câu "NO MORE THAN TWO WORDS" vẫn được chấm bình thường.
*Thẩm định (CONFIRMED):* đã grep mọi call site `isReadingCorrect` (`ReadingGradingService:68,103,133`; `MockTestService:284,361`; `ReadingQueryService:146`) — không nơi nào tiền xử lý đáp án. `normalizeSpelling` (có strip ký tự đặc biệt) chỉ dùng cho Listening.

**P1 #3 — FE không render 3 dạng câu hỏi** · THIẾU · [QuestionPanel.jsx:214](../frontend/src/components/reading/QuestionPanel.jsx)
Backend có đủ 12 `QuestionType` và chấm được cả 3 dạng; AI generator sinh ra chúng; seed V32 dùng `FILL_BLANK` dày đặc — nhưng FE (cả standalone lẫn `MockTestQuestionPanel.jsx:173`) hiện *"Unsupported question type"*. User bắt buộc bị 0 điểm các câu đó.

**P1 #4 — Timer Mock Test do client tự đếm, tự báo** · LỖI · [MockTestService.java:142](../backend/src/main/java/com/smartprep/service/MockTestService.java)
`session.setTimeRemainingSeconds(request.getTimeRemainingSeconds())` — server nhận nguyên giá trị client gửi, `MockTestProgressRequest` chỉ có `@NotNull`, không `@Min/@Max`, không clamp. FE tự trừ 1 giây mỗi tick, sync 30 giây/lần.
*Thẩm định (CONFIRMED):* `sectionStartedAt` được set nhưng **không dùng ở đâu** để đối chiếu; không có `@Scheduled` nào expire session; `submitExam` không kiểm deadline. Hệ quả: F5 được "hoàn" tới 30 giây; client sửa request có thể kéo dài bài thi vô hạn.

**P1 #5 — `startAttempt` tra attempt chỉ theo `(userId, skillType)`, bỏ qua đề đang làm** · LỖI · [ExamAttemptService.java:49](../backend/src/main/java/com/smartprep/service/ExamAttemptService.java)
```java
Optional<ExamAttempt> existing = attemptRepository
        .findByUserUserIdAndSkillTypeAndStatus(userId, skillType, SessionStatus.IN_PROGRESS);
```
`examReferenceIds` được **ghi** lúc tạo (dòng 86) nhưng **không bao giờ được so** lúc tra cứu. Bỏ dở quiz A rồi mở quiz B → B thừa hưởng deadline còn lại của A (có thể chỉ còn vài phút), và khi nộp B thì `timeSpentSeconds` ghi vào `score_history` được tính từ lúc bắt đầu A. Ảnh hưởng **cả 3 kỹ năng**. `ExamAttemptServiceTest.java:111` có test resume nhưng không phủ case khác đề.

**P1 #6 — `BAND_SCORES_13` chấm không quan tâm tổng số câu** · LỖI · [ReadingGradingService.java:190](../backend/src/main/java/com/smartprep/service/ReadingGradingService.java)
```java
private BigDecimal calculateBandScore(int correctAnswers) {
    int idx = Math.min(Math.max(correctAnswers, 0), BAND_SCORES_13.length - 1);
    return BAND_SCORES_13[idx];
}
```
Không dùng `quiz.getTotalQuestions()`, trong khi `validateReadingQuiz` chỉ đòi ≥1 câu. Quiz 10 câu đúng tuyệt đối 10/10 → `BAND_SCORES_13[10]` = **Band 6.5**. Bảng này còn bỏ hẳn band 7.0 và 8.0 (10→6.5, 11→7.5, 12→8.5).

**P2 khác:** ~~`submit-full` không chặn nộp lại~~ → **đã nâng lên P0 #7**, xem mục 4 · ghép quiz↔history bằng heuristic "điểm bằng nhau + timestamp ±5s" ([ReadingQueryService.java:72](../backend/src/main/java/com/smartprep/service/ReadingQueryService.java)) làm nút Review có thể biến mất · `ReadingFullResultPage` chỉ đọc `location.state` → F5 mất kết quả · bài 1 passage luôn được cấp 60 phút dù `ReadingConfigPage` quảng cáo 10/15/20 phút · Reading trong mock test không vào `score_history` · `/reading/templates` và `/assemble` không lọc `content_status` nên template DRAFT vẫn phát cho học viên · `PassageViewer.jsx:11` hardcode badge *"IELTS Academic"* cho cả đề General Training.

---

## 2. LISTENING

| Bước trong luồng | Trạng thái | file:line | Mô tả |
|---|---|---|---|
| 1. DB schema / migration | XONG (cấu trúc) | [V4__create_listening_tables.sql:1](../backend/src/main/resources/db/migration/V4__create_listening_tables.sql) | 4 bảng + `audio_status` (V18) + metadata (V19) + `content_status` (V39). Cấu trúc đúng; **dữ liệu seed** thì hỏng — xem P0 #3, P1 #9. |
| 2. Entity model + repository | XONG | [ListeningPart.java:13](../backend/src/main/java/com/smartprep/model/entity/ListeningPart.java) | Khớp schema, `findRecentPartIds` để tránh lặp đề trong 7 ngày. |
| 3. Service lấy đề | XONG | [ListeningQueryService.java:53](../backend/src/main/java/com/smartprep/service/ListeningQueryService.java) | DTO **không lộ** `correctAnswer` lẫn `transcript`; `assembleMockTest` chọn random 1 part cho mỗi partNumber 1-4. |
| 4. API endpoint lấy đề | XONG | [ListeningController.java:38](../backend/src/main/java/com/smartprep/controller/ListeningController.java) | Yêu cầu đăng nhập, response sạch. `/audio/{fileName}` là `permitAll` + hỗ trợ Range (để seek). |
| 5. FE api client | XONG | [listeningApi.js:23](../frontend/src/api/listeningApi.js) | Hàm chính khớp BE. Có 3 method chết trỏ tới endpoint không tồn tại (P2). |
| 6. FE render trang làm bài | **LỖI** | [ListeningExamPage.jsx:532](../frontend/src/pages/ListeningExamPage.jsx) | `FillBlankQuestion` chỉ render ô input khi `questionText` chứa `'___'`; `McqQuestion` fallback chỉ render option khi text có dòng option. Câu seed Cambridge 19 không thoả cả hai → **không có ô nhập nào**. |
| 7. Lưu đáp án tạm | **THIẾU** | [ListeningExamPage.jsx:28](../frontend/src/pages/ListeningExamPage.jsx) | `const [answers, setAnswers] = useState({})` — **không** localStorage, **không** autosave. F5 mất trắng đáp án trong khi timer server vẫn chạy tiếp. Trái ngược mock test (có autosave 30s + localStorage). |
| 8. Timer | XONG (2 lỗi phụ) | [useExamTimer.js:17](../frontend/src/hooks/useExamTimer.js) | Server-authoritative, resume sau F5 qua `sessionStorage` attemptId. Lỗi phụ: server không từ chối bài nộp trễ deadline (P1 #12); dính chung lỗi `startAttempt` ở Reading P1 #5, còn nặng hơn vì key sessionStorage là **toàn cục** `'listening_attemptId'`. |
| 9. Submit | XONG | [ListeningSubmitRequest.java:11](../backend/src/main/java/com/smartprep/dto/request/ListeningSubmitRequest.java) | Client gửi `testMode`, `partIds`, `answers`, không gửi điểm. |
| 10. Chấm điểm | **LỖI** | [ListeningGradingService.java:201](../backend/src/main/java/com/smartprep/service/ListeningGradingService.java) | Chấm ở backend nhưng **2 bảng band lệch nhau** (P0 #5) và logic bị copy 3 nơi. Không quy đổi số↔chữ (P1 #11). Bài <40 câu bị scale `(correct/total)*40` → làm 1 part đúng 5/5 ra **Band 9.0**. |
| 11. Lưu kết quả | XONG | [ListeningGradingService.java:108](../backend/src/main/java/com/smartprep/service/ListeningGradingService.java) | `ListeningTest` + `ListeningTestPart` (`user_answers_json`) + `ScoreHistory` + snapshot `UserAnswer`. Không có FK sang `score_history` → phải match heuristic (P2). |
| 12. Trang kết quả + lịch sử | **GIẢ LẬP** | [ListeningResultPage.jsx:19](../frontend/src/pages/ListeningResultPage.jsx) | `useEffect` chỉ có comment stub *"could fetch from API in the future"* — thân rỗng. F5 / mở link trực tiếp = **spinner vô hạn**, dù backend đã có sẵn `GET /listening/{testId}/result`. |
| 13. Pipeline sinh audio (TTS) | **GIẢ LẬP** | [ListeningController.java:236](../backend/src/main/java/com/smartprep/controller/ListeningController.java) | Xem mục "Audio ở đâu ra" bên dưới. Khi không tìm thấy file, backend trả **MP3 câm 1 giây** hardcode base64 với HTTP 200. |
| 14. Seek / giới hạn nghe lại | **GIẢ LẬP** | [AudioPlayer.jsx:40](../frontend/src/components/listening/AudioPlayer.jsx) | Chế độ mock-test chặn replay + khoá seek, nhưng **100% enforce ở FE** → bypass hoàn toàn (P1 #13). |
| 15. Xử lý khi TTS fail | **LỖI** | [ListeningExamPage.jsx:100](../frontend/src/pages/ListeningExamPage.jsx) | FE coi `FAILED` như đã sẵn sàng → vào thi với audio câm, không báo lỗi, không có nút retry (P1 #10). |
| 16. Listening trong Mock Test | **LỖI** | [MockTestService.java:245](../backend/src/main/java/com/smartprep/service/MockTestService.java) | Band map khác standalone, timer client-authoritative, không ghi `score_history`, không chờ audio READY, MCQ render 0 lựa chọn (P1 #14). |

### Phát hiện Listening

**P0 #4 — IDOR: `GET /listening/{testId}/result` trả bài của bất kỳ ai** · LỖI · [ListeningController.java:88](../backend/src/main/java/com/smartprep/controller/ListeningController.java)
```java
@GetMapping("/{testId}/result")
public ResponseEntity<ApiResponse<ListeningTestResponse>> getTestResult(@PathVariable Long testId) {
    return ResponseEntity.ok(ApiResponse.ok(listeningQueryService.getTestResult(testId)));
}
```
Không có `@AuthenticationPrincipal`; service `getTestResult(Long testId)` **về mặt chữ ký đã không thể** kiểm tra chủ sở hữu. Payload gồm `userAnswer`, `correctAnswer` và **transcript đầy đủ mọi part**. Đoán `testId` tuần tự là đọc được toàn bộ bài làm hệ thống — và lấy được đáp án của đề mình sắp làm. *(Đã có trong `docs/AUDIT.md`; nhắc lại vì nằm trong luồng Listening.)*

**P0 #5 — Hai bảng quy đổi band Listening lệch nhau** · LỖI · [ListeningGradingService.java:60](../backend/src/main/java/com/smartprep/service/ListeningGradingService.java)

| Số câu đúng | Standalone (`BAND_SCORE_MAP`) | Mock test (`IeltsScoringUtils.LISTENING_BAND_MAP`) |
|---|---|---|
| 7 | 3.0 | 3.5 |
| 6 | 3.0 | 3.5 |
| 5 | 2.5 | 3.0 |
| 4 | 2.5 | 3.0 |
| 3 | 2.0 | 2.5 |
| 0 | 0.0 | 1.0 |

Cùng một bài, nộp qua `/listening/submit` và qua Full Mock Test cho **band khác nhau**. Ít nhất một trong hai sai. Logic `checkAnswer`/`normalizeSpelling` còn bị copy ở **3 nơi** (`ListeningGradingService:184`, `ListeningQueryService:203`, `IeltsScoringUtils:177`) — hiện giống nhau nhưng là 3 bản độc lập, sẽ tiếp tục phân kỳ.
*Thẩm định (CONFIRMED):* không có nơi nào đồng bộ 2 map; không test nào phủ `ListeningGradingService` — nếu có test đối chiếu thì lỗi này đã lộ từ lâu.

**P0 #3 — Seed Cambridge 19 Listening: 80/160 câu không thể trả lời** · GIẢ LẬP · [V30:24](../backend/src/main/resources/db/migration/V30__seed_cambridge19_listening.sql)
```sql
(@t1p2, 'MCQ', 'Choose the correct answer (Q11).', 'B', 1)
(@t1p2, 'FILL_BLANK', 'Match item 16 to the correct letter.', 'G', 6)
```
Không có nội dung câu hỏi, không có options, không có `'___'`.
*Thẩm định (ADJUSTED — con số ban đầu sai):* đếm lại trong V30 ra **80/160 câu**, không phải ~60: 20 câu MCQ (4 bài × 5) + 60 câu FILL_BLANK giả matching (part 2: 5 câu × 4 bài; part 3: 10 câu × 4 bài) — tức **toàn bộ part 2 và part 3 của cả 4 bài**. Trên UI: `McqQuestion` split `questionText` rồi `slice(1)` → mảng rỗng → 0 radio button; `FillBlankQuestion` split `'___'` → 1 phần tử → 0 ô input.

**P1 #9 — Audio seed V6 không tồn tại nhưng bị ép READY** · GIẢ LẬP · [V18:6](../backend/src/main/resources/db/migration/V18__add_audio_status_to_listening_parts.sql)
```sql
-- Mark all existing seeded parts as READY (they have placeholder audio but are usable)
UPDATE listening_parts SET audio_status = 'READY' WHERE part_id > 0;
```
*Thẩm định (CONFIRMED):* `find` toàn project không có file `.mp3` nào; `resources/static` chỉ chứa `review_portal.html`; script upload duy nhất chỉ map 16 file cam19. 8 part này có transcript đầy đủ nhưng **audio là file câm 1 giây vĩnh viễn** — và vì status = READY nên `ListeningAudioService.generateAudio` từ chối sinh lại (dòng 34), chỉ admin `regenerate-audio` mới ép được.

**P1 #10 — TTS fail: user thi với audio câm mà không hề biết** · LỖI · [ListeningExamPage.jsx:100](../frontend/src/pages/ListeningExamPage.jsx)
`needsPolling` chỉ xét `'PENDING'` → part `FAILED` đi thẳng vào bài thi; dòng 253 `readyCount` đếm cả `FAILED` là ready. Không có error state, và grep `generate-audio` trong `frontend/src` chỉ ra 1 kết quả duy nhất ở `adminApi.js:82` → **user không có nút retry**. Chiều ngược lại: nếu part kẹt `PENDING`, vòng `setTimeout(poll, 3000)` **không giới hạn số lần** → spinner *"This process typically takes 10-20 seconds"* treo vĩnh viễn.

**P1 #11 — Không quy đổi số ↔ chữ, trong khi seed lưu đáp án dạng chữ** · LỖI · [ListeningGradingService.java:193](../backend/src/main/java/com/smartprep/service/ListeningGradingService.java)
`normalizeSpelling` chỉ có 7 cặp Anh–Mỹ hardcode + bỏ ký tự đặc biệt. Nhưng seed V6 lưu `correct_answer` là `'eighty-five'`, `'twenty-five'`, `'Forty-seven'`, `'twelve thousand'`. IELTS chấp nhận `85` cho `eighty-five` — hệ thống chấm **sai**.

**P1 #12 — Server không cưỡng chế deadline; `durationOverride` do client quyết** · THIẾU · [ListeningGradingService.java:132](../backend/src/main/java/com/smartprep/service/ListeningGradingService.java)
`attemptId` là optional và không có check `now > attempt.getDeadline()` để từ chối. Timer được quảng cáo "server-authoritative" nhưng thực chất chỉ auto-submit ở FE; server nhận bài nộp bất kỳ lúc nào. Client cũng có thể `POST /attempts/start` với `durationOverride` tuỳ ý.

**P1 #13 — Giới hạn nghe-1-lần chỉ là trang trí** · GIẢ LẬP · [AudioPlayer.jsx:40](../frontend/src/components/listening/AudioPlayer.jsx)
`playedSources` là `useState` trong component → **mất khi F5, nghe lại được ngay**. URL audio là `permitAll` (không cần cả JWT) và hỗ trợ Range request → mở tab mới là tua/nghe lại tuỳ ý. Server **không đếm số lần phát**.

**P1 #14 — Mock test: 34 câu MCQ Listening render 0 lựa chọn** · LỖI · [MockTestSessionPage.jsx:511](../frontend/src/pages/MockTestSessionPage.jsx)
V16 dòng 44 đã `UPDATE listening_questions SET question_text = @stem` — **cắt các dòng option ra khỏi `question_text`** và chuyển sang bảng `question_options`. Nhưng `McqQuestion` của mock test vẫn parse option bằng `questionText.split('\n').slice(1)` và **không đọc `question.options`**, dù `MockTestService.java:535` có gửi options xuống. `ListeningExamPage.jsx:487` thì có check `question.options` trước — nên đây là lỗi **riêng của nhánh mock test**, khác với lỗi seed placeholder ở trên.

**P2 khác:** ghép lịch sử bằng heuristic ±5 giây · mock test không ghi `ScoreHistory` cho Listening · transcript AI hiển thị lẫn marker `[ANS_x]` cho user · 3 method chết trong `listeningApi.js` · `getAllParts`/`assembleMockTest` **không lọc `content_status`/`createdBy`** nên part AI do user A sinh ra hiện trong danh sách của mọi user · practice 1 part được cấp 32 phút · **0 test** cho `ListeningGradingService.submitTest` · edge-tts rate limit 30 req/phút/IP dễ làm hỏng part hội thoại dài.

### Audio ở đâu ra — trả lời trực tiếp

**Sinh lúc nào:** *request time*, **không phải build time**. Ba nguồn khác nhau:

1. **Seed V6 (8 part)** — audio **không bao giờ được sinh**. `audio_url` trỏ tới `part1a.mp3` v.v. nhưng file không tồn tại ở đâu; V18 ép `READY` nên pipeline TTS từ chối chạy → phát MP3 câm.
2. **Seed V30 Cambridge 19 (16 part)** — file mp3 bản quyền nằm **ngoài repo**, phải ops chạy tay `scripts/upload_cam19_audio.ps1` (`docker exec` vào MinIO). Chưa chạy = phát MP3 câm, không báo lỗi. `transcript_text = NULL` nên cũng **không thể** tự sinh lại bằng TTS (`AudioGenerationService` yêu cầu transcript, dòng 49-51).
3. **Part AI-generated** — sinh tại request time, **async** trên `ttsExecutor` (core 2 / max 5): Gemini viết script kèm marker `[ANS_x]` → lưu part với `audioStatus=PENDING` → strip marker + speaker prefix → gọi edge-tts **một request cho mỗi lượt thoại** (6 giọng neural luân phiên) → ghép các đoạn MP3 (bỏ ID3) → upload MinIO bucket `listening-audio` → set `READY`. FE poll 3 giây/lần khi PENDING.

**Audio và transcript có khớp không:** part AI **khớp** (TTS đọc đúng transcript đã clean); part V6 có transcript nhưng audio câm → **không khớp**; cam19 không có transcript để đối chiếu.

---

## 3. WRITING

| Bước trong luồng | Trạng thái | file:line | Mô tả |
|---|---|---|---|
| 1. DB schema / migration | XONG | [V3__create_writing_tables.sql:8](../backend/src/main/resources/db/migration/V3__create_writing_tables.sql) | Đủ 4 cột điểm tiêu chí, `error_list_json`, `rewritten_version`; V7 thêm `image_url`, V26 thêm `visual_data` + bảng full submission, V35 thêm timer. Khớp entity. |
| 2. Entity model + repository | XONG | [WritingPrompt.java:27](../backend/src/main/java/com/smartprep/model/entity/WritingPrompt.java) | Repository có guard sở hữu (`findBySubmissionIdAndUserUserId`) — không lộ bài người khác. |
| 3. Service lấy đề | **LỖI** | [WritingPromptService.java:44](../backend/src/main/java/com/smartprep/service/WritingPromptService.java) | `toResponse()` **không set `visualData`** → chart của đề AI không bao giờ tới FE. Cũng không lọc `content_status` → đề DRAFT hiện cho user thường. |
| 4. API endpoint lấy đề | XONG | [WritingController.java:36](../backend/src/main/java/com/smartprep/controller/WritingController.java) | `/prompts`, `/prompts/{id}`, `/assemble`, `/generate-mock`. |
| 5. FE api client | XONG | [writingApi.ts:12](../frontend/src/api/writingApi.ts) | Đủ 10 hàm. `getPrompts`/`getHistory` khai kiểu `PaginatedResult` nhưng BE trả `List` phẳng — phân trang là giả (P2). |
| 6. FE render trang làm bài | **LỖI** | [WritingFullExamPage.jsx:102](../frontend/src/pages/WritingFullExamPage.jsx) | `WritingEditorPage` (bài đơn) render tốt. Nhưng full-exam đọc sai tên query param → **flow thi Writing full chết hoàn toàn** (P1 #15). |
| 7. Autosave bài viết | **LỖI** | [WritingEditorPage.jsx:395](../frontend/src/pages/WritingEditorPage.jsx) | **Không nhất quán:** full-exam CÓ autosave `localStorage` mỗi keystroke; mock test CÓ (localStorage + sync server); **`WritingEditorPage` — flow chính — KHÔNG có gì** → F5 mất trắng bài (P1 #17). |
| 8. Timer | XONG | [useExamTimer.js:29](../frontend/src/hooks/useExamTimer.js) | Server-authoritative (deadline 3600s), resume qua `sessionStorage`, có cảnh báo 5 phút/1 phút, chống lệch timezone. **Có test**: `useExamTimer.test.js`, `useWritingTaskTimer.test.js`, `WritingEditorPage.timer.test.jsx`. (Vẫn dính lỗi `startAttempt` ở Reading P1 #5.) |
| 9. Submit | **LỖI** | [WritingFullExamPage.jsx:66](../frontend/src/pages/WritingFullExamPage.jsx) | DTO và endpoint đúng, nhưng **hết giờ mà chưa đủ từ thì mất bài** ở cả 2 flow (P1 #18, #19). |
| 10. Chấm điểm | **LỖI** | [WritingGradingService.java:68](../backend/src/main/java/com/smartprep/service/ai/WritingGradingService.java) | Chấm thật bằng Gemini với schema JSON cố định (chi tiết bên dưới) — phần này làm tốt. Lỗi: **không gửi biểu đồ cho AI** (P1 #16). |
| 11. Lưu kết quả | XONG | [WritingAssemblyService.java:121](../backend/src/main/java/com/smartprep/service/WritingAssemblyService.java) | Lưu submission + `ScoreHistory` + timer fields. `@Transactional` nên Gemini fail → rollback, không lưu bài rác. |
| 12. Trang kết quả + lịch sử | XONG | [WritingResultPage.jsx:41](../frontend/src/pages/WritingResultPage.jsx) | Vòng điểm + 4 thanh tiêu chí + tab Error Analysis + tab Upgraded Essay. Hạn chế: `improvementNotes` không có cột DB nên luôn rỗng khi xem lại (P2). |
| 13. Writing trong Mock Test | **LỖI** | [MockTestService.java:309](../backend/src/main/java/com/smartprep/service/MockTestService.java) | Chấm async có màn hình FAILED — tốt. Nhưng **bỏ qua validate 150/250 từ**, **không ghi `score_history`**, và 4 đề Task 1 Cam19 có `image_url = NULL`. |
| 14. Sinh đề AI | XONG | [WritingGenerationService.java:80](../backend/src/main/java/com/smartprep/service/ai/WritingGenerationService.java) | Sinh cặp Task1+Task2 kèm `visualData` đúng format Recharts. Nhưng chuỗi phía sau đứt (P1 #15, #20). |
| 15. Test coverage | XONG | [WritingGradingServiceTest.java:27](../backend/src/test/java/com/smartprep/service/ai/WritingGradingServiceTest.java) | BE có `WritingGradingServiceTest`, `WritingServiceTest`, `WritingAssemblyServiceTest`, `GeminiClientTest`. Đây là kỹ năng **được test tốt nhất** trong 3. |

### Phát hiện Writing

**P1 #15 — Flow thi Writing Full không thể bắt đầu (sai tên query param)** · LỖI · [WritingFullExamPage.jsx:102](../frontend/src/pages/WritingFullExamPage.jsx)
Trang đọc `searchParams.get('task1')` / `get('task2')`, trong khi **cả hai** nơi navigate ([WritingPromptListPage.jsx:56 và :71](../frontend/src/pages/WritingPromptListPage.jsx)) đều dùng `?task1Id=...&task2Id=...` → `t1Id` luôn `null` → `setError('Missing task prompts')`.
*Thẩm định (CONFIRMED):* grep `full-exam` toàn `frontend/src` chỉ ra đúng 2 chỗ navigate (đều sai param); route trong `App.jsx:144` trỏ thẳng vào trang, không có wrapper chuẩn hoá; không có fallback đọc từ storage. **Cả nút "Start Mock Test (Both Tasks)" lẫn "AI Mock Test" đều chết.**

**P1 #16 — Chấm Task 1 không gửi biểu đồ cho Gemini** · LỖI · [WritingGradingService.java:68](../backend/src/main/java/com/smartprep/service/ai/WritingGradingService.java)
```java
String userPrompt = String.format("IELTS Writing %s Prompt:\n\"%s\"\n\nStudent Essay:\n%s",
        isTask1 ? "Task 1" : "Task 2", promptText, essayText);
```
Không có `imageUrl`, không có `visualData`. Trong khi `TASK1_GRADING_SYSTEM_PROMPT` (dòng 370) **yêu cầu AI đánh giá "data accuracy"**.
*Thẩm định (CONFIRMED):* đã loại trừ 4 đường — mọi caller chỉ truyền `promptText`; `GeminiClient.buildRequestBody` chỉ gửi `parts` dạng text, **không có `inline_data`/image**; `getVisualData`/`getImageUrl` chỉ xuất hiện ở DTO mapper và AdminService; số liệu của đề AI nằm riêng ở cột `visual_data`. Kết quả: **học viên bịa số liệu vẫn có thể được khen "data accuracy" tốt** — điểm TA của Task 1 không đáng tin.

**P1 #17 — `WritingEditorPage` không autosave** · THIẾU · [WritingEditorPage.jsx:395](../frontend/src/pages/WritingEditorPage.jsx)
Grep `localStorage` trong file = **0 kết quả**; `sessionStorage` chỉ lưu `attemptId`. Reload trong 60 phút làm bài → mất toàn bộ nội dung, trong khi attempt vẫn resume được (timer chạy tiếp nhưng bài thì không). Full-exam và mock test đều có draft — riêng flow chính thiếu.

**P1 #18 — Auto-submit full test luôn thất bại nếu bài dưới min words** · LỖI · [WritingFullExamPage.jsx:66](../frontend/src/pages/WritingFullExamPage.jsx)
Hết giờ → gửi placeholder `'(Time expired - no answer)'` (5 từ) → BE `validateMinimumWordCount` ném `WordCountTooLowException` (422) **trước khi đọc cờ `autoSubmitted`** → **cả 2 bài đều không được chấm**, kể cả task đã viết đủ. User chỉ thấy `alert('Auto-submit failed...')` nhưng thời gian đã hết.

**P1 #19 — Editor đơn: hết giờ mà chưa đủ từ → bài viết bị huỷ hoàn toàn** · LỖI · [WritingEditorPage.jsx:179](../frontend/src/pages/WritingEditorPage.jsx)
```javascript
} else { sessionStorage.removeItem(sessionKey);
         alert(`⏰ Time is up! ... The essay was not graded...`);
         navigate('/writing', { replace: true }); }
```
Attempt đã đóng vĩnh viễn ở dòng 162; essay chỉ tồn tại trong state của component sắp unmount; trang vốn không autosave. **Công sức viết 100+ từ mất sạch, không cách nào khôi phục.**

**P1 #20 — Cụm lỗi "biểu đồ vô hình"** (3 lỗi độc lập cùng gây 1 hậu quả)
- `GET /prompts/{id}` strip mất `visualData` ([WritingPromptService.java:44](../backend/src/main/java/com/smartprep/service/WritingPromptService.java)) — DTO có field nhưng builder không set.
- `WritingFullExamPage` **chỉ** render `visualData`, không render `imageUrl` (dòng 357); `WritingEditorPage` thì ngược lại (dòng 380) — hai trang lệch nhau.
- 4 đề Task 1 Cambridge 19 seed `image_url = NULL` ([V31:5](../backend/src/main/resources/db/migration/V31__seed_cambridge19_writing.sql)) **dù file `cam19_test1_task1.png`…`test4` đã có sẵn** trong `frontend/public/images`.

Hậu quả cộng dồn: Task 1 của cả 4 mock test Cambridge hiển thị đề chữ *"The graph below shows…"* mà **không có graph nào**; chart do AI sinh ra thì không bao giờ hiển thị được.

**P2 khác:** `improvementNotes` không có cột DB nên mất khi xem lại · mock test bỏ qua validate min words (bài 10-149 từ vẫn tốn 4 lượt Gemini) · `/writing/history` không set `timeSpentSeconds`/`autoSubmitted` dù DTO và FE đều dùng · đề DRAFT lộ cho user thường · phân trang giả · `extractScore` âm thầm default **5.0** khi Gemini trả field không phải số ([WritingGradingService.java:222](../backend/src/main/java/com/smartprep/service/ai/WritingGradingService.java)) — `validateGradingJson` chỉ check `has(field)`, không check `isNumber()`.

### Chấm Writing bằng gì — trả lời trực tiếp

| Câu hỏi | Trả lời |
|---|---|
| Model | Gemini 2.5 Flash (`application.yml:54`), timeout 60s, Resilience4j retry ×3 + circuit breaker. **Gọi thật, không mock.** |
| Prompt ở đâu | Hardcode trong [WritingGradingService.java](../backend/src/main/java/com/smartprep/service/ai/WritingGradingService.java): `GRADING_SYSTEM_PROMPT` (Task 2) dòng **236-311**, `TASK1_GRADING_SYSTEM_PROMPT` dòng **339-415**, prompt viết lại bài dòng **313-337** và **417-442**. Nội dung là band descriptor IELTS thật cho band 4-9 từng tiêu chí. |
| Output có schema cố định không | **Có.** `responseMimeType: application/json` (`GeminiClient.java:169`), parse bằng Jackson rồi `validateGradingJson` bắt buộc đủ 4 điểm tiêu chí + `generalFeedback` + `errors[]`. Không parse text tự do. |
| Đủ 4 tiêu chí TA/CC/LR/GRA | **Đủ.** Mỗi điểm clamp 0-9, làm tròn 0.5. Overall **không tin AI** mà server tự tính lại theo luật làm tròn IELTS; full test = (T1 + 2×T2)/3. |
| Đếm từ có đúng không | **Đúng và khớp FE-BE.** Cả hai dùng `trim().split(/\s+/)` (FE: `WritingEditorPage.jsx:205`, `WritingFullExamPage.jsx:230`, `MockTestSessionPage.jsx:117`; BE: `WritingGradingService.java:45`, `WritingAssemblyService.java:185`). `well-known` = 1 từ, số = 1 từ, nhiều space/newline gộp đúng ở cả hai. |
| LLM fail thì user thấy gì | Retry 2 tầng (vòng `for` 3 lần × Resilience4j 3 lần = tối đa 9 request/lượt gọi). Hết retry: `AiServiceException` → 503, `InvalidAiResponseException` → 502, circuit breaker mở → 503. `@Transactional` rollback nên **không lưu submission rác**. FE: editor đơn hiện lỗi và bài còn trong textarea *(nhưng F5 là mất vì không autosave)*; full-exam còn draft localStorage; mock test đặt status FAILED và có màn hình riêng, essay vẫn nằm trong `progressJson` trên server. |

---

## 4. Chung cho cả 3 kỹ năng — bảo mật chấm điểm

| Câu hỏi bắt buộc | Kết luận | Bằng chứng |
|---|---|---|
| **Chấm điểm ở FE hay BE?** | ✅ **100% ở backend** | Grep `correctAnswer` toàn `frontend/src` chỉ ra kết quả ở các trang **result / review / admin** (`QuestionPanel.jsx:133`, `ReadingResultPage.jsx:318`, `ListeningResultPage.jsx:203`, `HistoryReviewPage.jsx:304`, `ReadingFullResultPage.jsx:317`) — **không có so sánh đáp án nào trong luồng đang làm bài**. |
| **Endpoint lấy đề có chứa đáp án không?** | ✅ **Không** | `ReadingQueryService.mapToQuizResponse:107` và `MockTestService.mapToReadingQuizResponse:542` không set `correctAnswer`; `mapOptions(options, false)` set `isCorrect = null`. `ListeningQueryService.toPartResponse:111` cũng vậy — không kèm `correctAnswer` lẫn `transcript`. |
| **Client có gửi điểm lên không?** | ✅ **Không** | `ReadingSubmitRequest`, `ListeningSubmitRequest`, `WritingGradeRequest`, `MockTestSubmitRequest` chỉ chứa đáp án/bài viết, không có field score/band/correctCount. |
| **Có endpoint nào trả đáp án TRƯỚC khi nộp không?** | ❌ **CÓ — 4 chỗ** | `ai-analyze` (P0 #6), IDOR `{testId}/result` (P0 #4), oracle nộp lại (P0 #7), `vocabulary/{partId}` (P1). |

**P0 #6 — `POST /listening/ai-analyze/{questionId}` trả thẳng vị trí đáp án cho thí sinh đang thi** · LỖI · [ListeningController.java:98](../backend/src/main/java/com/smartprep/controller/ListeningController.java)

```java
@PostMapping("/ai-analyze/{questionId}")
public ResponseEntity<ApiResponse<Map<String, Object>>> analyzeQuestion(@PathVariable Long questionId) {
    return ResponseEntity.ok(ApiResponse.ok(listeningGenerationService.analyzeQuestion(questionId)));
}
```

Không có `@AuthenticationPrincipal`, không kiểm tra chủ sở hữu, **không kiểm tra đã nộp bài hay chưa**. Service ([ListeningGenerationService.java:363](../backend/src/main/java/com/smartprep/service/ai/ListeningGenerationService.java)) nhét thẳng đáp án và transcript vào prompt:

```java
"Analyze this IELTS Listening question. The correct answer is '%s'.\n\nTranscript:\n%s\n\n..."
+ "{\n  \"correctAnswerLocation\": \"<exact quote from transcript containing the answer>\", ..."
, question.getCorrectAnswer(), part.getTranscriptText(), question.getQuestionText());
```

Thí sinh đang thi chỉ cần mở DevTools và gọi endpoint này cho từng `questionId` là **biết hết đáp án**. Đây là lỗ hổng nặng nhất trong tài liệu này. *(Tôi đã tự mở lại cả 2 file để xác minh, không dựa vào báo cáo của agent.)*

Cùng nhóm, nhẹ hơn nhưng cùng bản chất:
- `POST /listening/vocabulary/{partId}` ([ListeningController.java:108](../backend/src/main/java/com/smartprep/controller/ListeningController.java) → service dòng 382) trả `contextExample` = **câu nguyên văn trích từ transcript** của bất kỳ `partId` nào, không cần đã nộp. Prompt yêu cầu *"Extract at least 8 words"* và gọi lặp được → user đang thi có thể **tái dựng phần lớn transcript** rồi suy ra đáp án các câu `FILL_BLANK`. (P1, nhưng sát P0.)
- `POST /vocab/ai-suggest` — 3 resolver dùng `findById` thuần, không scope theo user: `ReadingSourceResolver.java:23` (passage), `ListeningSourceResolver.java:25` (transcript), `VocabularyService.java:120` (`MockTestSubmission`) → IDOR đọc chéo nội dung bài của user khác.

**P0 #7 — Nộp lại vô hạn + response nộp bài chứa đáp án = oracle** · LỖI · [ReadingGradingService.java:122](../backend/src/main/java/com/smartprep/service/ReadingGradingService.java)

Kịch bản khai thác, hai request:

1. `POST /api/v1/reading/submit-full` với `answers` = một câu bất kỳ (chỉ cần thoả `@NotEmpty`) → response `mapToResultResponse` trả về `correctAnswer`, `explanation` **và** `evidenceText` của cả 3 passage ([ReadingQueryService.java:144-153](../backend/src/main/java/com/smartprep/service/ReadingQueryService.java)).
2. `POST` lại chính request đó với đáp án đúng → `quiz.setSubmittedAt` bị ghi đè, một `ScoreHistory` mới band 9.0 được lưu.

`ListeningGradingService.submitTest` còn dễ hơn: mỗi lần gọi tạo hẳn một `ListeningTest` mới, không dedup gì cả, và response kèm cả `transcriptText`.

*Thẩm định (CONFIRMED):* đây là **bỏ sót, không phải chủ ý** — `POST /reading/{quizId}/submit` có guard (`ReadingGradingService.java:57-59`) và mock test có guard `SessionStatus` (`MockTestService.java:207-209`); đúng hai đường này thì không. Ownership vẫn được enforce nên nạn nhân là chính điểm số của user, nhưng hệ quả là **mọi band Reading full-test và Listening trong hệ thống đều không đáng tin**.

**P1 — BE không bao giờ từ chối bài nộp quá deadline** (cả 3 kỹ năng) · [ExamAttemptService.java:174](../backend/src/main/java/com/smartprep/service/ExamAttemptService.java)
`completeAttemptInternal` chỉ *cap* `spentSeconds` về `durationSeconds + buffer`, **không có** `if (now.isAfter(deadline)) throw`. `deadline` chỉ được so sánh đúng một lần, lúc `startAttempt` (dòng 57). Tệ hơn: `attemptId` là **optional** ở cả 3 đường submit, nên user thậm chí không cần gửi attempt vẫn được chấm — làm comment *"anti-cheat timer enforcement"* trong `ExamAttempt.java:12` thành vô nghĩa. Tắt JS auto-submit, làm bài 3 tiếng rồi nộp, bài vẫn được chấm và `timeSpentSeconds` còn trông "sạch" vì đã bị cap.

**P2 — Không có test nào bảo vệ hành vi ẩn đáp án**
Việc ẩn đáp án hiện phụ thuộc vào **một tham số boolean `showCorrect` truyền tay ở 9 vị trí** (`ReadingQueryService:113/142`, `ListeningQueryService:118/177`, `MockTestService:535/556/366`, `AdminService:334`, `AdminListeningService:295`). Grep toàn bộ `backend/src/test`: không có test nào assert `isCorrect == null`, không test ownership của `getTestResult`, không test chặn re-submit. **Sửa nhầm một chữ `true`/`false` là lộ đáp án mà CI không phát hiện.**

---

## 5. Trả lời trực tiếp các điểm bạn yêu cầu soi kỹ

| Câu hỏi | Trả lời |
|---|---|
| **Reading** — highlight text trong passage? | ❌ **Không có.** [PassageViewer.jsx](../frontend/src/components/reading/PassageViewer.jsx) chỉ dài 30 dòng: split theo `'\n'`, detect nhãn đoạn, render `<p>` tĩnh. Không có `onMouseUp`, không selection handler, không nút highlighter. Highlight **chỉ tồn tại sau khi nộp** (evidence ở trang kết quả). |
| **Reading** — scroll đồng bộ passage ↔ câu hỏi? | ❌ **Không có.** Không có `ref`/`scrollIntoView` liên kết 2 panel; `exam-left` và `exam-right` cuộn hoàn toàn độc lập. |
| **Reading** — lưu đáp án tạm khi chưa nộp? | ✅ **Có** (`localStorage` `reading_quiz_draft_{quizId}`, ghi mỗi lần chọn đáp án, restore khi load). |
| **Reading** — timer persist khi F5? | ✅ **Có**, và bền hơn cả sessionStorage: kể cả mất `attemptId`, `startAttempt` trả lại attempt IN_PROGRESS cũ. ⚠️ Nhưng attempt tra theo `(userId, skillType)` nên **có thể trả nhầm attempt của đề khác** (P1 #5). |
| **Reading** — chấm TFNG có phân biệt True/False/Not Given? | ✅ **Có, phân biệt đúng 3 giá trị.** Là `equalsIgnoreCase` thô nên về lý thuyết `"T"` hay `"NG"` sẽ bị chấm sai — nhưng trên UI điều đó không xảy ra vì TFNG/YNNG là **3 nút bấm** phát ra đúng chuỗi `TRUE`/`FALSE`/`NOT GIVEN` (`QuestionPanel.jsx:264-307`), và AI generator chuẩn hoá đáp án về canonical (`ReadingGenerationService.normalizeCorrectAnswer:754`). ⚠️ Rủi ro nằm ở **import đề tay**: nếu admin nhập `'NG'` thì user bấm nút sẽ bị chấm sai. |
| **Listening** — edge-tts sinh lúc nào? | **Request time, async.** Không phải build time. Chi tiết 3 nguồn ở mục 2. |
| **Listening** — có seek/replay không? | Practice: **có, không giới hạn.** Mock test: chặn replay + khoá seek nhưng **chỉ ở FE** → bypass hoàn toàn (P1 #13). |
| **Listening** — audio và transcript có khớp? | Part AI: **khớp**. Part V6: transcript có, audio câm → **không khớp**. Cam19: transcript NULL → **không có gì để đối chiếu**. |
| **Listening** — chống nghe lại quá số lần? | ❌ **Không có enforcement backend.** Server không đếm số lần phát; URL audio là `permitAll`, không cần cả JWT. |
| **Listening** — TTS fail thì sao? | Part chuyển `FAILED`, backend trả **MP3 câm 1 giây với HTTP 200**, FE coi như đã sẵn sàng → user thi bình thường mà **không biết audio hỏng**, không có nút retry (P1 #10). |
| **Writing** — đếm từ có đúng không? | ✅ **Đúng, và FE/BE khớp nhau.** Xem bảng mục 3. |
| **Writing** — autosave? | ⚠️ **Có ở 2/3 flow.** Full-exam và mock test có; **`WritingEditorPage` (flow chính) không có** (P1 #17). |
| **Writing** — chấm bằng gì, prompt ở đâu, output có schema? | Gemini 2.5 Flash; prompt hardcode ở `WritingGradingService.java:236-442`; output **có schema JSON cố định** + validate bắt buộc. Xem bảng mục 3. |
| **Writing** — LLM fail thì user thấy gì? | 503/502 kèm message, transaction rollback nên không lưu bài rác. Xem bảng mục 3. |
| **Chung** — chấm ở FE hay BE? | ✅ **BE, 100%.** Xem mục 4. |
| **Chung** — endpoint nào trả đáp án trước khi nộp? | ❌ **Có 4:** `ai-analyze` (P0 #6), IDOR `listening/{testId}/result` (P0 #4), oracle nộp lại `submit-full`/`listening/submit` (P0 #7), `vocabulary/{partId}` (P1). |

### Luồng liên quan nhưng chưa ai dùng / hiển thị số giả

- `AdaptiveController` + `AdaptiveService` (186 dòng logic ước lượng band, chọn độ khó, `focusQuestionType`) — grep `adaptive` trong `frontend/src` = **0 kết quả** → tính năng chết.
- [HistoryPage.jsx](../frontend/src/pages/HistoryPage.jsx) — cột "Time Spent" **hardcode**: `'58 mins'` (dòng 70), `'30 mins'` (80), `'40 mins'` (92), `'2h 45m'` (103) cho mọi bản ghi, dù `ScoreHistory.timeSpentSeconds` đã có dữ liệu thật. → **GIẢ LẬP**, hiển thị số bịa cho người dùng.
- Hai stack analytics song song (`StatsController` và `AnalyticsController`) cùng đọc `ScoreHistory`/`UserAnswer`, FE dùng cả hai → nguy cơ số liệu lệch giữa Dashboard và Profile.

### Test coverage của logic chấm điểm

| Service | Test | Ghi chú |
|---|---|---|
| `IeltsScoringUtils` | ✅ 24-26 test | Phủ matcher + band map Academic/GT. Đây là phần chắc nhất. |
| `ReadingGradingService` | ❌ **0 test** | `ReadingServiceTest` là của `ReadingGenerationService` (chỉ test `generateQuiz`). `submitQuiz`/`submitFullQuiz`/`calculateBandScore` **trắng test** — đúng chỗ có bug band map (P1 #6). |
| `ListeningGradingService` | ❌ **0 test** | Đúng chỗ đang lệch band map với `IeltsScoringUtils` (P0 #5). |
| `WritingGradingService` | ✅ có test | Kỹ năng được phủ tốt nhất. |
| Frontend | ⚠️ chỉ timer | `__tests__` chỉ có timer/auth/axios. Không test nào cho `QuestionPanel`, draft, submit flow. |

---

## 6. SCHEMA GAPS

> Đối chiếu toàn bộ 40 migration + entity với cấu trúc đề **Cambridge IELTS thật** (Academic + General Training).
> ⚠️ *Lưu ý: bạn nhắc "xem chuẩn ở Prompt B" nhưng tôi chưa nhận được Prompt B. Phần này đối chiếu theo chuẩn Cambridge IELTS chính thức. Nếu Prompt B có yêu cầu khác, gửi tôi để rà lại.*

**Kết luận: schema hiện tại CHƯA lưu được một đề IELTS hoàn chỉnh.** 9 lỗ hổng:

| # | Bảng | Thiếu gì | Không lưu được tính năng IELTS nào | Mức |
|---|---|---|---|---|
| 1 | `reading_questions`, `listening_questions` | **Đáp án thay thế.** `correct_answer` là 1 `VARCHAR(500)` duy nhất, chấm bằng so chuỗi tuyệt đối | Key Cambridge thường chấp nhận nhiều biến thể: `walls // stone walls`, `(the) manager`, `4.95 / £4.95`. **Học viên trả lời đúng theo key vẫn bị chấm SAI** | **P0** |
| 2 | `reading_questions`, `listening_questions` | **Multi-select MCQ.** Không có `select_count`; `correct_answer` 1 chuỗi + `equalsIgnoreCase` chỉ so 1 chữ cái | "Choose TWO letters, A-E" (mỗi chữ cái đúng = 1 điểm, không phụ thuộc thứ tự). Bằng chứng: V32:40-43 phải **bóp méo** 2 cụm "Choose TWO letters" thành 4 MCQ đơn | P1 |
| 3 | `listening_questions` | **Toàn bộ cấu trúc nhóm câu hỏi**: `group_id`, `group_label`, `group_context`, `options_json`, `word_limit`, `explanation` — những cột `reading_questions` đã có từ V8 | Matching chọn từ hộp A-H dùng chung; form/note/table completion (khung note là ngữ cảnh chung cả nhóm). Đây **chính là lý do** V30 phải giả câu matching thành `FILL_BLANK` với đề bài vô nghĩa | P1 |
| 4 | `reading_questions`, `listening_questions` | **Cột ảnh.** Không có `image_url` nào cho câu hỏi | Diagram Label Completion (Reading) và **Map/Plan/Diagram Labelling** (Listening — có trong hầu hết đề Cambridge Part 2). Đây là lý do các seed Cam19 không có câu map labelling nào | P1 |
| 5 | `reading_quizzes` | **`title` của passage** và **đánh dấu đoạn A/B/C có cấu trúc**. `passage_text` là 1 blob; quy ước `"A. "` inline chỉ tồn tại trong prompt AI, không phải schema | Matching Headings / Matching Information cần passage chia đoạn có nhãn để hiển thị và đối chiếu. Tiêu đề Cambridge đang bị nhét vào `passage_text` (V32:13) | P1 |
| 6 | `mock_tests`, `writing_prompts` | **`module_type`.** V25 mới chỉ thêm cho `reading_quizzes` và `score_history` | Không lắp được đề GT trọn vẹn: mock test không biết dùng bảng band nào (bảng GT **đã có sẵn** ở `IeltsScoringUtils:104-145`), Writing Task 1 GT (letter) không lọc được. Thêm nữa 1 quiz = 1 passage, trong khi **GT Reading Section 1-2 gồm 2-3 văn bản ngắn** | P1 |
| 7 | `listening_parts`, `listening_questions` | `section_instructions` (câu "You will hear…" đầu part) và `cue_start_seconds` | Không xây được tính năng "nghe lại từ vị trí câu này" và transcript-sync khi review | P2 |
| 8 | `writing_prompts` | `sample_answer`, `sample_answer_band`, `examiner_comment`, `min_words` | Mỗi đề Cambridge kèm bài mẫu + nhận xét giám khảo — **phần giá trị học tập cốt lõi** của bộ sách, hiện không có chỗ lưu. `min_words` đang hardcode ở FE thay vì theo đề | P2 |
| 9 | `reading_questions`, `listening_questions` | `word_limit` chỉ lưu số, không biểu diễn được **"AND/OR A NUMBER"**; `listening_questions` không có `word_limit` gì cả | Instruction word-limit là **một phần của luật chấm** Cambridge (trả lời quá số từ = sai) | P2 |

### Migration đề xuất

```sql
-- V41__complete_cambridge_exam_schema.sql
-- Bổ sung schema để lưu được một đề IELTS Cambridge hoàn chỉnh
-- (Academic + General Training, đủ 14 dạng câu hỏi, ảnh, alternatives, multi-select)

-- 1. READING: tiêu đề passage + đánh dấu đoạn A/B/C (Matching Headings/Information)
ALTER TABLE reading_quizzes
    ADD COLUMN title VARCHAR(255) NULL AFTER difficulty,
    ADD COLUMN paragraph_labels_json JSON NULL AFTER passage_text;
    -- paragraph_labels_json: [{"label":"A","offset":0,"length":812}, {"label":"B","offset":814,...}]

-- GT Reading: 1 section có thể gồm nhiều văn bản ngắn (Section 1: 2-3 texts)
CREATE TABLE reading_passage_texts (
    text_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    quiz_id     BIGINT NOT NULL,
    text_order  INT NOT NULL,
    title       VARCHAR(255) NULL,
    body        MEDIUMTEXT NOT NULL,
    FOREIGN KEY (quiz_id) REFERENCES reading_quizzes(quiz_id) ON DELETE CASCADE,
    UNIQUE KEY uk_rpt_quiz_order (quiz_id, text_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. ĐÁP ÁN THAY THẾ — chuẩn key Cambridge "walls // stone walls"
ALTER TABLE reading_questions
    ADD COLUMN answer_alternatives_json JSON NULL AFTER correct_answer;
ALTER TABLE listening_questions
    ADD COLUMN answer_alternatives_json JSON NULL AFTER correct_answer;
    -- ví dụ: ["stone walls", "the walls"]; correct_answer vẫn là đáp án chính để hiển thị

-- 3. MCQ MULTI-SELECT ("Choose TWO letters, A-E") — chấm từng chữ cái
ALTER TABLE reading_questions
    ADD COLUMN select_count INT NOT NULL DEFAULT 1 AFTER question_type;
ALTER TABLE listening_questions
    ADD COLUMN select_count INT NOT NULL DEFAULT 1 AFTER question_type;
    -- select_count > 1: correct_answer lưu CSV đã sort ("B,D"); user_answer lưu CSV;
    -- mỗi chữ cái đúng = 1 điểm (map sang số câu trong nhóm khi chấm)

-- 4. LISTENING: cấu trúc nhóm câu hỏi + hộp phương án + word limit
--    (đồng bộ với các cột reading_questions đã có từ V8)
ALTER TABLE listening_questions
    ADD COLUMN group_id      INT NULL,
    ADD COLUMN group_label   VARCHAR(255) NULL,   -- "Questions 16-20: Choose from the box A-H"
    ADD COLUMN group_context TEXT NULL,           -- khung note/form/table hoặc nội dung hộp A-H
    ADD COLUMN options_json  JSON NULL,           -- phương án dùng chung cho nhóm matching
    ADD COLUMN word_limit    INT NULL,            -- "NO MORE THAN TWO WORDS"
    ADD COLUMN explanation   TEXT NULL;           -- giải thích đáp án khi review

-- Word-limit instruction đầy đủ: có cho phép số hay không ("AND/OR A NUMBER")
ALTER TABLE reading_questions   ADD COLUMN allow_number BOOLEAN NULL;
ALTER TABLE listening_questions ADD COLUMN allow_number BOOLEAN NULL;

-- 5. ẢNH cho câu hỏi: Diagram Label (Reading) + Map/Plan/Diagram Labelling (Listening)
ALTER TABLE reading_questions   ADD COLUMN image_url VARCHAR(512) NULL;
ALTER TABLE listening_questions ADD COLUMN image_url VARCHAR(512) NULL;
    -- ảnh gắn theo nhóm: các câu cùng group_id dùng image_url của câu đầu nhóm

-- 6. LISTENING: instruction đầu part + cue timing để review "nghe lại từ đây"
ALTER TABLE listening_parts
    ADD COLUMN section_instructions TEXT NULL;    -- "You will hear a conversation about..."
ALTER TABLE listening_questions
    ADD COLUMN cue_start_seconds INT NULL;        -- giây bắt đầu đoạn audio chứa đáp án

-- 7. MODULE TYPE đầy đủ: Academic vs General Training (V25 mới chỉ có reading)
ALTER TABLE mock_tests
    ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC';
ALTER TABLE writing_prompts
    ADD COLUMN module_type VARCHAR(30) NOT NULL DEFAULT 'ACADEMIC';
UPDATE writing_prompts SET module_type = 'GENERAL_TRAINING' WHERE essay_type = 'LETTER';

-- 8. WRITING: bài mẫu + nhận xét giám khảo + số từ tối thiểu theo đề
ALTER TABLE writing_prompts
    ADD COLUMN sample_answer      TEXT NULL,
    ADD COLUMN sample_answer_band DECIMAL(2,1) NULL,
    ADD COLUMN examiner_comment   TEXT NULL,
    ADD COLUMN min_words          INT NULL;       -- 150 (Task 1) / 250 (Task 2)
```

**Thay đổi code bắt buộc đi kèm** (ngoài phạm vi SQL):
- `IeltsScoringUtils.isReadingCorrect` / `isListeningCorrect` phải duyệt `answer_alternatives_json`, chấm CSV theo `select_count`, và enforce `word_limit`/`allow_number`.
- FE cần render `group_context`/`options_json` của Listening và `image_url` của câu hỏi.
- `ListeningQuestion` entity + DTO cần map các cột mới.

---

## 7. Thứ tự sửa đề xuất cho Giai đoạn 2

**P0 — làm sai kết quả hoặc lộ đáp án**

1. Chặn `ai-analyze` và `vocabulary`: thêm `@AuthenticationPrincipal`, kiểm tra người dùng đã nộp bài chứa câu hỏi đó *(P0 #6)*.
2. Thêm ownership check cho `GET /listening/{testId}/result` *(P0 #4)*.
2b. Chặn nộp lại ở `submit-full` và `listening/submit` *(P0 #7)* — copy đúng guard đã có sẵn ở `ReadingGradingService.java:57-59`.
3. Gộp 2 bảng band Listening về một nguồn duy nhất là `IeltsScoringUtils`, xoá 3 bản copy của `checkAnswer` *(P0 #5)*.
4. Chạy migration V41 **trước khi sinh bất kỳ nội dung nào** *(P0 SCHEMA)*.
5. Xoá hoặc thay toàn bộ seed giả V30/V32 *(P0 #1, #3)* — việc này nên gộp vào bước sinh nội dung.
6. Chuẩn hoá đáp án khi chấm: alternatives, bỏ dấu câu, số↔chữ, enforce word limit *(P1 #2, #11 — nâng lên làm cùng P0 vì đây là "sai kết quả")*.
7. Sửa `BAND_SCORES_13` để phụ thuộc tổng số câu *(P1 #6)*.

**P1 — thiếu chức năng lõi**

8. FE render `FILL_BLANK` / `SHORT_ANSWER` / `DIAGRAM_LABEL_COMPLETION` (cả 2 bản `QuestionPanel`) *(P1 #3)*.
9. Mock test `McqQuestion` đọc `question.options` thay vì parse `questionText` *(P1 #14)*.
10. Sửa query param `task1Id`/`task2Id` — mở lại flow Writing full *(P1 #15)*.
11. Autosave cho `WritingEditorPage` và `ListeningExamPage` *(P1 #17, Listening bước 7)*.
12. Không huỷ bài khi hết giờ mà thiếu từ — cho phép nộp và chấm phần đã viết *(P1 #18, #19)*.
13. Gửi `visualData`/ảnh cho Gemini khi chấm Task 1 *(P1 #16)*.
14. `startAttempt` phải so `examReferenceIds` *(P1 #5)*.
15. Timer mock test chuyển sang deadline server *(P1 #4)*; server từ chối bài nộp quá deadline *(P1 #12)*.
16. TTS fail: hiện lỗi + nút retry, poll có timeout *(P1 #10)*.

**P2 — UX và nhất quán**

17. `ListeningResultPage` / `ReadingFullResultPage` fetch từ API thay vì chỉ đọc `location.state`.
18. Lưu `history_id` trực tiếp thay cho heuristic ±5 giây.
19. Mock test ghi `score_history` cho cả 3 kỹ năng.
20. Lọc `content_status` ở Reading templates, Listening parts, Writing prompts.
21. Bỏ số hardcode trong `HistoryPage.jsx`.
22. Highlight + scroll sync cho passage Reading.

**Trước khi sửa bất kỳ logic chấm điểm nào:** viết test cho `ReadingGradingService`, `ListeningGradingService` và bảng band của cả hai. Đây là 2 service đang **trắng test** và đúng là nơi 3 bug chấm sai đang nằm.

Bốn test tối thiểu cần có để các lỗ hổng trên không quay lại:
1. `mapToQuizResponse` / `toPartResponse` trả `isCorrect == null` và không có `correctAnswer`.
2. `getResult` ném lỗi khi quiz chưa nộp.
3. `getTestResult` từ chối user không phải chủ sở hữu.
4. Nộp lần thứ hai bị từ chối ở cả 3 đường submit.
