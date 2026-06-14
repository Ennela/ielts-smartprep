import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import listeningApi from '../api/listeningApi';
import styles from '../styles/History.module.css';

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

  const getScoreColor = (score) => {
    const s = parseFloat(score);
    if (s >= 7.0) return 'var(--color-success)';
    if (s >= 5.5) return 'var(--color-warning)';
    return 'var(--color-error)';
  };

  return (
    <div className={styles.container}>
      
      {/* Header */}
      <div className={styles.header}>
        <button className="btn-back" onClick={() => navigate('/listening')} id="back-to-listening">
          <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>arrow_back</span>
          Back to Listening
        </button>
        <h1 className={styles.title}>Listening History</h1>
        <p className={styles.subtitle}>Review your previous Listening tests and practice sessions</p>
      </div>

      {loading ? (
        <div className="loading-spinner" style={{ margin: '48px auto' }}>
          <span className="spinner" />
          <span>Loading listening history...</span>
        </div>
      ) : history.length === 0 ? (
        <div style={{
          textAlign: 'center',
          padding: '4rem 2rem',
          border: '2px dashed var(--outline-variant)',
          borderRadius: '12px',
          color: 'var(--outline)'
        }}>
          <p style={{ fontSize: '1.1rem', marginBottom: '16px', fontWeight: 600 }}>No listening tests taken yet.</p>
          <button className="btn btn-primary" onClick={() => navigate('/listening')}>
            Start Practice
          </button>
        </div>
      ) : (
        <div className={styles['history-grid']}>
          {history.map(item => (
            <div key={item.testId} className={styles['history-card']}>
              <div>
                <div className={styles['card-top']}>
                  <span className={styles.badge} style={{ 
                    background: item.testMode === 'MOCK_TEST' ? 'var(--secondary-fixed)' : 'var(--primary-fixed)',
                    color: item.testMode === 'MOCK_TEST' ? 'var(--on-secondary-fixed-variant)' : 'var(--on-primary-fixed-variant)' 
                  }}>
                    {item.testMode === 'MOCK_TEST' ? 'Mock Test' : 'Practice'}
                  </span>
                  <span className={styles.date}>
                    {item.submittedAt ? new Date(item.submittedAt).toLocaleDateString('en-US', {
                      day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
                    }) : ''}
                  </span>
                </div>
                <div style={{ marginTop: '16px' }}>
                  <p style={{ fontSize: '0.92rem', color: 'var(--on-surface-variant)', fontWeight: 600, margin: 0 }}>
                    {item.correctAnswers} / {item.totalQuestions} Correct Questions
                  </p>
                  <p style={{ fontSize: '0.85rem', color: 'var(--outline)', marginTop: '4px', marginBottom: 0 }}>
                    Accuracy: {item.totalQuestions ? Math.round((item.correctAnswers / item.totalQuestions) * 100) : 0}%
                  </p>
                </div>
              </div>
              
              <div className={styles['card-bottom']}>
                <button
                  className="btn btn-sm btn-outline"
                  onClick={() => {
                    if (item.historyId) {
                      navigate(`/history/${item.historyId}/review`);
                    } else {
                      navigate(`/listening/result/${item.testId}`);
                    }
                  }}
                  id={`review-listening-${item.testId}`}
                >
                  Review Answers
                </button>
                <span className={styles.score} style={{ color: getScoreColor(item.score) }}>
                  Band {item.score?.toFixed ? item.score.toFixed(1) : item.score}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

    </div>
  );
}
