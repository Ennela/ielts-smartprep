import { useEffect, useState, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMockTest } from '../context/MockTestContext';
import AudioPlayer from '../components/listening/AudioPlayer';
import PassageViewer from '../components/reading/PassageViewer';
import MockTestQuestionPanel from '../components/mocktest/MockTestQuestionPanel';

export default function MockTestSessionPage() {
  const navigate = useNavigate();
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
    loadActiveSession,
    setAnswer,
    advanceSection,
    submitExam,
    syncNow
  } = useMockTest();

  // Selected sub-tabs within sections
  const [activeListeningPart, setActiveListeningPart] = useState(0);
  const [activeReadingQuiz, setActiveReadingQuiz] = useState(0);
  const [activeWritingTask, setActiveWritingTask] = useState(0); // 0 for Task 1, 1 for Task 2
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    // Ensure active session is loaded on mount
    if (!activeSession) {
      loadActiveSession().then((session) => {
        if (!session) {
          navigate('/mock-tests');
        }
      });
    }
  }, [activeSession]);

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
  const audioBaseUrl = import.meta.env.VITE_API_URL?.replace('/api/v1', '') || 'http://localhost:8080';

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
      const isA1 = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'].includes(a.essayType);
      const isB1 = ['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'].includes(b.essayType);
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

  // Handle section switch / submission
  const handleNextSection = async () => {
    const currentName = currentSection === 'LISTENING' ? 'Listening' : 'Reading';
    const nextName = currentSection === 'LISTENING' ? 'Reading' : 'Writing';
    
    if (window.confirm(`Are you sure you want to complete the ${currentName} section and move to the ${nextName} section? You will not be able to return.`)) {
      await advanceSection();
    }
  };

  const handleSubmitTest = async () => {
    if (wordCountTask1 < 150 || wordCountTask2 < 250) {
      const confirmStr = `Your essays do not meet the minimum length (Task 1: ${wordCountTask1}/150 words, Task 2: ${wordCountTask2}/250 words).\nAre you sure you want to submit the exam anyway?`;
      if (!window.confirm(confirmStr)) return;
    } else {
      if (!window.confirm('Are you sure you want to submit your mock test for AI grading? This will close the test.')) return;
    }

    try {
      setSubmitting(true);
      const submission = await submitExam();
      if (submission) {
        navigate(`/mock-tests/result/${submission.submissionId}`);
      }
    } catch (err) {
      alert('Failed to submit exam. Please verify connection and retry.');
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
          <span className="exam-logo" onClick={() => navigate('/mock-tests')} style={{ cursor: 'pointer' }}>IELTS Full Mock Test</span>
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

        <div className="exam-topbar-center">
          <h1 style={{ fontSize: '1.1rem', fontWeight: 700 }}>{activeSession.title}</h1>
          <p style={{ fontSize: '0.75rem', color: 'var(--on-surface-variant)' }}>
            {currentSection === 'LISTENING' && `Section 1 of 3`}
            {currentSection === 'READING' && `Section 2 of 3`}
            {currentSection === 'WRITING' && `Section 3 of 3`}
          </p>
        </div>

        <div className="exam-topbar-right">
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <div className={`exam-timer-pill ${timeRemaining < 300 ? 'warning' : ''}`} title="Section Remaining Time">
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>hourglass_empty</span>
              Section: {formatTime(timeRemaining)}
            </div>
            <div className="exam-timer-pill" style={{ background: 'var(--surface-container-highest)', color: 'var(--primary)' }} title="Overall Remaining Time">
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>schedule</span>
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
            Part {part.partNumber}
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
            Task {index + 1} ({['LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM'].includes(prompt.essayType) ? 'Report' : 'Essay'})
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
                  <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>headphones</span>
                  Part {currentListeningPart.partNumber}: {currentListeningPart.title}
                </h3>
                <AudioPlayer src={`${audioBaseUrl}${currentListeningPart.audioUrl}`} mode="mock-test" />
              </div>

              {/* Questions List */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                {(currentListeningPart.questions || [])
                  .sort((a, b) => a.orderIndex - b.orderIndex)
                  .map((q, qIdx) => {
                    let globalNum = qIdx + 1;
                    for (let i = 0; i < activeListeningPart; i++) {
                      globalNum += (listeningParts[i].questions?.length || 0);
                    }
                    return (
                      <div key={q.questionId} style={{
                        background: 'var(--surface-container-lowest)',
                        border: '1px solid var(--outline-variant)',
                        borderRadius: 'var(--radius-xl)', padding: 20
                      }}>
                        <div className="question-number" style={{ marginBottom: '8px' }}>Question {globalNum}</div>
                        {q.questionType === 'MCQ' ? (
                          <McqQuestion question={q} value={answers[q.questionId] || ''} onChange={v => setAnswer(q.questionId, v)} />
                        ) : (
                          <FillBlankQuestion question={q} value={answers[q.questionId] || ''} onChange={v => setAnswer(q.questionId, v)} />
                        )}
                      </div>
                    );
                  })}
              </div>
            </div>
          </div>
        )}

        {/* ── READING SECTION ── */}
        {currentSection === 'READING' && currentReadingQuiz && (
          <div className="exam-split" style={{ display: 'flex', flex: 1, width: '100%', overflow: 'hidden' }}>
            <div className="exam-left" style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
              <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '16px' }}>Passage {activeReadingQuiz + 1}</h2>
              <PassageViewer passage={currentReadingQuiz.passageText} />
            </div>
            <div className="exam-right" style={{ flex: 1, overflowY: 'auto', padding: '32px' }}>
              <MockTestQuestionPanel questions={currentReadingQuiz.questions} />
            </div>
          </div>
        )}

        {/* ── WRITING SECTION ── */}
        {currentSection === 'WRITING' && currentWritingPrompt && (
          <div className="exam-split" style={{ display: 'flex', flex: 1, width: '100%', overflow: 'hidden' }}>
            {/* Left Prompt Description */}
            <div className="exam-left" style={{ width: '40%', minWidth: '320px', overflowY: 'auto', padding: '32px' }}>
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

      {/* ── Sticky Footer ── */}
      <footer className="exam-action-bar">
        <div className="exam-action-bar-left">
          <span className="material-symbols-outlined" style={{ fontSize: 18, color: 'var(--secondary)' }}>check_circle</span>
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
          <button className="btn btn-outline" onClick={() => navigate('/mock-tests')}>
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

// ── Sub-component MCQ ──
function McqQuestion({ question, value, onChange }) {
  const lines = question.questionText.split('\n');
  const stem = lines[0];
  const options = lines.slice(1).filter(l => l.trim());
  return (
    <div>
      <p className="question-text">{stem}</p>
      <div className="mcq-options">
        {options.map((opt, idx) => {
          const letter = opt.trim().charAt(0);
          return (
            <label key={idx} className={`mcq-option ${value === letter ? 'selected' : ''}`}>
              <input 
                type="radio" 
                name={`q-${question.questionId}`} 
                value={letter}
                checked={value === letter} 
                onChange={() => onChange(letter)} 
              />
              <span className="mcq-letter">{letter}</span>
              <span className="mcq-label">{opt.trim().substring(2).trim()}</span>
            </label>
          );
        })}
      </div>
    </div>
  );
}

// ── Sub-component Fill Blank ──
function FillBlankQuestion({ question, value, onChange }) {
  const parts = question.questionText.split('___');
  return (
    <div>
      <p className="question-text" style={{ display: 'inline-flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px', margin: 0 }}>
        {parts.map((part, idx) => (
          <span key={idx} style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            {part}
            {idx < parts.length - 1 && (
              <input 
                type="text" 
                className="fill-blank-input" 
                value={value}
                onChange={e => onChange(e.target.value)} 
                placeholder="your answer..." 
                style={{
                  border: 'none', borderBottom: '2px solid var(--outline)',
                  background: 'transparent', outline: 'none', padding: '2px 8px',
                  fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--primary)',
                  textAlign: 'center', width: '120px'
                }}
              />
            )}
          </span>
        ))}
      </p>
    </div>
  );
}
