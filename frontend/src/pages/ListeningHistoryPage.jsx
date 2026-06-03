import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import listeningApi from '../api/listeningApi';

export default function ListeningHistoryPage() {
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    listeningApi.getHistory()
      .then(res => setHistory(res.data?.data || []))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return (
    <div className="listening-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  return (
    <div className="listening-page">
      <div className="listening-content">
        <h1>Listening History</h1>
        <p className="subtitle">Your previous Listening tests and practice sessions</p>

        {history.length === 0 ? (
          <div className="empty-state">
            <p>No Listening tests taken yet.</p>
            <button className="btn btn-primary" onClick={() => navigate('/listening')}>
              Start Practice
            </button>
          </div>
        ) : (
          <div className="history-list">
            {history.map(item => {
              const scoreColor = item.score >= 7.0 ? 'var(--clr-success)' :
                                 item.score >= 5.5 ? 'var(--clr-warning)' : 'var(--clr-error)';
              return (
                <div key={item.testId} className="history-card">
                  <div className="history-score" style={{ color: scoreColor }}>
                    {item.score?.toFixed ? item.score.toFixed(1) : item.score}
                  </div>
                  <div className="history-details">
                    <div className="history-top">
                      <span className={`badge ${item.testMode === 'MOCK_TEST' ? 'badge-mock' : 'badge-practice'}`}>
                        {item.testMode === 'MOCK_TEST' ? 'Mock Test' : 'Practice'}
                      </span>
                      <span className="history-date">
                        {item.submittedAt ? new Date(item.submittedAt).toLocaleDateString('en-US', {
                          day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
                        }) : ''}
                      </span>
                    </div>
                    <div className="history-stats">
                      <span>{item.correctAnswers}/{item.totalQuestions} correct</span>
                      <span>
                        {item.totalQuestions ? Math.round((item.correctAnswers / item.totalQuestions) * 100) : 0}% accuracy
                      </span>
                    </div>
                    {item.historyId && (
                      <button
                        className="btn btn-sm btn-primary"
                        style={{ marginTop: '0.75rem' }}
                        onClick={() => navigate(`/history/${item.historyId}/review`)}
                        id={`review-listening-${item.testId}`}
                      >
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4, verticalAlign: 'middle' }}>
                          <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
                        </svg>
                        Review Answers
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
