import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import readingApi from '../api/readingApi';

export default function ReadingHistoryPage() {
  const navigate = useNavigate();
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        const res = await readingApi.getHistory();
        setHistory(res.data.data || []);
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load history');
      } finally {
        setLoading(false);
      }
    };
    fetchHistory();
  }, []);

  if (loading) {
    return <div className="loading-screen">Loading history...</div>;
  }

  return (
    <div className="reading-history-page" id="reading-history-page">
      <div className="history-content">
        <div className="history-header">
          <button className="btn-back" onClick={() => navigate('/reading')} id="back-to-config">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            Go Back
          </button>
          <h1>Reading History</h1>
          <p className="subtitle">Your previous Reading practice sessions</p>
        </div>

        {error && <div className="error-msg">{error}</div>}

        {history.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
            </div>
            <p>No Reading tests completed yet.</p>
            <button className="btn btn-primary" onClick={() => navigate('/reading')}>Start your first test</button>
          </div>
        ) : (
          <div className="history-table-wrapper">
            <table className="history-table" id="history-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Topic</th>
                  <th>Difficulty</th>
                  <th>Score</th>
                  <th>Band</th>
                  <th>Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item, idx) => (
                  <tr key={item.quizId}>
                    <td>{idx + 1}</td>
                    <td><span className="meta-badge">{item.topic}</span></td>
                    <td><span className="meta-badge diff">{item.difficulty?.replace('_', ' ')}</span></td>
                    <td>{item.correctAnswers}/{item.totalQuestions}</td>
                    <td><span className={`band-score band-${getBandClass(item.bandScore)}`}>{item.bandScore}</span></td>
                    <td>{formatDate(item.submittedAt || item.createdAt)}</td>
                    <td>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <button
                          className="btn btn-sm btn-outline"
                          onClick={() => navigate(`/reading/result/${item.quizId}`)}
                          id={`view-detail-${item.quizId}`}
                        >
                          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4, verticalAlign: 'middle' }}>
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                            <circle cx="12" cy="12" r="3"/>
                          </svg>
                          Details
                        </button>
                        {item.historyId && (
                          <button
                            className="btn btn-sm btn-primary"
                            onClick={() => navigate(`/history/${item.historyId}/review`)}
                            id={`review-answers-${item.quizId}`}
                          >
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4, verticalAlign: 'middle' }}>
                              <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
                            </svg>
                            Review
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function getBandClass(score) {
  const s = parseFloat(score);
  if (s >= 7.0) return 'high';
  if (s >= 5.5) return 'mid';
  return 'low';
}

function formatDate(dateStr) {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' });
}
