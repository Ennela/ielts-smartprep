import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import readingApi from '../api/readingApi';

const TOPICS = [
  { value: 'ENVIRONMENT', label: 'Environment' },
  { value: 'TECHNOLOGY', label: 'Technology' },
  { value: 'HISTORY', label: 'History' },
  { value: 'HEALTH', label: 'Health' },
  { value: 'EDUCATION', label: 'Education' },
];

const DIFFICULTIES = [
  { value: 'PASSAGE_1', label: 'Passage 1 (Easy)', time: '10 min', desc: 'Simple vocabulary, basic comprehension' },
  { value: 'PASSAGE_2', label: 'Passage 2 (Medium)', time: '15 min', desc: 'Academic vocabulary, inference skills' },
  { value: 'PASSAGE_3', label: 'Passage 3 (Hard)', time: '20 min', desc: 'Advanced vocabulary, critical analysis' },
];

export default function ReadingConfigPage() {
  const navigate = useNavigate();
  const [topic, setTopic] = useState('ENVIRONMENT');
  const [difficulty, setDifficulty] = useState('PASSAGE_1');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleGenerate = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await readingApi.generateQuiz(topic, difficulty);
      const quizId = res.data.data.quizId;
      navigate(`/reading/exam/${quizId}`);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to generate quiz. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="reading-config-page">
      <div className="reading-config-content">
        <div className="reading-config-header">
          <button className="btn-back" onClick={() => navigate('/dashboard')} id="back-to-dashboard">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Dashboard
          </button>
          <h1>Reading Practice</h1>
          <p className="subtitle">Configure your AI-generated reading test</p>
        </div>

        {error && <div className="error-msg">{error}</div>}

        <div className="config-form">
          {/* Topic Selection */}
          <div className="config-section">
            <h2>Choose Topic</h2>
            <div className="topic-grid">
              {TOPICS.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  className={`topic-card ${topic === t.value ? 'active' : ''}`}
                  onClick={() => setTopic(t.value)}
                  id={`topic-${t.value.toLowerCase()}`}
                >
                  <span className="topic-icon">{getTopicIcon(t.value)}</span>
                  <span className="topic-label">{t.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Difficulty Selection */}
          <div className="config-section">
            <h2>Choose Difficulty</h2>
            <div className="difficulty-grid">
              {DIFFICULTIES.map((d) => (
                <button
                  key={d.value}
                  type="button"
                  className={`difficulty-card ${difficulty === d.value ? 'active' : ''}`}
                  onClick={() => setDifficulty(d.value)}
                  id={`diff-${d.value.toLowerCase()}`}
                >
                  <div className="diff-top">
                    <span className="diff-label">{d.label}</span>
                    <span className="diff-time">{d.time}</span>
                  </div>
                  <p className="diff-desc">{d.desc}</p>
                </button>
              ))}
            </div>
          </div>

          {/* Generate Button */}
          <button
            className="btn btn-primary btn-generate"
            onClick={handleGenerate}
            disabled={loading}
            id="generate-quiz-btn"
          >
            {loading ? (
              <>
                <span className="spinner"></span>
                Generating with AI...
              </>
            ) : (
              'Generate Reading Test'
            )}
          </button>
        </div>

        {/* History Link */}
        <div className="config-footer">
          <button className="btn btn-outline" onClick={() => navigate('/reading/history')} id="view-history-btn">
            View History
          </button>
        </div>
      </div>
    </div>
  );
}

function getTopicIcon(topic) {
  switch (topic) {
    case 'ENVIRONMENT': return '\u{1F30D}';
    case 'TECHNOLOGY': return '\u{1F4BB}';
    case 'HISTORY': return '\u{1F3DB}';
    case 'HEALTH': return '\u{1FA7A}';
    case 'EDUCATION': return '\u{1F393}';
    default: return '\u{1F4D6}';
  }
}
