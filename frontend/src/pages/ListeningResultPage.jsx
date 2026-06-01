import { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import listeningApi from '../api/listeningApi';

export default function ListeningResultPage() {
  const { testId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [result, setResult] = useState(location.state || null);
  const [activeTab, setActiveTab] = useState('answers');
  const [vocabData, setVocabData] = useState(null);
  const [vocabLoading, setVocabLoading] = useState(false);
  const [aiAnalysis, setAiAnalysis] = useState({});
  const [aiLoading, setAiLoading] = useState({});

  useEffect(() => {
    if (!result && testId) {
      // If no state passed, could fetch from API in the future
    }
  }, [testId, result]);

  if (!result) return (
    <div className="listening-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  const scoreColor = result.score >= 7.0 ? 'var(--color-success)' :
                     result.score >= 5.5 ? 'var(--color-warning)' : 'var(--color-error)';

  const circumference = 2 * Math.PI * 54;
  const scorePercent = (result.score / 9) * 100;
  const dashOffset = circumference - (scorePercent / 100) * circumference;

  const handleAnalyze = async (questionId) => {
    if (aiAnalysis[questionId]) return;
    setAiLoading(prev => ({ ...prev, [questionId]: true }));
    try {
      const res = await listeningApi.analyzeQuestion(questionId);
      setAiAnalysis(prev => ({ ...prev, [questionId]: res.data?.data }));
    } catch (err) {
      console.error(err);
    } finally {
      setAiLoading(prev => ({ ...prev, [questionId]: false }));
    }
  };

  const handleVocab = async (partId) => {
    if (vocabData) return;
    setVocabLoading(true);
    try {
      const res = await listeningApi.extractVocabulary(partId);
      setVocabData(res.data?.data);
    } catch (err) {
      console.error(err);
    } finally {
      setVocabLoading(false);
    }
  };

  return (
    <div className="listening-page">
      <div className="listening-result-content">
        <h1>Listening Test Results</h1>

        {/* Score Ring */}
        <div className="result-score-section">
          <div className="score-ring-container">
            <svg className="score-ring" width="140" height="140" viewBox="0 0 120 120">
              <circle cx="60" cy="60" r="54" fill="none" stroke="var(--color-border)" strokeWidth="8" />
              <circle
                cx="60" cy="60" r="54" fill="none"
                stroke={scoreColor} strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={dashOffset}
                transform="rotate(-90 60 60)"
                className="score-ring-progress"
              />
              <text x="60" y="55" textAnchor="middle" fill={scoreColor} fontSize="28" fontWeight="700">
                {result.score?.toFixed ? result.score.toFixed(1) : result.score}
              </text>
              <text x="60" y="75" textAnchor="middle" fill="var(--color-text-muted)" fontSize="11">
                Band Score
              </text>
            </svg>
          </div>
          <div className="score-stats">
            <div className="stat-item">
              <span className="stat-label">Mode</span>
              <span className={`badge ${result.testMode === 'MOCK_TEST' ? 'badge-mock' : 'badge-practice'}`}>
                {result.testMode === 'MOCK_TEST' ? 'Mock Test' : 'Practice'}
              </span>
            </div>
            <div className="stat-item">
              <span className="stat-label">Correct</span>
              <span className="stat-value">{result.correctAnswers}/{result.totalQuestions}</span>
            </div>
            <div className="stat-item">
              <span className="stat-label">Accuracy</span>
              <span className="stat-value">
                {result.totalQuestions ? Math.round((result.correctAnswers / result.totalQuestions) * 100) : 0}%
              </span>
            </div>
          </div>
        </div>

        {/* Tabs */}
        <div className="result-tabs">
          <button className={`tab-btn ${activeTab === 'answers' ? 'active' : ''}`}
            onClick={() => setActiveTab('answers')}>Review Answers</button>
          <button className={`tab-btn ${activeTab === 'transcript' ? 'active' : ''}`}
            onClick={() => setActiveTab('transcript')}>Transcript</button>
          <button className={`tab-btn ${activeTab === 'vocabulary' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('vocabulary');
              if (result.parts?.[0]) handleVocab(result.parts[0].partId);
            }}>AI Vocabulary</button>
        </div>

        {/* Answer Review Tab */}
        {activeTab === 'answers' && (
          <div className="result-answers">
            {result.parts?.map(part => (
              <div key={part.partId} className="result-part-section">
                <h3>Part {part.partNumber}: {part.title}</h3>
                <div className="answer-grid">
                  {part.questions?.sort((a,b) => a.orderIndex - b.orderIndex).map(q => (
                    <div key={q.questionId} className={`answer-card ${q.isCorrect ? 'correct' : 'wrong'}`}>
                      <div className="answer-card-header">
                        <span className={`answer-status ${q.isCorrect ? 'status-correct' : 'status-wrong'}`}>
                          {q.isCorrect ? '✓' : '✗'}
                        </span>
                        <span className="answer-type">{q.questionType}</span>
                      </div>
                      <p className="answer-question-text">
                        {q.questionType === 'MCQ' ? q.questionText.split('\n')[0] : q.questionText}
                      </p>
                      <div className="answer-comparison">
                        <div className="answer-row">
                          <span className="answer-label">Your answer:</span>
                          <span className={`answer-value ${q.isCorrect ? '' : 'answer-wrong'}`}>
                            {q.userAnswer || '(not answered)'}
                          </span>
                        </div>
                        {!q.isCorrect && (
                          <div className="answer-row">
                            <span className="answer-label">Correct answer:</span>
                            <span className="answer-value answer-correct">{q.correctAnswer}</span>
                          </div>
                        )}
                      </div>
                      {!q.isCorrect && (
                        <button
                          className="btn btn-sm btn-outline ai-analyze-btn"
                          onClick={() => handleAnalyze(q.questionId)}
                          disabled={aiLoading[q.questionId]}
                        >
                          {aiLoading[q.questionId] ? 'Analyzing...' :
                           aiAnalysis[q.questionId] ? 'View Analysis' : 'AI Analysis'}
                        </button>
                      )}
                      {aiAnalysis[q.questionId] && (
                        <div className="ai-analysis-panel">
                          <div className="ai-analysis-item">
                            <strong>Answer Location:</strong>
                            <p>{aiAnalysis[q.questionId].correctAnswerLocation}</p>
                          </div>
                          <div className="ai-analysis-item">
                            <strong>Trap Explanation:</strong>
                            <p>{aiAnalysis[q.questionId].trapExplanation}</p>
                          </div>
                          <div className="ai-analysis-item">
                            <strong>Tip:</strong>
                            <p>{aiAnalysis[q.questionId].tip}</p>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Transcript Tab */}
        {activeTab === 'transcript' && (
          <div className="result-transcripts">
            {result.parts?.map(part => (
              <div key={part.partId} className="transcript-section">
                <h3>Part {part.partNumber}: {part.title}</h3>
                <div className="transcript-text">
                  {part.transcriptText?.split('\n').map((line, i) => (
                    <p key={i}>{line}</p>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Vocabulary Tab */}
        {activeTab === 'vocabulary' && (
          <div className="result-vocabulary">
            {vocabLoading ? (
              <div className="loading-spinner"><div className="spinner" /></div>
            ) : vocabData?.vocabularies ? (
              <div className="vocab-grid">
                {vocabData.vocabularies.map((v, i) => (
                  <div key={i} className="vocab-card">
                    <div className="vocab-header">
                      <span className="vocab-word">{v.word}</span>
                      <span className={`badge badge-level-${v.level?.toLowerCase()}`}>{v.level}</span>
                    </div>
                    <span className="vocab-pos">{v.partOfSpeech}</span>
                    <p className="vocab-meaning">{v.vietnameseMeaning}</p>
                    <p className="vocab-context">"{v.contextExample}"</p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-muted">No vocabulary found. Please try again.</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
