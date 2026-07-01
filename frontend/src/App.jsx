import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { ThemeProvider } from './context/ThemeContext';
import { ReadingProvider } from './context/ReadingContext';
import { MockTestProvider } from './context/MockTestContext';
import ProtectedRoute from './components/common/ProtectedRoute';
import AdminRoute from './components/common/AdminRoute';
import UserRoute from './components/common/UserRoute';
import MainLayout from './components/common/MainLayout';
import UserLayout from './components/common/UserLayout';

// Lazy loaded page components
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const ReadingConfigPage = lazy(() => import('./pages/ReadingConfigPage'));
const ReadingExamPage = lazy(() => import('./pages/ReadingExamPage'));
const ReadingResultPage = lazy(() => import('./pages/ReadingResultPage'));
const ReadingHistoryPage = lazy(() => import('./pages/ReadingHistoryPage'));
const WritingPromptListPage = lazy(() => import('./pages/WritingPromptListPage'));
const WritingEditorPage = lazy(() => import('./pages/WritingEditorPage'));
const WritingResultPage = lazy(() => import('./pages/WritingResultPage'));
const WritingHistoryPage = lazy(() => import('./pages/WritingHistoryPage'));
const ListeningPracticePage = lazy(() => import('./pages/ListeningPracticePage'));
const ListeningExamPage = lazy(() => import('./pages/ListeningExamPage'));
const ListeningResultPage = lazy(() => import('./pages/ListeningResultPage'));
const ListeningHistoryPage = lazy(() => import('./pages/ListeningHistoryPage'));
const MockTestLobbyPage = lazy(() => import('./pages/MockTestLobbyPage'));
const MockTestSessionPage = lazy(() => import('./pages/MockTestSessionPage'));
const MockTestResultPage = lazy(() => import('./pages/MockTestResultPage'));
const AdminDashboardPage = lazy(() => import('./pages/AdminDashboardPage'));
const AdminUsersPage = lazy(() => import('./pages/AdminUsersPage'));
const AdminMockTestsPage = lazy(() => import('./pages/AdminMockTestsPage'));
const AdminWritingPromptsPage = lazy(() => import('./pages/AdminWritingPromptsPage'));
const AdminReadingQuizzesPage = lazy(() => import('./pages/AdminReadingQuizzesPage'));
const AdminListeningListPage = lazy(() => import('./pages/AdminListeningListPage'));
const AdminPartEditorPage = lazy(() => import('./pages/AdminPartEditorPage'));
const ReadingFullExamPage = lazy(() => import('./pages/ReadingFullExamPage'));
const ReadingFullResultPage = lazy(() => import('./pages/ReadingFullResultPage'));
const WritingFullExamPage = lazy(() => import('./pages/WritingFullExamPage'));
const WritingFullResultPage = lazy(() => import('./pages/WritingFullResultPage'));
const HistoryReviewPage = lazy(() => import('./pages/HistoryReviewPage'));
const VocabularyPage = lazy(() => import('./pages/VocabularyPage'));
const HistoryPage = lazy(() => import('./pages/HistoryPage'));
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage'));
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'));
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage'));

import './index.css';

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider>
      <ToastProvider>
        <AuthProvider>
          <Suspense fallback={
            <div className="loading-spinner" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center' }}>
              <div className="spinner" style={{ width: 40, height: 40, border: '4px solid var(--color-border-subtle)', borderTopColor: 'var(--color-primary)', borderRadius: '50%', animation: 'spin 1s linear infinite' }}></div>
              <p style={{ marginTop: 16, color: 'var(--color-text-muted)', fontFamily: 'var(--font-heading)' }}>Loading page...</p>
            </div>
          }>
            <Routes>
              {/* Public */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="/verify-email" element={<VerifyEmailPage />} />

            {/* Student Portal Routes wrapped in UserLayout & UserRoute */}
            <Route element={<ProtectedRoute><UserRoute><UserLayout /></UserRoute></ProtectedRoute>}>
              <Route path="/dashboard" element={<DashboardPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/history" element={<HistoryPage />} />

              {/* Mock Tests */}
              <Route path="/mock-tests" element={
                <MockTestProvider><MockTestLobbyPage /></MockTestProvider>
              } />
              <Route path="/mock-tests/result/:submissionId" element={
                <MockTestProvider><MockTestResultPage /></MockTestProvider>
              } />

              {/* Reading (config, result, history use UserLayout) */}
              <Route path="/reading" element={<ReadingConfigPage />} />
              <Route path="/reading/result/:quizId" element={
                <ReadingProvider><ReadingResultPage /></ReadingProvider>
              } />
              <Route path="/reading/full-result" element={<ReadingFullResultPage />} />
              <Route path="/reading/history" element={<ReadingHistoryPage />} />

              {/* Writing */}
              <Route path="/writing" element={<WritingPromptListPage />} />
              <Route path="/writing/result/:submissionId" element={<WritingResultPage />} />
              <Route path="/writing/full-result" element={<WritingFullResultPage />} />
              <Route path="/writing/history" element={<WritingHistoryPage />} />

              {/* Listening (practice, result, history use UserLayout) */}
              <Route path="/listening" element={<ListeningPracticePage />} />
              <Route path="/listening/result/:testId" element={<ListeningResultPage />} />
              <Route path="/listening/history" element={<ListeningHistoryPage />} />

              {/* Vocabulary Builder */}
              <Route path="/vocabulary" element={<VocabularyPage />} />

              {/* History Review (across all skills) */}
              <Route path="/history/:historyId/review" element={<HistoryReviewPage />} />
            </Route>

            {/* Admin Portal Routes wrapped in MainLayout & AdminRoute */}
            <Route element={<ProtectedRoute><AdminRoute><MainLayout /></AdminRoute></ProtectedRoute>}>
              <Route path="/admin" element={<AdminDashboardPage />} />
              <Route path="/admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
              <Route path="/admin/mock-tests" element={<AdminRoute><AdminMockTestsPage /></AdminRoute>} />
              <Route path="/admin/writing-prompts" element={<AdminRoute><AdminWritingPromptsPage /></AdminRoute>} />
              <Route path="/admin/reading-quizzes" element={<AdminRoute><AdminReadingQuizzesPage /></AdminRoute>} />
              <Route path="/admin/listening" element={<AdminRoute><AdminListeningListPage /></AdminRoute>} />
              <Route path="/admin/listening/new" element={<AdminRoute><AdminPartEditorPage /></AdminRoute>} />
              <Route path="/admin/listening/edit/:partId" element={<AdminRoute><AdminPartEditorPage /></AdminRoute>} />
            </Route>

            {/* Full-screen student exam pages (no UserLayout, but protected with UserRoute) */}
            <Route path="/reading/exam/:quizId" element={
              <ProtectedRoute>
                <UserRoute>
                  <ReadingProvider><ReadingExamPage /></ReadingProvider>
                </UserRoute>
              </ProtectedRoute>
            } />
            <Route path="/writing/editor/:promptId" element={
              <ProtectedRoute>
                <UserRoute>
                  <WritingEditorPage />
                </UserRoute>
              </ProtectedRoute>
            } />
            <Route path="/reading/full-exam" element={
              <ProtectedRoute><UserRoute><ReadingFullExamPage /></UserRoute></ProtectedRoute>
            } />
            <Route path="/writing/full-exam" element={
              <ProtectedRoute><UserRoute><WritingFullExamPage /></UserRoute></ProtectedRoute>
            } />
            <Route path="/listening/exam" element={
              <ProtectedRoute><UserRoute><ListeningExamPage /></UserRoute></ProtectedRoute>
            } />
            <Route path="/mock-tests/take/:sessionId" element={
              <ProtectedRoute>
                <UserRoute>
                  <MockTestProvider><MockTestSessionPage /></MockTestProvider>
                </UserRoute>
              </ProtectedRoute>
            } />

            {/* Default redirect */}
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Suspense>
      </AuthProvider>
      </ToastProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
