import { useEffect, useState, useMemo, useCallback } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMockTest } from '../context/MockTestContext';
import AudioPlayer from '../components/listening/AudioPlayer';
import McqQuestion from '../components/listening/McqQuestion';
import FillBlankQuestion from '../components/listening/FillBlankQuestion';
import QuestionPanel, { FlagButton } from '../components/questions/QuestionPanel';
import QuestionNavigator from '../components/mocktest/QuestionNavigator';
import { usesGroupedLayout, questionOffset } from '../components/listening/groupedLayout';
import PassageViewer from '../components/reading/PassageViewer';
import MockTestQuestionPanel from '../components/mocktest/MockTestQuestionPanel';
import { useToast } from '../context/ToastContext';
import { isTask1Type } from '../constants/examTypes';
import { useConfirm } from '../context/ConfirmContext';

export default function MockTestSessionPage() {
  const navigate = useNavigate();
  const confirm = useConfirm();
  const { error: showErrorToast } = useToast();
  const { sessionId } = useParams();
  const {
    activeSession,
    answers,
    timeRemaining,
    overallTimeRemaining,
    isOffline,
    isSyncing,
    loading,
    error,
    latestSubmissionId,
    clearLatestSubmissionId,
    loadActiveSession,
    loadSession,
    setAnswer,
    advanceSection,
    submitExam,
  } = useMockTest();

  // Selected sub-tabs within sections
  const [activeListeningPart, setActiveListeningPart] = useState(0);
  const [activeReadingQuiz, setActiveReadingQuiz] = useState(0);
  const [activeWritingTask, setActiveWritingTask] = useState(0); // 0 for Task 1, 1 for Task 2
  const [submitting, setSubmitting] = useState(false);

  // Questions flagged for review, kept per sitting so a reload does not clear them.
  const flagKey = activeSession?.sessionId ? `mock_flags_${activeSession.sessionId}` : null;
  const [flagged, setFlagged] = useState(() => new Set());
  useEffect(() => {
    if (!flagKey) return;
    try {
      setFlagged(new Set(JSON.parse(localStorage.getItem(flagKey) || '[]')));
    } catch {
      setFlagged(new Set());
    }
  }, [flagKey]);

  // `value` forces the state, for a group of questions flagged together.
  const toggleFlag = useCallback((questionId, value) => {
    setFlagged((prev) => {
      const next = new Set(prev);
      const on = value ?? !next.has(questionId);
      if (on) next.add(questionId); else next.delete(questionId);
      if (flagKey) {
        try { localStorage.setItem(flagKey, JSON.stringify([...next])); } catch { /* storage full or blocked */ }
      }
      return next;
    });
  }, [flagKey]);

  useEffect(() => {
    // Ensure active session is loaded on mount. Mount only: after submitExam() clears the
    // session, the redirect effect below resets latestSubmissionId too, and re-running this
    // would find no session (404) and send the user to the lobby instead of the result page.
    if (!activeSession && !latestSubmissionId) {
      (sessionId ? loadSession(sessionId) : loadActiveSession()).then((session) => {
        if (!session) {
          navigate('/mock-tests');
        }
      });
    }
  }, []);

  // Handle auto-submission redirect
  useEffect(() => {
    if (latestSubmissionId) {
      const subId = latestSubmissionId;
      clearLatestSubmissionId();
      navigate(`/mock-tests/result/${subId}`);
    }
  }, [latestSubmissionId, navigate, clearLatestSubmissionId]);

  // Tab close/reload warning interceptor
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      const message = 'Are you sure you want to leave? Your exam is in progress and the timer will continue to run!';
      e.preventDefault();
      e.returnValue = message;
      return message;
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, []);

  const handleExitLobby = async () => {
    const ok = await confirm({
      title: 'Leave for the lobby?',
      message: 'The timer does not pause: the test keeps running on the server while you are away.',
      confirmLabel: 'Go to lobby',
    });
    if (ok) {
      navigate('/mock-tests');
    }
  };

  // Helper to format countdown timer
  const formatTime = (seconds) => {
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    
    if (hrs > 0) {
      return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const currentSection = activeSession?.currentSection || 'LISTENING';
  const audioBaseUrl = import.meta.env.VITE_API_URL?.replace('/api/v1', '') ?? '';

  // ── Section 1: Listening Helpers ──
  const listeningParts = activeSession?.listeningParts || [];
  const currentListeningPart = listeningParts[activeListeningPart];
  
  // ── Section 2: Reading Helpers ──
  const readingQuizzes = activeSession?.readingQuizzes || [];
  const currentReadingQuiz = readingQuizzes[activeReadingQuiz];

  // ── Section 3: Writing Helpers ──
  const writingPrompts = activeSession?.writingPrompts || [];
  
  // Sort writing prompts so Task 1 (shorter or specific type) comes first
  const sortedWritingPrompts = useMemo(() => {
    return [...writingPrompts].sort((a, b) => {
      const isA1 = isTask1Type(a.essayType);
      const isB1 = isTask1Type(b.essayType);
      if (isA1 && !isB1) return -1;
      if (!isA1 && isB1) return 1;
      return 0;
    });
  }, [writingPrompts]);

  const currentWritingPrompt = sortedWritingPrompts[activeWritingTask];
  const essayTask1Text = answers['w_task1'] || '';
  const essayTask2Text = answers['w_task2'] || '';

  const getWordCount = (text) => {
    if (!text?.trim()) return 0;
    return text.trim().split(/\s+/).filter(w => w.length > 0).length;
  };

  const wordCountTask1 = getWordCount(essayTask1Text);
  const wordCountTask2 = getWordCount(essayTask2Text);

  // Compute question progress
  const answeredListeningCount = useMemo(() => {
    if (!activeSession?.listeningParts) return 0;
    let count = 0;
    activeSession.listeningParts.forEach(part => {
      part.questions?.forEach(q => {
        if (answers[q.questionId]?.trim()) count++;
      });
    });
    return count;
  }, [answers, activeSession]);

  const totalListeningQuestions = useMemo(() => {
    if (!activeSession?.listeningParts) return 0;
    return activeSession.listeningParts.reduce((acc, p) => acc + (p.questions?.length || 0), 0);
  }, [activeSession]);

  const answeredReadingCount = useMemo(() => {
    if (!activeSession?.readingQuizzes) return 0;
    let count = 0;
    activeSession.readingQuizzes.forEach(quiz => {
      quiz.questions?.forEach(q => {
        if (answers[q.questionId]?.trim()) count++;
      });
    });
    return count;
  }, [answers, activeSession]);

  const totalReadingQuestions = useMemo(() => {
    if (!activeSession?.readingQuizzes) return 0;
    return activeSession.readingQuizzes.reduce((acc, q) => acc + (q.questions?.length || 0), 0);
  }, [activeSession]);

  // The navigator strip: one group per part or passage, numbered as the questions are shown.
  const navGroups = useMemo(() => {
    const answeredOf = (id) => !!String(answers[id] ?? '').trim();
    const sorted = (qs) => [...(qs || [])].sort((a, b) => a.orderIndex - b.orderIndex);
    if (currentSection === 'LISTENING') {
      return listeningParts.map((part, i) => {
        const offset = questionOffset(listeningParts, i);
        const grouped = usesGroupedLayout(part.questions);
        return {
          index: i,
          label: `Part ${i + 1}`,
          items: sorted(part.questions).map((q, idx) => ({
            questionId: q.questionId,
            number: offset + (grouped ? (q.orderIndex || idx + 1) : idx + 1),
            answered: answeredOf(q.questionId),
            flagged: flagged.has(q.questionId),
          })),
        };
      });
    }
    if (currentSection === 'READING') {
      return readingQuizzes.map((quiz, i) => ({
        index: i,
        label: `Passage ${i + 1}`,
        items: sorted(quiz.questions).map((q, idx) => ({
          questionId: q.questionId,
          number: q.orderIndex || idx + 1,
          answered: answeredOf(q.questionId),
          flagged: flagged.has(q.questionId),
        })),
      }));
    }
    return [];
  }, [currentSection, listeningParts, readingQuizzes, answers, flagged]);

  // A jump is kept in state and carried out by the effect below, after the part or
  // passage it targets has rendered; reaching for the element straight away found the
  // previous part's DOM when the jump crossed parts.
  const [jumpRequest, setJumpRequest] = useState(null);
  const jumpToQuestion = (index, questionId) => {
    if (currentSection === 'LISTENING') setActiveListeningPart(index);
    else setActiveReadingQuiz(index);
    setJumpRequest({ questionId, at: Date.now() });
  };

  useEffect(() => {
    if (!jumpRequest) return undefined;
    const { questionId } = jumpRequest;
    const target = document.querySelector(`[data-question-id="${questionId}"]`)
      || document.querySelector(`[data-question-ids~="${questionId}"]`);
    if (!target) return undefined;
    target.scrollIntoView({ block: 'center', behavior: 'smooth' });
    const field = target.matches('input, select, textarea')
      ? target
      : target.querySelector('input, select, textarea, button:not(.question-flag)');
    field?.focus({ preventScroll: true });
    target.classList.add('question-jump-target');
    const timer = setTimeout(() => target.classList.remove('question-jump-target'), 1200);
    return () => clearTimeout(timer);
  }, [jumpRequest]);

  // "Part 1: 3, 7; Part 3: 22", for the review before a section is closed.
  const listNumbers = (pick) => navGroups
    .map((g) => ({ label: g.label, nums: g.items.filter(pick).map((it) => it.number) }))
    .filter((g) => g.nums.length)
    .map((g) => (navGroups.length > 1 ? `${g.label}: ${g.nums.join(', ')}` : g.nums.join(', ')))
    .join('; ');

  // Handle section switch / submission
  const handleNextSection = async () => {
    const currentName = currentSection === 'LISTENING' ? 'Listening' : 'Reading';
    const nextName = currentSection === 'LISTENING' ? 'Reading' : 'Writing';
    
    const unanswered = currentSection === 'LISTENING'
      ? totalListeningQuestions - answeredListeningCount
      : totalReadingQuestions - answeredReadingCount;
    const flaggedCount = navGroups.reduce((n, g) => n + g.items.filter((it) => it.flagged).length, 0);
    const review = [
      unanswered > 0 ? `Unanswered (${unanswered}): ${listNumbers((it) => !it.answered)}` : null,
      flaggedCount > 0 ? `Flagged for review (${flaggedCount}): ${listNumbers((it) => it.flagged)}` : null,
    ].filter(Boolean).join('\n');
    const ok = await confirm({
      title: `Finish ${currentName}?`,
      message: `You will move on to ${nextName} and cannot come back to ${currentName}.${review ? `\n\n${review}` : ''}`,
      confirmLabel: unanswered > 0 ? `Start ${nextName} anyway` : `Start ${nextName}`,
      cancelLabel: 'Keep reviewing',
    });
    if (ok) {
      await advanceSection();
    }
  };

  const handleSubmitTest = async () => {
    if (wordCountTask1 < 150 || wordCountTask2 < 250) {
      const confirmStr = `Your essays are below the minimum length (Task 1: ${wordCountTask1}/150 words, Task 2: ${wordCountTask2}/250 words).`;
      if (!(await confirm({ title: 'Submit the mock test?', message: confirmStr, confirmLabel: 'Submit anyway' }))) return;
    } else {
      if (!(await confirm({ title: 'Submit the mock test?', message: 'Your answers go to AI grading and the test closes.', confirmLabel: 'Submit' }))) return;
    }

    try {
      setSubmitting(true);
      const submission = await submitExam();
      if (flagKey) localStorage.removeItem(flagKey);
      if (submission) {
        navigate(`/mock-tests/result/${submission.submissionId}`);
      }
    } catch (err) {
      console.error(err);
      showErrorToast('Failed to submit exam. Please verify connection and retry.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading && !activeSession) {
    return (
      <div className="loading-screen">
        <span className="spinner" style={{ width: 24, height: 24 }} />
        Loading mock test session...
      </div>
    );
  }

  if (error && !activeSession) {
    return (
      <div className="loading-screen">
        <div>
          <p style={{ color: 'var(--error)' }}>{error}</p>
          <button className="btn btn-primary" onClick={() => navigate('/mock-tests')} style={{ marginTop: 16 }}>
            Back to Lobby
          </button>
        </div>
      </div>
    );
  }

  if (!activeSession) return null;

  return (
    <div className="reading-exam-page" style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      
      {/* ── Exam Header ── */}
      <header className="exam-topbar">
        <div className="exam-topbar-left">
          <span className="exam-logo" onClick={handleExitLobby} style={{ cursor: 'pointer' }}>IELTS Full Mock Test</span>
          <div className="exam-divider-v" />
          <span className="exam-topic-badge" style={{ background: 'var(--surface-container-highest)', color: 'var(--primary)', fontWeight: 700 }}>
            {currentSection}
          </span>
          {isOffline && (
            <span className="exam-diff-badge" style={{ background: 'rgba(186, 26, 26, 0.08)', color: 'var(--error)', fontWeight: 700 }}>
              OFFLINE MODE (Saved locally)
            </span>
          )}
          {!isOffline && isSyncing && (
            <span className="exam-diff-badge" style={{ background: 'rgba(0, 108, 74, 0.08)', color: 'var(--secondary)' }}>
              Autosaving...
            </span>
          )}
        </div>

        <div className="exam-topbar-center" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
          <h1 style={{ fontSize: '0.95rem', fontWeight: 700, margin: 0 }}>{activeSession.title}</h1>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {['LISTENING', 'READING', 'WRITING'].map((sec, idx) => {
              const secIdx = ['LISTENING', 'READING', 'WRITING'].indexOf(currentSection);
              const isActive = idx === secIdx;
              const isCompleted = idx < secIdx;
              return (
                <div key={sec} style={{ display: 'flex', alignItems: 'center', gap: '6px', opacity: isActive || isCompleted ? 1 : 0.45 }}>
                  <div style={{
                    width: '18px', height: '18px', borderRadius: '50%',
                    background: isCompleted ? 'var(--secondary)' : isActive ? 'var(--primary)' : 'var(--surface-container-high)',
                    color: isCompleted || isActive ? 'var(--on-primary)' : 'var(--on-surface-variant)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.68rem', fontWeight: 'bold'
                  }}>
                    {isCompleted ? '✓' : idx + 1}
                  </div>
                  <span style={{ fontSize: '0.75rem', fontWeight: isActive ? 700 : 500 }}>
                    {sec.charAt(0) + sec.slice(1).toLowerCase()}
                  </span>
                  {idx < 2 && <span style={{ color: 'var(--outline-variant)', fontSize: '10px' }}>&rarr;</span>}
                </div>
              );
            })}
          </div>
        </div>

        <div className="exam-topbar-right">
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <div className={`exam-timer-pill ${timeRemaining < 300 ? 'warning' : ''}`} title="Section Remaining Time">
              <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 16 }}>hourglass_empty</span>
              Section: {formatTime(timeRemaining)}
            </div>
            <div className="exam-timer-pill" style={{ background: 'var(--surface-container-highest)', color: 'var(--primary)' }} title="Overall Remaining Time">
              <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 16 }}>schedule</span>
              Total: {formatTime(overallTimeRemaining)}
            </div>
          </div>
          {currentSection === 'WRITING' ? (
            <button 
              className="btn btn-primary btn-submit-exam" 
              onClick={handleSubmitTest}
              disabled={submitting}
            >
              {submitting ? 'Submitting...' : 'Submit Exam'}
            </button>
          ) : (
            <button 
              className="btn btn-primary btn-submit-exam" 
              onClick={handleNextSection}
            >
              Next Section
            </button>
          )}
        </div>
      </header>

      {/* ── Sub-Tabs Navigation for Section Parts ── */}
      <div className="listening-part-tabs" style={{ padding: '12px 32px', background: 'var(--surface-container-low)', borderBottom: '1px solid var(--outline-variant)', display: 'flex', gap: '8px' }}>
        {currentSection === 'LISTENING' && listeningParts.map((part, index) => (
          <button
            key={part.partId}
            className={`part-tab ${index === activeListeningPart ? 'active' : ''}`}
            onClick={() => setActiveListeningPart(index)}
          >
            Part {index + 1}
          </button>
        ))}
        
        {currentSection === 'READING' && readingQuizzes.map((quiz, index) => (
          <button
            key={quiz.quizId}
            className={`part-tab ${index === activeReadingQuiz ? 'active' : ''}`}
            onClick={() => setActiveReadingQuiz(index)}
          >
            Passage {index + 1}
          </button>
        ))}

        {currentSection === 'WRITING' && sortedWritingPrompts.map((prompt, index) => (
          <button
            key={prompt.promptId}
            className={`part-tab ${index === activeWritingTask ? 'active' : ''}`}
            onClick={() => setActiveWritingTask(index)}
          >
            Task {index + 1} ({isTask1Type(prompt.essayType) ? 'Report' : 'Essay'})
          </button>
        ))}
      </div>

      {/* ── Main Section Content ── */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

        {/* ── LISTENING SECTION ── */}
        {currentSection === 'LISTENING' && currentListeningPart && (
          <div style={{ flex: 1, overflowY: 'auto', padding: '32px 24px', width: '100%' }}>
            <div style={{ maxWidth: '820px', margin: '0 auto' }}>
              
              {/* Audio player card */}
              <div style={{
                background: 'var(--surface-container-lowest)', border: '1px solid var(--outline-variant)',
                borderRadius: 'var(--radius-xl)', padding: 24, marginBottom: 32
              }}>
                <h3 style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '1.05rem', fontWeight: 600, marginBottom: '12px' }}>
                  <span aria-hidden="true" className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>headphones</span>
                  Part {activeListeningPart + 1}: {currentListeningPart.title}
                </h3>
                <AudioPlayer
                  src={`${audioBaseUrl}${currentListeningPart.audioUrl}`}
                  mode="mock-test"
                  playedStorageKey={activeSession?.sessionId ? `mock_played_${activeSession.sessionId}` : undefined}
                />
              </div>

              {/* Questions List */}
              {usesGroupedLayout(currentListeningPart.questions) ? (
                <QuestionPanel
                  questions={[...(currentListeningPart.questions || [])].sort((a, b) => a.orderIndex - b.orderIndex)}
                  answers={answers}
                  setAnswer={setAnswer}
                  numberOffset={questionOffset(listeningParts, activeListeningPart)}
                  flaggedIds={flagged}
                  onToggleFlag={toggleFlag}
                />
              ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                {(currentListeningPart.questions || [])
                  .sort((a, b) => a.orderIndex - b.orderIndex)
                  .map((q, qIdx) => {
                    let globalNum = qIdx + 1;
                    for (let i = 0; i < activeListeningPart; i++) {
                      globalNum += (listeningParts[i].questions?.length || 0);
                    }
                    return (
                      <div key={q.questionId} data-question-id={q.questionId} style={{
                        background: 'var(--surface-container-lowest)',
                        border: '1px solid var(--outline-variant)',
                        borderRadius: 'var(--radius-xl)', padding: 20
                      }}>
                        <div className="question-number-row" style={{ marginBottom: '8px' }}>
                          <div className="question-number">Question {globalNum}</div>
                          <FlagButton
                            flagged={flagged.has(q.questionId)}
                            onClick={() => toggleFlag(q.questionId)}
                            label={`question ${globalNum}`}
                          />
                        </div>
                        {q.questionType === 'MCQ' ? (
                          <McqQuestion question={q} value={answers[q.questionId] || ''} onChange={v => setAnswer(q.questionId, v)} />
                        ) : (
                          <FillBlankQuestion variant="underline" question={q} value={answers[q.questionId] || ''} onChange={v => setAnswer(q.questionId, v)} />
                        )}
                      </div>
                    );
                  })}
              </div>
              )}
            </div>
          </div>
        )}

        {/* ── READING SECTION ── */}
        {currentSection === 'READING' && currentReadingQuiz && (
          <div className="exam-split">
            <div className="exam-left">
              <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '16px' }}>Passage {activeReadingQuiz + 1}</h2>
              <PassageViewer passage={currentReadingQuiz.passageText} moduleType={currentReadingQuiz.moduleType} />
            </div>
            <div className="exam-right">
              <MockTestQuestionPanel questions={currentReadingQuiz.questions} flaggedIds={flagged} onToggleFlag={toggleFlag} />
            </div>
          </div>
        )}

        {/* ── WRITING SECTION ── */}
        {currentSection === 'WRITING' && currentWritingPrompt && (
          <div className="exam-split">
            {/* Left Prompt Description */}
            <div className="exam-left exam-left-prompt">
              <p style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--primary)', marginBottom: 8 }}>
                Task {activeWritingTask + 1} Prompt
              </p>
              <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.5rem', fontWeight: 700, color: 'var(--on-surface)', marginBottom: 16 }}>
                Writing Task {activeWritingTask + 1}
              </h2>
              <div style={{
                padding: 20, borderRadius: 'var(--radius-lg)',
                background: 'var(--surface-container-lowest)',
                border: '1px solid var(--outline-variant)',
                marginBottom: 16
              }}>
                <p style={{ fontSize: '0.95rem', lineHeight: 1.75, color: 'var(--on-surface)' }}>
                  {currentWritingPrompt.promptText}
                </p>
              </div>

              {currentWritingPrompt.imageUrl && (
                <div style={{ width: '100%', display: 'flex', justifyContent: 'center', margin: '20px 0' }}>
                  <img 
                    src={currentWritingPrompt.imageUrl} 
                    alt="Task Visual" 
                    loading="lazy" 
                    style={{ maxWidth: '100%', height: 'auto', border: '1px solid var(--outline-variant)', borderRadius: 'var(--radius-md)' }} 
                  />
                </div>
              )}

              <p style={{ fontSize: '0.82rem', color: 'var(--on-surface-variant)', marginTop: 12, fontStyle: 'italic' }}>
                {activeWritingTask === 0 
                  ? 'Write at least 150 words. Analyze visual data and report main trends.'
                  : 'Write at least 250 words. Write a coherent discussion essay supporting your claims.'}
              </p>
            </div>

            {/* Right Editor Area */}
            <div className="exam-right" style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', padding: 0 }}>
              <textarea
                style={{
                  flex: 1, resize: 'none', border: 'none', outline: 'none',
                  padding: '32px', fontSize: '1.05rem', lineHeight: '1.7',
                  background: 'var(--surface-container-lowest)',
                  color: 'var(--on-surface)',
                  fontFamily: 'var(--font-body)'
                }}
                value={activeWritingTask === 0 ? essayTask1Text : essayTask2Text}
                onChange={e => setAnswer(activeWritingTask === 0 ? 'w_task1' : 'w_task2', e.target.value)}
                placeholder={activeWritingTask === 0 ? 'Start drafting Task 1 report here...' : 'Start drafting Task 2 essay here...'}
              />
              
              {/* Word counter info bar */}
              <div style={{
                display: 'flex', alignItems: 'center', gap: 16,
                padding: '12px 32px', background: 'var(--surface-container-low)',
                borderTop: '1px solid var(--outline-variant)'
              }}>
                <div style={{ flex: 1, height: 6, background: 'var(--surface-variant)', borderRadius: 'var(--radius-full)', overflow: 'hidden' }}>
                  <div style={{
                    height: '100%', borderRadius: 'var(--radius-full)',
                    background: (activeWritingTask === 0 ? wordCountTask1 >= 150 : wordCountTask2 >= 250) ? 'var(--secondary)' : 'var(--primary)',
                    width: `${Math.min(100, Math.round(((activeWritingTask === 0 ? wordCountTask1 : wordCountTask2) / (activeWritingTask === 0 ? 150 : 250)) * 100))}%`,
                    transition: 'width 0.3s ease'
                  }} />
                </div>
                <div style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--on-surface-variant)' }}>
                  <strong>{activeWritingTask === 0 ? wordCountTask1 : wordCountTask2}</strong> / {activeWritingTask === 0 ? 150 : 250} words
                  {((activeWritingTask === 0 && wordCountTask1 < 150) || (activeWritingTask === 1 && wordCountTask2 < 250)) && (
                    <span style={{ color: 'var(--error)' }}>
                      {' '}· need {activeWritingTask === 0 ? 150 - wordCountTask1 : 250 - wordCountTask2} more
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {(currentSection === 'LISTENING' || currentSection === 'READING') && (
        <QuestionNavigator
          groups={navGroups}
          activeIndex={currentSection === 'LISTENING' ? activeListeningPart : activeReadingQuiz}
          onJump={jumpToQuestion}
        />
      )}

      {/* ── Sticky Footer ── */}
      <footer className="exam-action-bar">
        <div className="exam-action-bar-left">
          <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 18, color: 'var(--secondary)' }}>check_circle</span>
          {currentSection === 'LISTENING' && (
            <span>Answered <strong>{answeredListeningCount}</strong> / {totalListeningQuestions} questions</span>
          )}
          {currentSection === 'READING' && (
            <span>Answered <strong>{answeredReadingCount}</strong> / {totalReadingQuestions} questions</span>
          )}
          {currentSection === 'WRITING' && (
            <span>Word Counts: Task 1 (<strong>{wordCountTask1}</strong> words) · Task 2 (<strong>{wordCountTask2}</strong> words)</span>
          )}
        </div>
        <div className="exam-action-bar-right">
          <button className="btn btn-outline" onClick={handleExitLobby}>
            Exit Exam Lobby
          </button>
          
          {currentSection === 'WRITING' ? (
            <button 
              className="btn btn-primary btn-submit-exam" 
              onClick={handleSubmitTest}
              disabled={submitting}
            >
              {submitting ? 'Submitting...' : 'Complete & Submit Exam'}
            </button>
          ) : (
            <button 
              className="btn btn-primary btn-submit-exam" 
              onClick={handleNextSection}
            >
              Finish section & continue
            </button>
          )}
        </div>
      </footer>
    </div>
  );
}
