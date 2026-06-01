import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMockTest } from '../context/MockTestContext';
import mockTestApi from '../api/mockTestApi';

export default function MockTestLobbyPage() {
  const navigate = useNavigate();
  const { activeSession, startOrResumeTest, loadActiveSession, clearSession } = useMockTest();
  const [tests, setTests] = useState([]);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    // Load active session on mount
    loadActiveSession().catch(() => {});
    
    // Load available mock tests
    setLoading(true);
    mockTestApi.getAllMockTests()
      .then(res => {
        setTests(res.data?.data || []);
      })
      .catch(err => {
        console.error('Failed to load mock tests', err);
      })
      .finally(() => setLoading(false));

    // Load attempt history
    setHistoryLoading(true);
    mockTestApi.getHistory()
      .then(res => {
        setHistory(res.data?.data || []);
      })
      .catch(err => {
        console.error('Failed to load history', err);
      })
      .finally(() => setHistoryLoading(false));
  }, []);

  const handleStartTest = async (testId) => {
    try {
      setActionLoading(true);
      await startOrResumeTest(testId);
      // Retrieve the current session to get the ID
      const session = await loadActiveSession();
      if (session) {
        navigate(`/mock-tests/take/${session.sessionId}`);
      }
    } catch (err) {
      alert(err.message || 'Failed to start test');
    } finally {
      setActionLoading(false);
    }
  };

  const handleResumeActive = () => {
    if (activeSession) {
      navigate(`/mock-tests/take/${activeSession.sessionId}`);
    }
  };

  const handleCancelActive = () => {
    if (window.confirm('Are you sure you want to abandon this mock test? Your progress will be lost.')) {
      clearSession();
    }
  };

  // Compute quick statistics
  const completedHistory = history.filter(h => h.status === 'COMPLETED');
  const avgBand = completedHistory.length > 0 
    ? (completedHistory.reduce((acc, h) => acc + (h.overallBand || 0), 0) / completedHistory.length).toFixed(1)
    : '—';

  return (
    <div className="dashboard-content">
      
      {/* ── Header ── */}
      <section className="dash-hero reveal" style={{ gridTemplateColumns: '1fr' }}>
        <div className="dash-welcome-card" style={{ gridColumn: '1 / -1' }}>
          <div>
            <h1 style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <span className="material-symbols-outlined" style={{ fontSize: '3rem', color: 'var(--primary)' }}>
                quiz
              </span>
              IELTS Full Mock Test Center
            </h1>
            <p className="subtitle" style={{ fontSize: '1.05rem', marginTop: '12px' }}>
              Experience the real exam pressure. Complete Listening, Reading, and Writing in a unified sitting. 
              Get immediate scoring and AI essay feedback.
            </p>
          </div>
        </div>
      </section>

      {/* ── Active Session Box ── */}
      {activeSession && (
        <section className="reveal" style={{ animationDelay: '0.1s' }}>
          <div className="card" style={{
            borderLeft: '4px solid var(--tertiary-container)',
            background: 'rgba(172,60,0,0.03)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '16px'
          }}>
            <div>
              <h3 style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--tertiary-container)' }}>
                <span className="material-symbols-outlined">hourglass_empty</span>
                Test in Progress
              </h3>
              <p style={{ fontSize: '0.9rem', color: 'var(--color-text-secondary)', marginTop: '4px' }}>
                You have an active session for <strong>{activeSession.title}</strong>, currently at the <strong>{activeSession.currentSection}</strong> section.
              </p>
            </div>
            <div style={{ display: 'flex', gap: '12px' }}>
              <button className="btn btn-primary" onClick={handleResumeActive}>
                Resume Exam
              </button>
              <button className="btn btn-outline" style={{ color: 'var(--error)', borderColor: 'var(--error)' }} onClick={handleCancelActive}>
                Abandon Exam
              </button>
            </div>
          </div>
        </section>
      )}

      {/* ── Quick Stats Grid ── */}
      <section className="reveal" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '24px' }}>
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div className="spc-icon-wrap" style={{ width: '48px', height: '48px', background: 'var(--primary-fixed)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--primary)' }}>history</span>
          </div>
          <div>
            <p style={{ fontSize: '0.8rem', color: 'var(--outline)', textTransform: 'uppercase', fontWeight: 600 }}>Total Attempts</p>
            <p style={{ fontSize: '1.8rem', fontWeight: 700 }}>{history.length}</p>
          </div>
        </div>
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div className="spc-icon-wrap" style={{ width: '48px', height: '48px', background: 'rgba(0, 108, 74, 0.1)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--secondary)' }}>emoji_events</span>
          </div>
          <div>
            <p style={{ fontSize: '0.8rem', color: 'var(--outline)', textTransform: 'uppercase', fontWeight: 600 }}>Average Band Score</p>
            <p style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--secondary)' }}>{avgBand}</p>
          </div>
        </div>
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div className="spc-icon-wrap" style={{ width: '48px', height: '48px', background: 'rgba(245,158,11,0.1)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--tertiary-container)' }}>schedule</span>
          </div>
          <div>
            <p style={{ fontSize: '0.8rem', color: 'var(--outline)', textTransform: 'uppercase', fontWeight: 600 }}>Duration per Exam</p>
            <p style={{ fontSize: '1.8rem', fontWeight: 700 }}>2h 40m</p>
          </div>
        </div>
      </section>

      {/* ── Test List ── */}
      <section className="reveal" style={{ marginTop: '12px' }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '20px' }}>Available Full Mock Tests</h2>
        
        {loading ? (
          <div className="loading-spinner">
            <span className="spinner" />
            <span>Loading mock tests...</span>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '24px' }}>
            {tests.map(test => (
              <div key={test.mockTestId} className="card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                    <h3 style={{ fontSize: '1.2rem', fontWeight: 700 }}>{test.title}</h3>
                    <span className="exam-diff-badge">{test.difficulty}</span>
                  </div>
                  <p style={{ fontSize: '0.9rem', color: 'var(--color-text-secondary)', marginBottom: '16px' }}>
                    {test.description || 'Full academic simulation including all question types.'}
                  </p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', marginBottom: '24px' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                      <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>headphones</span>
                      Listening: {test.listeningPartsCount} Parts
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                      <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>menu_book</span>
                      Reading: {test.readingQuizzesCount} Passages
                    </span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                      <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>edit_note</span>
                      Writing: {test.writingPromptsCount} Tasks
                    </span>
                  </div>
                </div>
                <button 
                  className="btn btn-primary" 
                  disabled={actionLoading || activeSession}
                  onClick={() => handleStartTest(test.mockTestId)}
                  style={{ width: '100%' }}
                >
                  Start Mock Test
                </button>
              </div>
            ))}
            {tests.length === 0 && <p style={{ color: 'var(--color-text-muted)' }}>No mock tests available.</p>}
          </div>
        )}
      </section>

      {/* ── Attempt History ── */}
      <section className="reveal" style={{ marginTop: '24px' }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 600, marginBottom: '20px' }}>Your Exam History</h2>
        
        {historyLoading ? (
          <div className="loading-spinner">
            <span className="spinner" />
            <span>Loading history...</span>
          </div>
        ) : history.length > 0 ? (
          <div className="history-table">
            <div className="ht-header" style={{ gridTemplateColumns: '2fr 1fr 1fr 1.2fr' }}>
              <span>Test Title</span>
              <span>Overall Band</span>
              <span>Status</span>
              <span>Date & Actions</span>
            </div>
            {history.map(item => (
              <div key={item.submissionId} className="ht-row" style={{ gridTemplateColumns: '2fr 1fr 1fr 1.2fr' }}>
                <span style={{ fontWeight: 600 }}>{item.title}</span>
                <span className="ht-score" style={{ color: item.status === 'COMPLETED' ? 'var(--secondary)' : 'inherit' }}>
                  {item.status === 'COMPLETED' ? (item.overallBand?.toFixed(1) || '—') : '—'}
                </span>
                <span>
                  <span className={`badge ${
                    item.status === 'COMPLETED' ? 'badge-reading' : 
                    item.status === 'GRADING' ? 'badge-listening' : 'badge-writing'
                  }`} style={{ textTransform: 'capitalize' }}>
                    {item.status === 'GRADING' ? 'AI Grading...' : item.status.toLowerCase()}
                  </span>
                </span>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
                  <span className="ht-date">
                    {new Date(item.submittedAt).toLocaleDateString('en-US', {
                      day: 'numeric', month: 'short', year: 'numeric'
                    })}
                  </span>
                  <button 
                    className="btn btn-sm btn-outline" 
                    onClick={() => navigate(`/mock-tests/result/${item.submissionId}`)}
                  >
                    View Report
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="card" style={{ textAlign: 'center', padding: '40px', color: 'var(--color-text-muted)' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '3rem', marginBottom: '12px' }}>
              folder_open
            </span>
            <p>You haven't taken any full mock tests yet. Your reports will appear here once you complete an exam.</p>
          </div>
        )}
      </section>
    </div>
  );
}
