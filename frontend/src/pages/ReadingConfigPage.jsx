import { useState, useEffect } from 'react';
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
  { value: 'PASSAGE_1', label: 'Passage 1 (Easy)', time: '10 mins', desc: 'Simple vocabulary, basic reading comprehension' },
  { value: 'PASSAGE_2', label: 'Passage 2 (Medium)', time: '15 mins', desc: 'Academic vocabulary, inference skills' },
  { value: 'PASSAGE_3', label: 'Passage 3 (Hard)', time: '20 mins', desc: 'Advanced vocabulary, critical analysis' },
];

const topicLabels = {
  ENVIRONMENT: 'Environment',
  TECHNOLOGY: 'Technology',
  HISTORY: 'History',
  HEALTH: 'Health',
  EDUCATION: 'Education'
};

const difficultyLabels = {
  PASSAGE_1: 'Passage 1 (Easy)',
  PASSAGE_2: 'Passage 2 (Medium)',
  PASSAGE_3: 'Passage 3 (Hard)'
};

export default function ReadingConfigPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('ai'); // 'ai' or 'admin'
  
  // AI Generate states
  const [topic, setTopic] = useState('ENVIRONMENT');
  const [difficulty, setDifficulty] = useState('PASSAGE_1');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Admin template states
  const [adminQuizzes, setAdminQuizzes] = useState([]);
  const [adminLoading, setAdminLoading] = useState(false);
  const [filterTopic, setFilterTopic] = useState('');
  const [filterDifficulty, setFilterDifficulty] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    if (activeTab === 'admin') {
      loadAdminQuizzes();
    }
  }, [activeTab, filterTopic, filterDifficulty, page]);

  const loadAdminQuizzes = async () => {
    setAdminLoading(true);
    setError('');
    try {
      const res = await readingApi.getTemplates(filterTopic, filterDifficulty, page, 6);
      setAdminQuizzes(res.data.data.content || []);
      setTotalPages(res.data.data.totalPages || 0);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Unable to load test templates from system.');
    } finally {
      setAdminLoading(false);
    }
  };

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

  const handleStartTemplate = async (templateId) => {
    setLoading(true);
    setError('');
    try {
      const res = await readingApi.startTemplateQuiz(templateId);
      const quizId = res.data.data.quizId;
      navigate(`/reading/exam/${quizId}`);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to start the quiz. Please try again.');
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
            Home
          </button>
          <h1>Reading Practice</h1>
          <p className="subtitle">Practice your reading skills with AI or template tests</p>
        </div>

        {/* Tab Container */}
        <div className="tab-container" style={{
          display: 'flex',
          gap: '1rem',
          marginBottom: '2rem',
          borderBottom: '1px solid var(--border-color)',
          paddingBottom: '0.5rem'
        }}>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'ai' ? 'active' : ''}`}
            onClick={() => { setActiveTab('ai'); setError(''); }}
            style={{
              padding: '0.75rem 1.5rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'ai' ? '3px solid var(--primary-color)' : '3px solid transparent',
              color: activeTab === 'ai' ? 'var(--primary-color)' : 'var(--text-secondary)',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s',
              fontSize: '1rem'
            }}
          >
            AI-Generated Tests
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === 'admin' ? 'active' : ''}`}
            onClick={() => { setActiveTab('admin'); setError(''); setPage(0); }}
            style={{
              padding: '0.75rem 1.5rem',
              background: 'none',
              border: 'none',
              borderBottom: activeTab === 'admin' ? '3px solid var(--primary-color)' : '3px solid transparent',
              color: activeTab === 'admin' ? 'var(--primary-color)' : 'var(--text-secondary)',
              fontWeight: '600',
              cursor: 'pointer',
              transition: 'all 0.2s',
              fontSize: '1rem'
            }}
          >
            Curated Tests
          </button>
        </div>

        {error && <div className="error-msg" style={{ marginBottom: '1.5rem' }}>{error}</div>}

        {activeTab === 'ai' ? (
          <div className="config-form">
            {/* Topic Selection */}
            <div className="config-section">
              <h2>Select Topic</h2>
              <div className="topic-grid">
                {TOPICS.map((t) => (
                  <button
                    key={t.value}
                    type="button"
                    className={`topic-card ${topic === t.value ? 'active' : ''}`}
                    onClick={() => setTopic(t.value)}
                    id={`topic-${t.value.toLowerCase()}`}
                  >
                    <span className="topic-label">{t.label}</span>
                  </button>
                ))}
              </div>
            </div>

            {/* Difficulty Selection */}
            <div className="config-section">
              <h2>Select Difficulty</h2>
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
        ) : (
          <div className="admin-templates-section">
            {/* Filters */}
            <div className="filters-row" style={{
              display: 'flex',
              gap: '1rem',
              marginBottom: '2rem',
              flexWrap: 'wrap'
            }}>
              <div className="filter-group" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', minWidth: '200px' }}>
                <label style={{ fontSize: '0.875rem', fontWeight: '500', color: 'var(--text-secondary)' }}>Topic</label>
                <select
                  value={filterTopic}
                  onChange={(e) => { setFilterTopic(e.target.value); setPage(0); }}
                  className="form-input"
                  style={{ width: '100%' }}
                >
                  <option value="">All Topics</option>
                  {TOPICS.map(t => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>

              <div className="filter-group" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', minWidth: '200px' }}>
                <label style={{ fontSize: '0.875rem', fontWeight: '500', color: 'var(--text-secondary)' }}>Difficulty</label>
                <select
                  value={filterDifficulty}
                  onChange={(e) => { setFilterDifficulty(e.target.value); setPage(0); }}
                  className="form-input"
                  style={{ width: '100%' }}
                >
                  <option value="">All Difficulties</option>
                  {DIFFICULTIES.map(d => (
                    <option key={d.value} value={d.value}>{d.label}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Templates Grid */}
            {adminLoading ? (
              <div style={{ display: 'flex', justifyContent: 'center', padding: '3rem' }}>
                <span className="spinner"></span> &nbsp; Loading tests...
              </div>
            ) : adminQuizzes.length === 0 ? (
              <div style={{
                textAlign: 'center',
                padding: '4rem 2rem',
                border: '2px dashed var(--border-color)',
                borderRadius: '8px',
                color: 'var(--text-secondary)'
              }}>
                <p style={{ fontSize: '1.1rem', marginBottom: '0.5rem' }}>No tests available in this category</p>
                <p style={{ fontSize: '0.875rem' }}>Teachers or Admins will update more tests in the future.</p>
              </div>
            ) : (
              <div>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
                  gap: '1.5rem',
                  marginBottom: '2rem'
                }}>
                  {adminQuizzes.map((quiz) => (
                    <div
                      key={quiz.quizId}
                      className="quiz-template-card"
                      style={{
                        background: 'var(--bg-card)',
                        border: '1px solid var(--border-color)',
                        borderRadius: '12px',
                        padding: '1.5rem',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'space-between',
                        boxShadow: 'var(--shadow-sm)',
                        transition: 'transform 0.2s, box-shadow 0.2s',
                        position: 'relative'
                      }}
                    >
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                          <span style={{
                            background: 'var(--primary-light)',
                            color: 'var(--primary-color)',
                            fontSize: '0.75rem',
                            fontWeight: '600',
                            padding: '0.25rem 0.75rem',
                            borderRadius: '50px'
                          }}>
                            {topicLabels[quiz.topic] || quiz.topic}
                          </span>
                          <span style={{
                            background: 'var(--bg-body)',
                            color: 'var(--text-primary)',
                            fontSize: '0.75rem',
                            fontWeight: '500',
                            padding: '0.25rem 0.75rem',
                            borderRadius: '50px',
                            border: '1px solid var(--border-color)'
                          }}>
                            {difficultyLabels[quiz.difficulty] || quiz.difficulty}
                          </span>
                        </div>
                        <h3 style={{
                          fontSize: '1.15rem',
                          fontWeight: '600',
                          marginBottom: '0.75rem',
                          color: 'var(--text-primary)',
                          lineHeight: '1.4'
                        }}>
                          Passage: {topicLabels[quiz.topic] || quiz.topic}
                        </h3>
                        <p style={{
                          fontSize: '0.875rem',
                          color: 'var(--text-secondary)',
                          marginBottom: '1rem',
                          display: '-webkit-box',
                          WebkitLineClamp: 3,
                          WebkitBoxOrient: 'vertical',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis'
                        }}>
                          {quiz.passageText}
                        </p>
                        <div style={{
                          display: 'flex',
                          gap: '1rem',
                          fontSize: '0.8rem',
                          color: 'var(--text-secondary)',
                          marginBottom: '1.5rem',
                          borderTop: '1px solid var(--border-color)',
                          paddingTop: '0.75rem'
                        }}>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                            {Math.round(quiz.timeLimitSeconds / 60)} mins
                          </span>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                            {quiz.totalQuestions} questions
                          </span>
                        </div>
                      </div>
                      <button
                        className="btn btn-primary"
                        onClick={() => handleStartTemplate(quiz.quizId)}
                        disabled={loading}
                        style={{ width: '100%', marginTop: 'auto' }}
                      >
                        Start Test
                      </button>
                    </div>
                  ))}
                </div>

                {/* Pagination */}
                {totalPages > 1 && (
                  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '1rem' }}>
                    <button
                      className="btn btn-outline"
                      disabled={page === 0}
                      onClick={() => setPage(p => p - 1)}
                    >
                      Previous
                    </button>
                    <span style={{ fontSize: '0.9rem', color: 'var(--text-secondary)' }}>
                      Page {page + 1} / {totalPages}
                    </span>
                    <button
                      className="btn btn-outline"
                      disabled={page >= totalPages - 1}
                      onClick={() => setPage(p => p + 1)}
                    >
                      Next
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

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
