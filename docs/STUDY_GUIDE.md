# STUDY GUIDE — Kiến thức nền cần học trước khi vấn đáp

> Tài liệu phục vụ 6 buổi vấn đáp về repo IELTS SmartPrep. Học theo đúng thứ tự — buổi sau dựa trên buổi trước.
>
> **Cách dùng:** với MỖI khái niệm, làm đủ 3 bước:
> 1. Học lý thuyết chung (sách/docs/Google — không phụ thuộc repo).
> 2. Mở file repo được dẫn kèm, tự chỉ ra khái niệm đó "hiện thân ở dòng nào".
> 3. Tự trả lời các câu hỏi tự kiểm tra **thành tiếng hoặc viết ra** — trả lời trong đầu là tự lừa mình.
>
> Câu tự kiểm tra ở đây mô phỏng đúng kiểu phỏng vấn: không hỏi định nghĩa, mà hỏi *cơ chế* và *"nếu bỏ đi thì hỏng gì"*.

---

## Phần 0 — Nền chung (bắt buộc trước tất cả các buổi)

### 0.1. Java cốt lõi (học TRƯỚC TIÊN nếu nền Java còn yếu)

> Phạm vi: **Java đủ để đọc và bảo vệ repo này**, không phải Java toàn tập.
> KHÔNG cần học (ở giai đoạn này): JVM tuning, classloader, GC chi tiết, Java Memory Model, module system. Học mấy cái đó bây giờ là lạc đường.

**Ngôn ngữ:**

- [ ] Class / interface / enum / abstract, access modifier (`public`/`private`/`protected`), `static`, `final`.
- [ ] Interface vs implementation — repo dùng pattern này thật: [VocabSourceResolver.java](../backend/src/main/java/com/smartprep/service/vocab/VocabSourceResolver.java) có 4 class implement ([ReadingSourceResolver](../backend/src/main/java/com/smartprep/service/vocab/ReadingSourceResolver.java), Listening/Writing/Speaking tương tự).
- [ ] Exception: `try/catch/finally`, **checked vs unchecked** (`Exception` vs `RuntimeException`) — mở [../backend/src/main/java/com/smartprep/exception/](../backend/src/main/java/com/smartprep/exception/) xem các exception tự định nghĩa extends gì, tự hỏi vì sao chọn loại đó (câu này nối thẳng sang rollback rule ở buổi 3).
- [ ] Generics: đọc trôi chảy kiểu lồng nhau — ví dụ thật trong [WritingController.java](../backend/src/main/java/com/smartprep/controller/WritingController.java): `ResponseEntity<ApiResponse<List<WritingPromptResponse>>>` — nói được ai bọc ai, mỗi lớp vỏ để làm gì.
- [ ] Collections: `List`/`Map`/`Set`, `ArrayList`/`HashMap`, vòng lặp, `Map.Entry`.
- [ ] `Optional<T>`: `orElseThrow`, `ifPresent`, `map` — vì sao repository trả `Optional` thay vì null. Ví dụ thật: `userRepository.findById(userId).ifPresent(...)` trong [JwtAuthenticationFilter.java](../backend/src/main/java/com/smartprep/security/JwtAuthenticationFilter.java).
- [ ] Lambda, method reference (`User::getUserId`), functional interface.
- [ ] **Stream API**: `stream().map().filter().collect()` — dùng dày đặc trong [MockTestService.java](../backend/src/main/java/com/smartprep/service/MockTestService.java), [ReadingQueryService.java](../backend/src/main/java/com/smartprep/service/ReadingQueryService.java) và hầu hết service khác. Bài tập: lấy 1 đoạn stream thật trong repo, viết lại thành vòng for và ngược lại.
- [ ] Java 17 features repo đang dùng: `var` (suy kiểu cục bộ), **text block `"""`** (các prompt builder như [ReadingPromptBuilder.java](../backend/src/main/java/com/smartprep/service/ai/ReadingPromptBuilder.java) dùng để viết prompt Gemini nhiều dòng), switch expression.
- [ ] `BigDecimal` vs `double`: band score trong repo là `BigDecimal` ([User.java](../backend/src/main/java/com/smartprep/model/entity/User.java), [MockTestService.java](../backend/src/main/java/com/smartprep/service/MockTestService.java)) — phải giải thích được vì sao tiền/điểm số không dùng `double`.
- [ ] `equals`/`hashCode` contract — nền cho câu hỏi `@Data` trên entity ở buổi 3.
- [ ] Annotation là gì, framework đọc annotation bằng reflection ra sao (mức khái niệm) — nền cho Spring proxy ở mục 0.4.
- [ ] Thread ở mức Java thuần: `Runnable`, `Thread`, `ExecutorService` — nền cho buổi 5.

**Tự kiểm tra:**
- `0.1 + 0.2 == 0.3` trong Java cho kết quả gì? Từ đó suy ra vì sao band score không dùng `double`.
- `Optional.orElseThrow()` hơn gì so với trả về `null` rồi caller tự check?
- Một exception extends `RuntimeException` ném ra từ service mà không ai catch — nó đi qua những tầng nào rồi cuối cùng ai xử lý? (Nối sang mục 1.5.)
- Đọc to một chữ ký method thật: `public ResponseEntity<ApiResponse<List<WritingPromptResponse>>> getPrompts(...)` — giải thích từng lớp kiểu.

### 0.2. HTTP

- [ ] Method: GET / POST / PUT / DELETE — ngữ nghĩa, idempotency.
- [ ] Status code phải thuộc lòng: `200`, `201`, `202`, `206`, `400`, `401`, `403`, `404`, `409`, `429`, `500`, `502`, `503`.
  - Đặc biệt phân biệt **200 vs 202** (repo này dùng cả hai có chủ đích) và **401 vs 403**.
- [ ] Header quan trọng: `Authorization`, `Content-Type`, `Set-Cookie`, `X-Forwarded-For`, `Range`.
- [ ] Cookie: attribute `HttpOnly`, `Secure`, `SameSite`, `Path` — mỗi cái chống cái gì.

**Tự kiểm tra:**
- Khi nào API nên trả 202 thay vì 200? Trả 202 xong thì client làm gì tiếp?
- 401 và 403 khác nhau thế nào? Ai quyết định trả cái nào?

### 0.3. Lombok (để đọc được code repo)

- [ ] `@RequiredArgsConstructor` — sinh constructor cho field `final` → đây là cách inject dependency trong toàn bộ repo.
- [ ] `@Data` — sinh getter/setter/equals/hashCode/toString. (Buổi 3 sẽ hỏi tại sao `@Data` trên entity JPA là nguy hiểm.)
- [ ] `@Slf4j`, `@Builder`, `@Value` (của Spring, khác `@Value` của Lombok).

### 0.4. Spring core

- [ ] IoC container, bean, `@Component`/`@Service`/`@Configuration`/`@Bean`.
- [ ] Constructor injection (soi bất kỳ service nào trong repo: field `final` + `@RequiredArgsConstructor`).
- [ ] **Spring proxy** — QUAN TRỌNG NHẤT phần này: Spring bọc bean bằng proxy để chèn hành vi (`@Transactional`, `@Async`, `@PreAuthorize` đều hoạt động nhờ proxy). Không hiểu proxy thì buổi 3 và buổi 5 sẽ trả lời sai.

**Tự kiểm tra:**
- Gọi method `@Transactional` từ method khác *trong cùng class* thì transaction có mở không? Vì sao?

### 0.5. Maven

- [ ] `pom.xml`: parent, dependency, BOM, version management. Soi: [../backend/pom.xml](../backend/pom.xml).

---

## Phần 1 — Vòng đời request (Buổi 1)

Mục tiêu: kể được từng trạm mà `POST /api/v1/writing/grade` đi qua, từ browser đến response, và nói được **nếu bỏ trạm X thì hỏng gì**.

### 1.1. Nginx reverse proxy

- [ ] Reverse proxy là gì, khác forward proxy.
- [ ] Nginx vừa serve static (SPA) vừa proxy `/api/` sang backend.
- [ ] Header `X-Forwarded-For` — ai set, để làm gì, vì sao tin nó là rủi ro.

**Soi repo:** [../frontend/nginx.conf](../frontend/nginx.conf) — 3 block `location`, thứ tự ưu tiên giữa chúng.

**Tự kiểm tra:**
- Request `POST /api/v1/writing/grade` khớp block `location` nào? Vì sao không khớp block SPA?
- Backend nhìn thấy IP của ai nếu không có `X-Forwarded-For`?

### 1.2. Tomcat và thread-per-request

- [ ] Embedded Tomcat trong Spring Boot, worker thread pool (mặc định max 200).
- [ ] **Mỗi request chiếm trọn 1 thread cho đến khi response trả xong** — nền tảng để hiểu vì sao endpoint gọi AI đồng bộ là vấn đề.

**Tự kiểm tra:**
- Nếu 1 request mất 60 giây mới xong, thread đó làm gì trong 60 giây đó? Có phục vụ request khác được không?

### 1.3. Chuỗi Filter → Security → Interceptor → Controller

- [ ] Servlet Filter: thuộc servlet spec, chạy **trước** `DispatcherServlet`, không biết gì về controller.
- [ ] Spring Security filter chain là một dãy filter đặc biệt được nhét vào chuỗi servlet filter.
- [ ] `HandlerInterceptor`: thuộc Spring MVC, chạy **sau** khi đã chọn được handler method, có `preHandle`/`postHandle`/`afterCompletion`.
- [ ] Thứ tự tổng: Filter → Security rules → DispatcherServlet → Interceptor → Controller.

**Soi repo:**
- Filter: [../backend/src/main/java/com/smartprep/security/TraceIdFilter.java](../backend/src/main/java/com/smartprep/security/TraceIdFilter.java), [JwtAuthenticationFilter.java](../backend/src/main/java/com/smartprep/security/JwtAuthenticationFilter.java)
- Nơi khai báo rule + gắn filter: [SecurityConfig.java](../backend/src/main/java/com/smartprep/config/SecurityConfig.java)
- Interceptor + thứ tự: [WebMvcConfig.java](../backend/src/main/java/com/smartprep/config/WebMvcConfig.java) — chú ý `.order(...)` và path patterns.

**Tự kiểm tra:**
- Vì sao rate-limit theo *userId* phải làm bằng Interceptor (hoặc filter đặt sau security) chứ không đặt trước JwtAuthenticationFilter?
- Request bị chặn ở Interceptor thì có vào controller không? Có đi qua filter không?

### 1.4. Data binding và validation

- [ ] `@RequestBody` + Jackson: JSON → object lúc nào, lỗi parse thì ném gì.
- [ ] Bean Validation: `@Valid`, `@NotBlank`, `@NotNull`, `@Size`, `@Pattern` — chạy ở bước binding argument, **trước khi** method controller chạy.
- [ ] Lỗi validation ném `MethodArgumentNotValidException` → ai bắt?

**Soi repo:** [WritingGradeRequest.java](../backend/src/main/java/com/smartprep/dto/request/WritingGradeRequest.java), [WritingController.java](../backend/src/main/java/com/smartprep/controller/WritingController.java) (chú ý `@Valid`, `@AuthenticationPrincipal`).

**Tự kiểm tra:**
- Bỏ `@Valid` khỏi tham số controller thì các annotation trong DTO còn tác dụng không?
- `essayText` 20.000 ký tự thì bị chặn ở đâu, status code nào?

### 1.5. Xử lý lỗi tập trung

- [ ] `@RestControllerAdvice` + `@ExceptionHandler`: exception từ controller/service propagate lên được map thành response thế nào.

**Soi repo:** [GlobalExceptionHandler.java](../backend/src/main/java/com/smartprep/exception/GlobalExceptionHandler.java) — đếm xem có bao nhiêu handler, exception tự định nghĩa nằm ở [../backend/src/main/java/com/smartprep/exception/](../backend/src/main/java/com/smartprep/exception/).

**Tự kiểm tra:**
- Service ném `ResourceNotFoundException` thì user nhận status code gì? Dòng nào quyết định?
- Nếu không có `@RestControllerAdvice` thì user thấy gì khi service ném exception?

---

## Phần 2 — Auth (Buổi 2)

### 2.1. JWT

- [ ] Cấu trúc 3 phần: header.payload.signature — phần nào mã hoá, phần nào chỉ encode base64.
- [ ] Claim chuẩn: `sub`, `iat`, `exp`, `jti` — và claim tự thêm (`role`, `type`).
- [ ] Ký HMAC đối xứng (1 secret) vs RSA bất đối xứng (private/public) — trade-off.
- [ ] **JWT là stateless: server không "lưu" token** → hệ quả: không tự thu hồi được nếu không có cơ chế phụ (blacklist).

**Soi repo:** [JwtTokenProvider.java](../backend/src/main/java/com/smartprep/security/JwtTokenProvider.java) — tìm: secret lấy từ đâu, TTL bao nhiêu, claim nào được nhét vào access vs refresh.

**Tự kiểm tra:**
- User tự sửa payload đổi `role` thành ADMIN rồi gửi lên — chuyện gì xảy ra, chặn ở dòng nào?
- Vì sao secret bắt buộc ≥32 bytes?
- Access token TTL 15 phút — ngắn thế để làm gì? Dài 7 ngày thì sao?

### 2.2. Access / Refresh / Rotation

- [ ] Vì sao cần 2 loại token, TTL khác nhau.
- [ ] Refresh **rotation**: mỗi lần refresh phát cặp mới + thu hồi cái cũ — chống replay.
- [ ] JTI: định danh token, server chỉ cần lưu JTI (không lưu cả token) để quản lý vòng đời.
- [ ] Blacklist access token khi logout.

**Soi repo:** [TokenService.java](../backend/src/main/java/com/smartprep/service/TokenService.java) (key Redis nào, TTL nào), [UserService.java](../backend/src/main/java/com/smartprep/service/UserService.java) (flow refresh + logout), [AuthController.java](../backend/src/main/java/com/smartprep/controller/AuthController.java) (cookie đọc/ghi thế nào).

**Tự kiểm tra:**
- Kẻ trộm lấy được refresh token cũ (đã rotate) đem dùng — chuyện gì xảy ra?
- Redis restart (không volume) — user đang đăng nhập bị ảnh hưởng gì? Token nào chết, token nào sống?
- Vì sao refresh token để ở HttpOnly cookie còn access token để ở localStorage? Trade-off của từng chỗ chứa?

### 2.3. Spring Security

- [ ] `SecurityFilterChain`, `permitAll` vs `authenticated` vs `hasRole`.
- [ ] `SecurityContextHolder` — ai set, ai đọc, sống bao lâu.
- [ ] Authority `ROLE_` prefix, `@PreAuthorize`, `@EnableMethodSecurity`.
- [ ] `@AuthenticationPrincipal` lấy object từ đâu ra.

**Soi repo:** [SecurityConfig.java](../backend/src/main/java/com/smartprep/config/SecurityConfig.java), [JwtAuthenticationFilter.java](../backend/src/main/java/com/smartprep/security/JwtAuthenticationFilter.java) — đọc kỹ **từng bước một** trong `doFilterInternal`, buổi 2 sẽ xoáy vào đây.

**Tự kiểm tra:**
- Filter này query DB mỗi request để làm gì? Bỏ query đó đi thì được lợi gì, mất gì? (Đây là câu chắc chắn bị hỏi.)
- Token hợp lệ nhưng JTI nằm trong blacklist — filter làm gì? Vì sao nó *không ném lỗi* mà chỉ đi tiếp?
- Phân quyền "user A không đọc được data user B" nằm ở tầng nào trong repo này?

### 2.4. Mật khẩu và các flow phụ

- [ ] BCrypt: salt, cost factor — vì sao không dùng SHA-256.
- [ ] User enumeration là gì, chống thế nào (forgot-password luôn trả 200).
- [ ] Login lockout theo username.
- [ ] CSRF: là gì, vì sao app stateless + token ở header có thể tắt; CORS: preflight, `allowCredentials`.

**Soi repo:** [LoginLockoutService.java](../backend/src/main/java/com/smartprep/service/LoginLockoutService.java), [PasswordResetService.java](../backend/src/main/java/com/smartprep/service/PasswordResetService.java), [CorsConfig.java](../backend/src/main/java/com/smartprep/config/CorsConfig.java).

**Tự kiểm tra:**
- Vì sao tắt CSRF ở đây chấp nhận được, và điều kiện nào phải giữ để nó tiếp tục chấp nhận được?
- Lockout theo username vs theo IP — mỗi cái chống kiểu tấn công nào, bị lách kiểu nào?

---

## Phần 3 — Tầng dữ liệu (Buổi 3)

### 3.1. JPA / Hibernate

- [ ] Entity, `@ManyToOne` / `@OneToMany`, owning side.
- [ ] **LAZY vs EAGER**: lazy trả về proxy, query chỉ chạy khi truy cập thật.
- [ ] `LazyInitializationException` — khi nào nổ (truy cập lazy ngoài session).
- [ ] **N+1**: 1 query lấy N row cha + N query lấy con. Cách chữa: `JOIN FETCH`, `@EntityGraph`, DTO projection.
- [ ] Persistence context, dirty checking (sửa entity trong transaction không cần gọi `save`).

**Soi repo:** bất kỳ entity nào trong [../backend/src/main/java/com/smartprep/model/entity/](../backend/src/main/java/com/smartprep/model/entity/) — xác nhận: toàn bộ `@ManyToOne` đều khai LAZY tường minh, và **cả repo không có `JOIN FETCH`/`@EntityGraph` nào** (grep để tự xác nhận). Đây là điểm sẽ bị hỏi "vậy N+1 xảy ra ở đâu".

**Tự kiểm tra:**
- Entity dùng Lombok `@Data` có quan hệ LAZY — gọi `toString()` thì chuyện gì xảy ra?
- Load 1 quiz có 40 câu hỏi, mỗi câu có options — đếm số query nếu không có fetch join.

### 3.2. Transaction

- [ ] `@Transactional`: mở lúc nào, commit/rollback lúc nào, rollback rule mặc định (RuntimeException).
- [ ] Hoạt động qua proxy → self-invocation không có transaction.
- [ ] **Transaction giữ 1 connection từ pool suốt thời gian sống** → transaction dài = chiếm connection.
- [ ] Propagation mặc định REQUIRED; biết tồn tại REQUIRES_NEW.
- [ ] HikariCP: pool size mặc định 10, hết pool thì request sau *chờ* (timeout 30s).

**Soi repo:** [WritingService.java](../backend/src/main/java/com/smartprep/service/WritingService.java) — tìm `@Transactional` bao những gì (đây là ví dụ trung tâm của cả buổi 3 lẫn buổi 5).

**Tự kiểm tra:**
- `@Transactional` bao một method có gọi HTTP ra ngoài mất 60s — hệ quả lên pool là gì? 10 user đồng thời thì user thứ 11 gặp gì?
- Exception ném ra giữa chừng — những gì đã `save` trước đó còn trong DB không?

### 3.3. Migration & Index

- [ ] Flyway: versioned migration, vì sao `ddl-auto: validate` + Flyway thay vì `ddl-auto: update`.
- [ ] Index B-tree, composite index và **leftmost prefix rule**.
- [ ] Khi nào index vô dụng (hàm bọc cột, wildcard đầu, cardinality thấp).
- [ ] FK `ON DELETE`: CASCADE / RESTRICT / SET NULL — hệ quả nghiệp vụ của từng loại.

**Soi repo:** [../backend/src/main/resources/db/migration/](../backend/src/main/resources/db/migration/) (source V1 → V41; DB dev hiện ở V40), mục D.3–D.4 của [ARCHITECTURE.md](ARCHITECTURE.md) (index trùng, cột thiếu index, và cách V41 thay CASCADE nguy hiểm ở `writing_submissions.prompt_id` bằng RESTRICT).

**Tự kiểm tra:**
- Index `(user_id, skill_type, recorded_at)` có phục vụ được query `WHERE skill_type = ?` không? Vì sao?
- Xoá 1 writing prompt thì mất những dữ liệu nào? Dòng nào trong migration quyết định điều đó?

---

## Phần 4 — Hạ tầng (Buổi 4)

### 4.1. Docker Compose

- [ ] Service, image vs build, network mặc định và **DNS theo tên service** (vì sao backend gọi được `http://edge-tts:8000`).
- [ ] `depends_on` (thứ tự start) vs `condition: service_healthy` (chờ healthcheck) — khác nhau thế nào.
- [ ] `healthcheck`: test/interval/retries.
- [ ] **Volume**: có volume thì restart giữ data, không có thì mất — soi service nào trong compose có, service nào không.
- [ ] `ports` (publish ra host) vs `expose` (chỉ nội bộ) — hệ quả bảo mật.
- [ ] Env var truyền vào container, Spring profile (`application-dev.yml` / `application-prod.yml`).

**Soi repo:** [../docker-compose.yml](../docker-compose.yml) — tự vẽ lại: 6 service là gì, ai nói chuyện với ai, qua port nào, ai có healthcheck, ai có volume.

**Tự kiểm tra:**
- Backend start trước khi MySQL sẵn sàng thì chuyện gì xảy ra? Compose chống việc này bằng gì (và cho service nào thì *không* chống)?
- Máy dev đang mở port nào ra ngoài mà production không nên mở?

### 4.2. Redis

- [ ] Key-value, TTL/EXPIRE, `INCR` (atomic counter), key naming convention.
- [ ] Persistence: RDB/AOF — mặc định của image `redis:alpine` và hệ quả khi không mount volume.
- [ ] Redis làm cache (mất được) vs Redis làm store (mất là sự cố) — cùng một instance đang gánh cả hai vai.

**Soi repo — 4 công dụng của Redis, tự tìm key pattern của từng cái:**
1. Spring Cache: [CacheConfig.java](../backend/src/main/java/com/smartprep/config/CacheConfig.java)
2. JTI refresh/blacklist: [TokenService.java](../backend/src/main/java/com/smartprep/service/TokenService.java)
3. Rate limit bucket: [RateLimitConfig.java](../backend/src/main/java/com/smartprep/config/RateLimitConfig.java)
4. Quota AI theo ngày + token counter: [RateLimitInterceptor.java](../backend/src/main/java/com/smartprep/security/RateLimitInterceptor.java), [GeminiClient.java](../backend/src/main/java/com/smartprep/service/ai/GeminiClient.java)

**Tự kiểm tra:**
- Redis chết hẳn (không restart được) — liệt kê từng chức năng của app còn sống hay chết. (Câu này gần như chắc chắn có trong buổi 4.)
- Trong 4 loại dữ liệu trên, loại nào mất đi gây hại nhất? Xếp hạng và giải thích.

### 4.3. MinIO / S3 + các dịch vụ ngoài

- [ ] S3 API: bucket, object, path-style vs virtual-host style.
- [ ] Pattern "backend làm proxy stream file" vs "presigned URL" — repo này dùng cách nào, trade-off.
- [ ] SMTP gửi mail, Sentry nhận error event — đường đi ra ngoài nào có timeout, đường nào không.

**Soi repo:** [MinioConfig.java](../backend/src/main/java/com/smartprep/config/MinioConfig.java), [StorageService.java](../backend/src/main/java/com/smartprep/service/StorageService.java), [../edge-tts/app.py](../edge-tts/app.py).

**Tự kiểm tra:**
- File audio đi từ edge-tts đến tai user qua những trạm nào? Trạm nào buffer cả file vào RAM?

---

## Phần 5 — Bất đồng bộ (Buổi 5)

### 5.1. Thread pool

- [ ] `ThreadPoolTaskExecutor`: **coreSize / maxSize / queueCapacity** — thứ tự quyết định: đủ core? → vào queue → queue đầy mới mở thêm đến max → max + queue đầy → RejectedExecutionException (policy mặc định).
- [ ] Nhiều pool tách nhau để cô lập loại việc (bulkhead).

**Soi repo:** [AsyncConfig.java](../backend/src/main/java/com/smartprep/config/AsyncConfig.java) — 2 executor, tên gì, số liệu bao nhiêu, executor nào cho việc gì.

**Tự kiểm tra:**
- Pool core 2 / max 5 / queue 25: job thứ 3 đi đâu? Job thứ 28? Job thứ 31?
- Vì sao TTS và chấm bài dùng 2 pool riêng thay vì 1 pool to?

### 5.2. @Async

- [ ] Cần `@EnableAsync`; chạy method trên executor khác, caller không chờ.
- [ ] **Hoạt động qua proxy** → gọi method `@Async` từ trong cùng class = chạy đồng bộ luôn (bug kinh điển).
- [ ] Return `void` thì exception biến mất nếu không có handler.
- [ ] **Race với transaction**: gọi `@Async` từ trong method `@Transactional` — thread async có thể chạy *trước khi* transaction caller commit → đọc không thấy dữ liệu.

**Soi repo:**
- [AudioGenerationService.java](../backend/src/main/java/com/smartprep/service/AudioGenerationService.java) — chú ý nó cố tình *không* có `@Transactional`, tự hỏi vì sao.
- [MockTestAsyncGrader.java](../backend/src/main/java/com/smartprep/service/ai/MockTestAsyncGrader.java) + chỗ gọi nó trong [MockTestService.java](../backend/src/main/java/com/smartprep/service/MockTestService.java) — đây là ca race có thật trong repo, buổi 5 sẽ mổ.

**Tự kiểm tra:**
- Restart backend lúc có 10 job đang nằm trong queue — chuyện gì xảy ra với 10 job đó? Hệ thống có tự phát hiện không?
- Vì sao mock test finish trả 202 rồi FE phải poll, còn `/writing/grade` trả 200 luôn? Hai lựa chọn thiết kế này trade-off gì?

### 5.3. Test async

- [ ] Mock + `verify` với `timeout`/`Awaitility` — vì sao test async cần chờ.
- [ ] Test đồng bộ `/writing/grade`: mock GeminiClient trả kết quả ngay, không cần chờ gì.

**Soi repo:** [../backend/src/test/java/com/smartprep/service/AudioGenerationServiceTest.java](../backend/src/test/java/com/smartprep/service/AudioGenerationServiceTest.java) và các test service khác — xem test nào phải xử lý yếu tố thời gian, test nào không.

---

## Phần 6 — Chống lỗi & chống lạm dụng (Buổi 6)

### 6.1. Timeout

- [ ] Connect timeout vs read timeout.
- [ ] **Không timeout = thread bị giam vô hạn** — nghiêm trọng hơn mọi lỗi khác trong nhóm này.

**Soi repo:** so sánh 2 RestTemplate: [GeminiConfig.java](../backend/src/main/java/com/smartprep/config/GeminiConfig.java) (có timeout) vs [TtsService.java](../backend/src/main/java/com/smartprep/service/TtsService.java) (tìm xem có không).

### 6.2. Retry

- [ ] Exponential backoff + jitter — vì sao retry dồn dập làm downstream chết hẳn (retry storm).
- [ ] Retry chỉ hợp lệ với lỗi transient + request idempotent.
- [ ] **Retry lồng nhau nhân số call**: tầng ngoài ×3, tầng trong ×3 = 9.

**Soi repo:** retry thủ công trong [WritingGradingService.java](../backend/src/main/java/com/smartprep/service/ai/WritingGradingService.java) + retry Resilience4j trong [../backend/src/main/resources/application.yml](../backend/src/main/resources/application.yml) (section `resilience4j.retry`) — hai tầng này chồng lên nhau thế nào.

**Tự kiểm tra:**
- Gemini sập hoàn toàn — 1 request `/writing/grade` tệ nhất tạo ra bao nhiêu HTTP call, user chờ tối đa bao lâu?

### 6.3. Circuit breaker

- [ ] 3 trạng thái CLOSED / OPEN / HALF_OPEN, chuyển trạng thái khi nào.
- [ ] Sliding window, failure rate threshold, wait duration.
- [ ] Circuit breaker để làm gì mà retry không làm được: **fail fast, cắt tải cho downstream đang hấp hối**.

**Soi repo:** `application.yml` section `resilience4j.circuitbreaker` + annotation trên [GeminiClient.java](../backend/src/main/java/com/smartprep/service/ai/GeminiClient.java). Chú ý luôn: config `timelimiter` tồn tại nhưng tìm xem có `@TimeLimiter` nào trong code không.

**Tự kiểm tra:**
- Breaker đang OPEN, user gọi `/writing/grade` — user nhận gì, nhanh hay chậm? Vì sao đó lại là điều *tốt*?
- Retry ×3 và breaker "đếm fail" tương tác thế nào — 1 request fail tính là 1 hay 3 vào sliding window?

### 6.4. Rate limit & quota

- [ ] Thuật toán **token bucket** (capacity, refill rate) — chạy tay được một ví dụ.
- [ ] Limit theo IP (endpoint public, chưa biết ai) vs theo userId (endpoint đã auth) — vì sao phải khác.
- [ ] Rate (req/phút) vs quota (req/ngày) — hai mục đích khác nhau.
- [ ] Bucket lưu Redis → sống sót khi scale ngang nhiều instance backend.

**Soi repo:** [RateLimitConfig.java](../backend/src/main/java/com/smartprep/config/RateLimitConfig.java), [RateLimitInterceptor.java](../backend/src/main/java/com/smartprep/security/RateLimitInterceptor.java), [AuthRateLimitInterceptor.java](../backend/src/main/java/com/smartprep/security/AuthRateLimitInterceptor.java), [LoginLockoutService.java](../backend/src/main/java/com/smartprep/service/LoginLockoutService.java) — phân biệt rõ 3 cơ chế này bảo vệ đường nào, key theo gì.

**Tự kiểm tra:**
- Nếu bucket lưu in-memory (ConcurrentHashMap) thay vì Redis thì khi nào limit bị "hở"?
- Danh sách path mà `RateLimitInterceptor` bảo vệ có phủ hết endpoint gọi AI không? (Đối chiếu bảng C của ARCHITECTURE.md — tự tìm endpoint lọt lưới.)

---

## Checklist tổng — tự chấm trước khi hẹn vấn đáp

| Buổi | Tự tin trả lời được câu này thì mới vào buổi |
|---|---|
| 1 | Kể miệng 8 trạm của một request từ browser → controller → response, không nhìn tài liệu |
| 2 | Vẽ được flow login → dùng API → refresh → logout, chỉ ra Redis được đụng ở bước nào |
| 3 | Giải thích N+1 bằng ví dụ quiz–questions–options của chính repo này |
| 4 | Trả lời "Redis chết thì chức năng nào chết theo" không cần mở code |
| 5 | Giải thích vì sao gọi `@Async` trong transaction chưa commit là race, lấy ví dụ MockTestAsyncGrader |
| 6 | Chạy tay token bucket 10 req/phút cho 12 request liên tiếp, nói đúng request nào bị 429 |

Học xong phần nào, quay lại phiên chat nói **"bắt đầu buổi N"** để vấn đáp phần đó.
