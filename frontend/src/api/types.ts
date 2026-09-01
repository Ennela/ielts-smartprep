/**
 * Wire shapes returned by the backend.
 *
 * Each interface names the Java DTO it mirrors so the two can be checked against
 * each other. The backend sets no Jackson naming strategy and uses no
 * @JsonProperty, so field names are the Lombok getter names verbatim.
 *
 * Two conversions to keep in mind when comparing with the Java source:
 *   - BigDecimal serialises as a JSON number, so it is `number` here.
 *   - LocalDateTime serialises as an ISO-8601 string, so it is `string` here.
 *
 * Optionality reflects whether the server can send null, not whether the field
 * exists. Java object types (Long, Integer, Boolean, BigDecimal) are nullable;
 * primitives (boolean, long, double) are not.
 */

// ── Envelope ────────────────────────────────────────────────────────────────

/** com.smartprep.dto.response.ApiResponse — @JsonInclude(NON_NULL) */
export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  errorCode?: string;
  timestamp?: string;
}

/**
 * Spring Data's Page, serialised. Returned directly by endpoints that page with
 * a Pageable (currently GET /reading/templates). Distinct from PaginatedResult,
 * which is hand-built by StatsService.
 */
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  /** zero-based page index */
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// ── Auth ────────────────────────────────────────────────────────────────────

/**
 * com.smartprep.dto.response.AuthResponse without the token fields.
 *
 * The payload is flat: there is no nested `user` object. GET /auth/me and
 * PUT /auth/profile return exactly this, and AuthContext stores the whole login
 * response under `user`, which is why the two shapes have to line up.
 */
export interface User {
  userId: number;
  username: string;
  email: string;
  role: string;
  displayName?: string;
  avatarUrl?: string;
  emailVerified?: boolean;
  targetReadingScore?: number;
  targetWritingScore?: number;
  targetListeningScore?: number;
}

/** com.smartprep.dto.response.AuthResponse */
export interface AuthResponse extends User {
  token: string;
  /**
   * Legacy. The server now returns the refresh token in an httpOnly cookie and
   * the client never stores this; AuthContext deletes the old localStorage key
   * on every login.
   */
  refreshToken?: string;
  /** access token lifetime in milliseconds */
  expiresIn?: number;
}

// ── Shared question pieces ──────────────────────────────────────────────────

/** com.smartprep.dto.response.QuestionOptionResponse */
export interface QuestionOption {
  optionId: number;
  label: string;
  content: string;
  /** omitted before submission */
  isCorrect?: boolean;
}

// ── Reading ─────────────────────────────────────────────────────────────────

/**
 * ReadingQuizResponse.QuestionDto.
 *
 * correctAnswer and explanation are deliberately absent — the backend withholds
 * them until the quiz is submitted, and they appear on QuestionResult instead.
 */
export interface Question {
  questionId: number;
  questionType: string;
  questionText: string;
  options?: QuestionOption[];
  orderIndex?: number;
  /** JSON array of choices, for matching dropdowns */
  optionsJson?: string;
  /** word limit for completion types */
  wordLimit?: number;
  groupLabel?: string;
  groupId?: number;
  /** shared context, e.g. summary text containing the blanks */
  groupContext?: string;
}

/** com.smartprep.dto.response.ReadingQuizResponse */
export interface Quiz {
  quizId: number;
  topic: string;
  difficulty: string;
  moduleType?: string;
  passageText: string;
  timeLimitSeconds?: number;
  submitted: boolean;
  createdAt?: string;
  questions: Question[];
  /** populated for full-test assembly, listing every quiz in the set */
  quizIds?: number[];
}

/** ReadingResultResponse.QuestionResultDto */
export interface QuestionResult {
  questionId: number;
  questionType: string;
  questionText: string;
  options?: QuestionOption[];
  orderIndex?: number;
  correctAnswer?: string;
  userAnswer?: string;
  correct: boolean;
  explanation?: string;
  optionsJson?: string;
  wordLimit?: number;
  groupLabel?: string;
  groupId?: number;
  groupContext?: string;
  evidenceText?: string;
  evidenceOffset?: number;
  evidenceLength?: number;
}

/** com.smartprep.dto.response.ReadingResultResponse */
export interface ReadingResult {
  quizId: number;
  topic: string;
  difficulty: string;
  moduleType?: string;
  passageText: string;
  /** count of correct answers, not a map */
  correctAnswers?: number;
  totalQuestions?: number;
  bandScore?: number;
  createdAt?: string;
  submittedAt?: string;
  questions: QuestionResult[];
}

// ── Writing ─────────────────────────────────────────────────────────────────

/** com.smartprep.dto.response.WritingPromptResponse */
export interface WritingPrompt {
  promptId: number;
  promptText: string;
  essayType: string;
  taskType?: string;
  imageUrl?: string;
  visualData?: string;
}

/** WritingGradeResponse.ErrorDto */
export interface WritingError {
  originalSentence: string;
  errorType: string;
  explanation: string;
  correctedSentence: string;
}

/** com.smartprep.dto.response.WritingGradeResponse */
export interface WritingGradeResult {
  submissionId: number;
  promptId: number;
  promptText: string;
  essayType: string;
  essayText: string;
  visualData?: string;
  wordCount?: number;
  overallBand?: number;
  taskResponse?: number;
  coherence?: number;
  lexical?: number;
  grammar?: number;
  errors?: WritingError[];
  generalFeedback?: string;
  rewrittenVersion?: string;
  improvementNotes?: string[];
  submittedAt?: string;
}

// ── Listening ───────────────────────────────────────────────────────────────

/** ListeningPartResponse.QuestionDto — narrower than the reading Question */
export interface ListeningQuestion {
  questionId: number;
  questionType: string;
  questionText: string;
  options?: QuestionOption[];
  orderIndex?: number;
}

/**
 * com.smartprep.dto.response.ListeningPartResponse.
 *
 * The transcript is not part of this payload. Nothing imports this interface
 * yet; it is kept because listeningApi is still untyped JavaScript.
 */
export interface ListeningPart {
  partId: number;
  partNumber?: number;
  title: string;
  topic: string;
  audioUrl: string;
  audioStatus?: string;
  durationSeconds?: number;
  questionCount?: number;
  questions: ListeningQuestion[];
}

// ── Stats ───────────────────────────────────────────────────────────────────

/** AnalyticsOverviewResponse.SkillProgress */
export interface SkillOverview {
  skill: string;
  currentAvg?: number;
  targetScore?: number;
  progressPercent: number;
  totalTests: number;
  /** TARGET_MET or IN_PROGRESS */
  status: string;
  /** remaining gap to target */
  gap?: number;
}

/** com.smartprep.dto.response.AnalyticsOverviewResponse — GET /stats/overview */
export interface StatsOverview {
  skills: SkillOverview[];
  totalTests: number;
  targetMetCount: number;
  targetBand?: number;
  currentEstimate?: number;
}

/** ScoreTrendResponse.DataPoint */
export interface ScoreDataPoint {
  period: string;
  avgScore?: number;
}

/** com.smartprep.dto.response.ScoreTrendResponse — GET /stats/trend */
export interface ScoreTrend {
  skill: string;
  period: string;
  targetScore?: number;
  dataPoints: ScoreDataPoint[];
}

/** One entry of the map built by StatsService.getHistory */
export interface HistoryItem {
  historyId: number;
  skillType: string;
  score: number;
  recordedAt: string;
}

/**
 * The hand-rolled page shape returned by StatsService.getHistory. Note `page`
 * rather than Spring's `number`; see SpringPage for endpoints that return a
 * real Page.
 */
export interface PaginatedResult<T> {
  items: T[];
  /** zero-based page index */
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

// ── Analytics ───────────────────────────────────────────────────────────────

/** AnalyticsService.WeaknessDto — GET /analytics/weakness */
export interface WeaknessAnalysis {
  weakestType?: string;
  weakestAccuracy?: number;
  accuracies: Record<string, number>;
  recommendation?: string;
}

// ── Exam attempts ───────────────────────────────────────────────────────────

export interface StartAttemptRequest {
  skillType: string;
  examReferenceIds?: string;
  durationOverride?: number;
}

export interface CompleteAttemptRequest {
  autoSubmitted?: boolean;
  timeSpentTask1?: number;
  timeSpentTask2?: number;
}

/** com.smartprep.dto.response.AttemptResponse */
export interface AttemptResponse {
  attemptId: number;
  skillType: string;
  durationSeconds: number;
  startedAt: string;
  deadline: string;
  status: string;
  autoSubmitted?: boolean;
  timeSpentSeconds?: number;
  timeSpentTask1?: number;
  timeSpentTask2?: number;
  examReferenceIds?: string;
  suggestedTask1Duration?: number;
  suggestedTask2Duration?: number;
}
