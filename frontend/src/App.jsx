import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ReadingProvider } from './context/ReadingContext';
import ProtectedRoute from './components/common/ProtectedRoute';
import AdminRoute from './components/common/AdminRoute';
import MainLayout from './components/common/MainLayout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import ProfilePage from './pages/ProfilePage';
import ReadingConfigPage from './pages/ReadingConfigPage';
import ReadingExamPage from './pages/ReadingExamPage';
import ReadingResultPage from './pages/ReadingResultPage';
import ReadingHistoryPage from './pages/ReadingHistoryPage';
import WritingPromptListPage from './pages/WritingPromptListPage';
import WritingEditorPage from './pages/WritingEditorPage';
import WritingResultPage from './pages/WritingResultPage';
import WritingHistoryPage from './pages/WritingHistoryPage';
import ListeningPracticePage from './pages/ListeningPracticePage';
import ListeningExamPage from './pages/ListeningExamPage';
import ListeningResultPage from './pages/ListeningResultPage';
import ListeningHistoryPage from './pages/ListeningHistoryPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import AdminUsersPage from './pages/AdminUsersPage';
import AdminWritingPromptsPage from './pages/AdminWritingPromptsPage';
import AdminReadingQuizzesPage from './pages/AdminReadingQuizzesPage';

import './index.css';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Protected with MainLayout */}
          <Route element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />

            {/* Reading (config, result, history use MainLayout) */}
            <Route path="/reading" element={<ReadingConfigPage />} />
            <Route path="/reading/result/:quizId" element={
              <ReadingProvider><ReadingResultPage /></ReadingProvider>
            } />
            <Route path="/reading/history" element={<ReadingHistoryPage />} />

            {/* Writing */}
            <Route path="/writing" element={<WritingPromptListPage />} />
            <Route path="/writing/editor/:promptId" element={<WritingEditorPage />} />
            <Route path="/writing/result/:submissionId" element={<WritingResultPage />} />
            <Route path="/writing/history" element={<WritingHistoryPage />} />

            {/* Listening (practice, result, history use MainLayout) */}
            <Route path="/listening" element={<ListeningPracticePage />} />
            <Route path="/listening/result/:testId" element={<ListeningResultPage />} />
            <Route path="/listening/history" element={<ListeningHistoryPage />} />

            {/* Admin (guard: ADMIN role only) */}
            <Route path="/admin" element={<AdminRoute><AdminDashboardPage /></AdminRoute>} />
            <Route path="/admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
            <Route path="/admin/writing-prompts" element={<AdminRoute><AdminWritingPromptsPage /></AdminRoute>} />
            <Route path="/admin/reading-quizzes" element={<AdminRoute><AdminReadingQuizzesPage /></AdminRoute>} />


          </Route>

          {/* Full-screen exam pages (no MainLayout) */}
          <Route path="/reading/exam/:quizId" element={
            <ProtectedRoute>
              <ReadingProvider><ReadingExamPage /></ReadingProvider>
            </ProtectedRoute>
          } />
          <Route path="/listening/exam" element={
            <ProtectedRoute><ListeningExamPage /></ProtectedRoute>
          } />

          {/* Default redirect */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
