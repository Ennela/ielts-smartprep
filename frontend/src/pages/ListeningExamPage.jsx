import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import listeningApi from '../api/listeningApi';
import AudioPlayer from '../components/listening/AudioPlayer';

export default function ListeningExamPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const mode = searchParams.get('mode') || 'practice';
  const partIds = useMemo(() => (searchParams.get('parts') || '').split(',').map(Number).filter(Boolean), [searchParams]);

  const [parts, setParts] = useState([]);
  const [answers, setAnswers] = useState({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [currentPartIndex, setCurrentPartIndex] = useState(0);

  useEffect(() => {
    const loadParts = async () => {
      try {
        const loaded = [];
        for (const id of partIds) {
          const res = await listeningApi.getPartById(id);
          if (res.data?.data) loaded.push(res.data.data);
        }
        setParts(loaded);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    if (partIds.length) loadParts();
  }, [partIds]);

  const handleAnswer = (questionId, value) => {
    setAnswers(prev => ({ ...prev, [questionId]: value }));
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const res = await listeningApi.submitTest(
        mode === 'mock-test' ? 'MOCK_TEST' : 'PRACTICE',
        partIds,
        answers
      );
      const testId = res.data?.data?.testId;
      navigate(`/listening/result/${testId}`, { state: res.data?.data });
    } catch (err) {
      console.error(err);
      alert('Failed to submit test');
    } finally {
      setSubmitting(false);
    }
  };

  const currentPart = parts[currentPartIndex];
  const totalQuestions = parts.reduce((sum, p) => sum + (p.questions?.length || 0), 0);
  const answeredCount = Object.keys(answers).filter(k => answers[k]?.trim()).length;

  if (loading) return (
    <div className="listening-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  const audioBaseUrl = import.meta.env.VITE_API_URL?.replace('/api/v1', '') || 'http://localhost:8080';

  return (
    <div className="listening-page">
      <nav className="navbar">
        <div className="navbar-brand" style={{cursor:'pointer'}} onClick={() => navigate('/listening')}>SmartPrep</div>
        <div className="navbar-user">
          <span className={`badge ${mode === 'mock-test' ? 'badge-mock' : 'badge-practice'}`}>
            {mode === 'mock-test' ? 'MOCK TEST' : 'PRACTICE'}
          </span>
          <span className="answer-progress">{answeredCount}/{totalQuestions} answered</span>
        </div>
      </nav>

      <main className="listening-exam-layout">
        {/* Part tabs */}
        {parts.length > 1 && (
          <div className="listening-part-tabs">
            {parts.map((part, idx) => (
              <button
                key={part.partId}
                className={`part-tab ${idx === currentPartIndex ? 'active' : ''}`}
                onClick={() => setCurrentPartIndex(idx)}
              >
                Part {part.partNumber}: {part.title}
              </button>
            ))}
          </div>
        )}

        {currentPart && (
          <div className="listening-exam-content">
            {/* Audio player */}
            <div className="listening-audio-section">
              <h2>Part {currentPart.partNumber}: {currentPart.title}</h2>
              <p className="listening-topic-label">{currentPart.topic}</p>
              <AudioPlayer
                src={`${audioBaseUrl}${currentPart.audioUrl}`}
                mode={mode}
              />
            </div>

            {/* Questions */}
            <div className="listening-questions">
              {(currentPart.questions || [])
                .sort((a, b) => a.orderIndex - b.orderIndex)
                .map((q, qIdx) => {
                  // Calculate global question number
                  let globalNum = qIdx + 1;
                  for (let i = 0; i < currentPartIndex; i++) {
                    globalNum += (parts[i].questions?.length || 0);
                  }

                  return (
                    <div key={q.questionId} className="question-card">
                      <div className="question-number">Q{globalNum}</div>
                      {q.questionType === 'MCQ' ? (
                        <McqQuestion
                          question={q}
                          value={answers[q.questionId] || ''}
                          onChange={(val) => handleAnswer(q.questionId, val)}
                        />
                      ) : (
                        <FillBlankQuestion
                          question={q}
                          value={answers[q.questionId] || ''}
                          onChange={(val) => handleAnswer(q.questionId, val)}
                        />
                      )}
                    </div>
                  );
                })}
            </div>
          </div>
        )}

        <div className="listening-submit-bar">
          <button
            className="btn btn-primary btn-lg"
            onClick={handleSubmit}
            disabled={submitting || answeredCount === 0}
            id="submit-listening-btn"
          >
            {submitting ? 'Grading...' : `Submit Test (${answeredCount}/${totalQuestions})`}
          </button>
        </div>
      </main>
    </div>
  );
}

// ===== MCQ Question Component =====
function McqQuestion({ question, value, onChange }) {
  // Parse MCQ options from questionText
  const lines = question.questionText.split('\n');
  const stem = lines[0];
  const options = lines.slice(1).filter(l => l.trim());

  return (
    <div className="mcq-question">
      <p className="question-text">{stem}</p>
      <div className="mcq-options">
        {options.map((opt, i) => {
          const letter = opt.trim().charAt(0);
          return (
            <label key={i} className={`mcq-option ${value === letter ? 'selected' : ''}`}>
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

// ===== Fill-in-the-Blank Question Component =====
function FillBlankQuestion({ question, value, onChange }) {
  const parts = question.questionText.split('___');

  return (
    <div className="fill-blank-question">
      <p className="question-text">
        {parts.map((part, i) => (
          <span key={i}>
            {part}
            {i < parts.length - 1 && (
              <input
                type="text"
                className="fill-blank-input"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                placeholder="your answer"
              />
            )}
          </span>
        ))}
      </p>
    </div>
  );
}
