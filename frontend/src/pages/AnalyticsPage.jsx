import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import statsApi from '../api/statsApi';
import ScoreTrendChart from '../components/analytics/ScoreTrendChart';

export default function AnalyticsPage() {
  const navigate = useNavigate();
  const [overview, setOverview] = useState(null);
  const [trendSkill, setTrendSkill] = useState('READING');
  const [trendPeriod, setTrendPeriod] = useState('MONTHLY');
  const [trendData, setTrendData] = useState(null);
  const [history, setHistory] = useState(null);
  const [historySkill, setHistorySkill] = useState('');
  const [historyPage, setHistoryPage] = useState(0);
  const [loading, setLoading] = useState(true);

  // Load overview
  useEffect(() => {
    statsApi.getOverview()
      .then(res => setOverview(res.data?.data))
      .catch(err => console.error(err))
      .finally(() => setLoading(false));
  }, []);

  // Load trend data
  useEffect(() => {
    statsApi.getScoreTrend(trendSkill, trendPeriod)
      .then(res => setTrendData(res.data?.data))
      .catch(err => console.error(err));
  }, [trendSkill, trendPeriod]);

  // Load history
  useEffect(() => {
    statsApi.getHistory(historySkill || null, historyPage, 8)
      .then(res => setHistory(res.data?.data))
      .catch(err => console.error(err));
  }, [historySkill, historyPage]);

  if (loading) return (
    <div className="analytics-page">
      <div className="loading-spinner"><div className="spinner" /></div>
    </div>
  );

  const getProgressColor = (pct) => {
    if (pct >= 80) return 'var(--color-success)';
    if (pct >= 50) return 'var(--color-warning)';
    return 'var(--color-error)';
  };

  const skillIcons = {
    READING: '📖',
    WRITING: '✍️',
    LISTENING: '🎧'
  };

  return (
    <div className="analytics-page">
      <div className="analytics-content">
        <h1>Analytics & Progress</h1>
        <p className="subtitle">Track your IELTS preparation journey</p>

        {/* Row 1: Skill Progress Cards */}
        <div className="skill-progress-row">
          {overview?.skills?.map(skill => (
            <div key={skill.skill} className="skill-progress-card">
              <div className="spc-header">
                <span className="spc-icon">{skillIcons[skill.skill]}</span>
                <span className="spc-skill-name">{skill.skill}</span>
                <span className={`spc-status ${skill.status === 'TARGET_MET' ? 'status-met' : 'status-progress'}`}>
                  {skill.status === 'TARGET_MET' ? 'Target Met' : 'In Progress'}
                </span>
              </div>
              <div className="spc-scores">
                <div className="spc-current">
                  <span className="spc-score-value">{skill.currentAvg?.toFixed ? skill.currentAvg.toFixed(1) : '0.0'}</span>
                  <span className="spc-score-label">Current Avg</span>
                </div>
                <div className="spc-separator">/</div>
                <div className="spc-target">
                  <span className="spc-score-value">{skill.targetScore?.toFixed ? skill.targetScore.toFixed(1) : '6.5'}</span>
                  <span className="spc-score-label">Target</span>
                </div>
              </div>
              <div className="spc-progress-bar">
                <div
                  className="spc-progress-fill"
                  style={{
                    width: `${Math.min(100, skill.progressPercent || 0)}%`,
                    background: getProgressColor(skill.progressPercent || 0)
                  }}
                />
              </div>
              <div className="spc-footer">
                <span>{skill.totalTests || 0} tests taken</span>
                {skill.status !== 'TARGET_MET' && (
                  <span className="spc-gap">Gap: {skill.gap?.toFixed ? skill.gap.toFixed(1) : '0.0'}</span>
                )}
              </div>
            </div>
          ))}
        </div>

        {/* Row 2: Score Trend Chart */}
        <div className="trend-section">
          <div className="trend-header">
            <h2>Score Trend</h2>
            <div className="trend-controls">
              <div className="trend-tabs">
                {['READING', 'WRITING', 'LISTENING'].map(s => (
                  <button
                    key={s}
                    className={`trend-tab ${trendSkill === s ? 'active' : ''}`}
                    onClick={() => setTrendSkill(s)}
                  >
                    {s}
                  </button>
                ))}
              </div>
              <select
                className="trend-period-select"
                value={trendPeriod}
                onChange={e => setTrendPeriod(e.target.value)}
              >
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </div>
          </div>
          <div className="trend-chart-wrapper">
            {trendData && (
              <ScoreTrendChart
                dataPoints={trendData.dataPoints || []}
                targetScore={trendData.targetScore}
                skill={trendData.skill}
              />
            )}
            {(!trendData || !trendData.dataPoints?.length) && (
              <div className="trend-empty">
                <p>No data yet. Start practicing to see your progress!</p>
              </div>
            )}
          </div>
        </div>

        {/* Row 3: History Table */}
        <div className="history-section">
          <div className="history-header">
            <h2>Score History</h2>
            <div className="history-filter">
              <select
                className="trend-period-select"
                value={historySkill}
                onChange={e => { setHistorySkill(e.target.value); setHistoryPage(0); }}
              >
                <option value="">All Skills</option>
                <option value="READING">Reading</option>
                <option value="WRITING">Writing</option>
                <option value="LISTENING">Listening</option>
              </select>
            </div>
          </div>

          {history?.items?.length > 0 ? (
            <>
              <div className="history-table">
                <div className="ht-header">
                  <span>Skill</span>
                  <span>Score</span>
                  <span>Date</span>
                </div>
                {history.items.map((item, i) => (
                  <div key={i} className="ht-row">
                    <span className={`ht-skill badge badge-${item.skillType?.toLowerCase()}`}>
                      {item.skillType}
                    </span>
                    <span className="ht-score">{item.score?.toFixed ? item.score.toFixed(1) : item.score}</span>
                    <span className="ht-date">
                      {item.recordedAt ? new Date(item.recordedAt).toLocaleDateString('en-GB', {
                        day: 'numeric', month: 'short', year: 'numeric'
                      }) : ''}
                    </span>
                  </div>
                ))}
              </div>
              {history.totalPages > 1 && (
                <div className="ht-pagination">
                  <button
                    className="btn btn-sm btn-outline"
                    disabled={historyPage === 0}
                    onClick={() => setHistoryPage(p => Math.max(0, p - 1))}
                  >Previous</button>
                  <span className="ht-page-info">
                    Page {historyPage + 1} of {history.totalPages}
                  </span>
                  <button
                    className="btn btn-sm btn-outline"
                    disabled={historyPage >= history.totalPages - 1}
                    onClick={() => setHistoryPage(p => p + 1)}
                  >Next</button>
                </div>
              )}
            </>
          ) : (
            <div className="trend-empty">
              <p>No history entries yet.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
