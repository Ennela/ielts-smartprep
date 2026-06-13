export interface ApiResponse<T> {
  status: string | number;
  message?: string;
  data: T;
}

export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  displayName?: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  user: User;
}

export interface SkillOverview {
  skill: string;
  currentAvg: string | number;
  targetScore: string | number;
  progressPercent: number;
  totalTests: number;
  gap: number;
  status: string;
}

export interface StatsOverview {
  targetBand: string;
  currentEstimate: string;
  skills: SkillOverview[];
  improvementTip?: string;
}

export interface ScoreDataPoint {
  date: string;
  score: number;
}

export interface ScoreTrend {
  skill: string;
  targetScore: number;
  dataPoints: ScoreDataPoint[];
}

export interface WeaknessAnalysis {
  accuracies: Record<string, number>;
  recommendation?: string;
}

export interface HistoryItem {
  id: number;
  skillType: string;
  score: number;
  recordedAt: string;
}

export interface PaginatedResult<T> {
  items: T[];
  totalPages: number;
  totalItems: number;
  currentPage: number;
}

export interface WritingPrompt {
  id: number;
  title: string;
  promptText: string;
  essayType: string;
  createdAt: string;
}

export interface EssayGradingResult {
  score: number;
  feedback: string;
  grammaticalFeedback?: string;
  coherenceFeedback?: string;
  lexicalFeedback?: string;
}

export interface Question {
  id: number;
  questionNumber: number;
  questionText: string;
  questionType: string;
  options?: string[];
  correctAnswer?: string;
}

export interface Quiz {
  id: number;
  title: string;
  passageText: string;
  topic: string;
  difficulty: string;
  questions: Question[];
}

export interface QuizSubmissionResult {
  score: number;
  correctAnswers: Record<number, string>;
  isCorrect: Record<number, boolean>;
  explanations?: Record<number, string>;
}

export interface ListeningPart {
  id: number;
  partNumber: number;
  title: string;
  audioUrl: string;
  transcript?: string;
  topic: string;
  questions: Question[];
}

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

export interface AttemptResponse {
  attemptId: number;
  skillType: string;
  durationSeconds: number;
  startedAt: string;
  deadline: string;
  status: string;
  autoSubmitted: boolean;
  timeSpentSeconds?: number;
  timeSpentTask1?: number;
  timeSpentTask2?: number;
  examReferenceIds?: string;
  suggestedTask1Duration?: number;
  suggestedTask2Duration?: number;
}
