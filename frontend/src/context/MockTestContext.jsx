import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import mockTestApi from '../api/mockTestApi';

const MockTestContext = createContext(null);

export function MockTestProvider({ children }) {
  const [activeSession, setActiveSession] = useState(null);
  const [answers, setAnswers] = useState({});
  const [timeRemaining, setTimeRemaining] = useState(0);
  const [isOffline, setIsOffline] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const answersRef = useRef({});
  const timerRef = useRef(0);
  const sessionRef = useRef(null);

  // Sync refs to keep useEffect handlers updated without re-running loops
  useEffect(() => {
    answersRef.current = answers;
  }, [answers]);

  useEffect(() => {
    timerRef.current = timeRemaining;
  }, [timeRemaining]);

  useEffect(() => {
    sessionRef.current = activeSession;
  }, [activeSession]);

  // Load an existing session or start a new one
  const startOrResumeTest = async (mockTestId) => {
    try {
      setLoading(true);
      setError(null);
      const res = await mockTestApi.startMockTest(mockTestId);
      const sessionData = res.data.data;
      initializeSession(sessionData);
    } catch (err) {
      setError(err.message || 'Failed to start mock test');
      setLoading(false);
    }
  };

  const loadActiveSession = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await mockTestApi.getCurrentSession();
      const sessionData = res.data.data;
      initializeSession(sessionData);
      return sessionData;
    } catch (err) {
      // It's normal to have no active session
      setLoading(false);
      return null;
    }
  };

  const initializeSession = (sessionData) => {
    setActiveSession(sessionData);
    setTimeRemaining(sessionData.timeRemainingSeconds);

    // Parse progressJson
    let serverAnswers = {};
    if (sessionData.progressJson) {
      try {
        serverAnswers = JSON.parse(sessionData.progressJson);
      } catch (e) {
        serverAnswers = {};
      }
    }

    // Recover from local storage if local storage has a newer state
    const localKey = `mock_session_${sessionData.sessionId}`;
    const localBackupStr = localStorage.getItem(localKey);
    if (localBackupStr) {
      try {
        const localBackup = JSON.parse(localBackupStr);
        // If backup is newer than lastSyncedAt in sessionData (or if we had changes offline)
        const serverSyncedTime = new Date(sessionData.lastSyncedAt || sessionData.startedAt).getTime();
        if (localBackup.timestamp > serverSyncedTime) {
          logInfo('Restoring from newer local storage backup.');
          serverAnswers = { ...serverAnswers, ...localBackup.answers };
          // If local backup has a different time remaining, we can align it (bound by server limit check)
          if (localBackup.timeRemaining < sessionData.timeRemainingSeconds) {
            setTimeRemaining(localBackup.timeRemaining);
          }
        }
      } catch (e) {
        // ignore malformed backup
      }
    }

    setAnswers(serverAnswers);
    setLoading(false);
  };

  // Log helpers
  const logInfo = (msg) => {
    console.log(`[MockTestContext] ${msg}`);
  };

  // Update answer in memory and local storage
  const setAnswer = useCallback((questionId, value) => {
    setAnswers((prev) => {
      const next = { ...prev, [questionId]: value };
      
      // Save local backup immediately
      if (sessionRef.current) {
        const localKey = `mock_session_${sessionRef.current.sessionId}`;
        localStorage.setItem(localKey, JSON.stringify({
          answers: next,
          timeRemaining: timerRef.current,
          timestamp: Date.now()
        }));
      }
      return next;
    });
  }, []);

  // Send progress to server (Stateless helper)
  const syncWithServer = async (sessionId, section, time, answersMap) => {
    try {
      setIsSyncing(true);
      const jsonString = JSON.stringify(answersMap);
      await mockTestApi.saveProgress(sessionId, section, time, jsonString);
      setIsOffline(false);
    } catch (err) {
      setIsOffline(true);
      console.warn('Network offline, saving progress locally.', err);
    } finally {
      setIsSyncing(false);
    }
  };

  // Tick the countdown timer every second
  useEffect(() => {
    if (!activeSession || activeSession.status !== 'IN_PROGRESS') return;

    const interval = setInterval(() => {
      setTimeRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          handleTimeExpired();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [activeSession]);

  // Handle time expiration
  const handleTimeExpired = async () => {
    logInfo('Time expired for current section.');
    if (!sessionRef.current) return;

    if (sessionRef.current.currentSection === 'LISTENING' || sessionRef.current.currentSection === 'READING') {
      // Auto-transition to next section
      await advanceSection();
    } else {
      // Writing finished: force final submit
      await submitExam();
    }
  };

  // Background sync loop: every 30 seconds
  useEffect(() => {
    if (!activeSession || activeSession.status !== 'IN_PROGRESS') return;

    const syncInterval = setInterval(() => {
      syncWithServer(
        activeSession.sessionId,
        activeSession.currentSection,
        timerRef.current,
        answersRef.current
      );
    }, 30000);

    return () => clearInterval(syncInterval);
  }, [activeSession]);

  // Transition to next section
  const advanceSection = async () => {
    if (!activeSession) return;
    try {
      setLoading(true);
      setError(null);
      
      const jsonString = JSON.stringify(answersRef.current);
      const res = await mockTestApi.nextSection(
        activeSession.sessionId,
        activeSession.currentSection,
        timerRef.current,
        jsonString
      );
      
      const sessionData = res.data.data;
      setActiveSession(sessionData);
      setTimeRemaining(sessionData.timeRemainingSeconds);
      // We don't wipe answers since answers contains all cumulative sections
      
      // Update local storage backup with new section timer
      const localKey = `mock_session_${sessionData.sessionId}`;
      localStorage.setItem(localKey, JSON.stringify({
        answers: answersRef.current,
        timeRemaining: sessionData.timeRemainingSeconds,
        timestamp: Date.now()
      }));

    } catch (err) {
      setError(err.message || 'Failed to advance to next section. Please check internet connection.');
    } finally {
      setLoading(false);
    }
  };

  // Submit full exam
  const submitExam = async () => {
    if (!activeSession) return null;
    try {
      setLoading(true);
      setError(null);

      const jsonString = JSON.stringify(answersRef.current);
      const res = await mockTestApi.submitExam(activeSession.sessionId, jsonString);
      const submissionData = res.data.data;

      // Clear local storage backups
      localStorage.removeItem(`mock_session_${activeSession.sessionId}`);
      setActiveSession(null);
      setAnswers({});
      setTimeRemaining(0);

      return submissionData;
    } catch (err) {
      setError(err.message || 'Failed to submit exam. Please try again.');
      setLoading(false);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const clearSession = () => {
    if (activeSession) {
      localStorage.removeItem(`mock_session_${activeSession.sessionId}`);
    }
    setActiveSession(null);
    setAnswers({});
    setTimeRemaining(0);
    setError(null);
  };

  const getOverallTimeRemaining = () => {
    if (!activeSession) return 0;
    const currentSec = activeSession.currentSection;
    const currentSecRemaining = timeRemaining;
    
    const readingDuration = 3600; // 60 mins
    const writingDuration = 3600; // 60 mins

    if (currentSec === 'LISTENING') {
      return currentSecRemaining + readingDuration + writingDuration;
    } else if (currentSec === 'READING') {
      return currentSecRemaining + writingDuration;
    } else if (currentSec === 'WRITING') {
      return currentSecRemaining;
    }
    return 0;
  };

  const overallTimeRemaining = getOverallTimeRemaining();

  return (
    <MockTestContext.Provider value={{
      activeSession,
      answers,
      timeRemaining,
      overallTimeRemaining,
      isOffline,
      isSyncing,
      loading,
      error,
      startOrResumeTest,
      loadActiveSession,
      setAnswer,
      advanceSection,
      submitExam,
      clearSession,
      syncNow: () => syncWithServer(activeSession?.sessionId, activeSession?.currentSection, timerRef.current, answersRef.current)
    }}>
      {children}
    </MockTestContext.Provider>
  );
}

export const useMockTest = () => useContext(MockTestContext);
