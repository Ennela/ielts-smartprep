import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import statsApi from '../api/statsApi';
import SkillProgressCard from '../components/common/SkillProgressCard';
import ScoreTrendChart from '../components/analytics/ScoreTrendChart';

export default function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [overview, setOverview] = useState(null);
  const [trendSkill, setTrendSkill] = useState('READING');
  const [trendPeriod, setTrendPeriod] = useState('MONTHLY');
  const [trendData, setTrendData] = useState(null);
  const [history, setHistory] = useState(null);
  const [historySkill, setHistorySkill] = useState('');
  const [historyPage, setHistoryPage] = useState(0);

  useEffect(() => {
    statsApi.getOverview()
      .then(res => setOverview(res.data?.data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    statsApi.getScoreTrend(trendSkill, trendPeriod)
      .then(res => setTrendData(res.data?.data))
      .catch(() => {});
  }, [trendSkill, trendPeriod]);

  useEffect(() => {
    statsApi.getHistory(historySkill || null, historyPage, 5)
      .then(res => setHistory(res.data?.data))
      .catch(() => {});
  }, [historySkill, historyPage]);

  const displayName = user?.displayName || user?.username || 'You';
  const targetBand = overview?.targetBand || '8.0';
  const currentBand = overview?.currentEstimate || '—';

  return (
    <div className="dashboard-content">

      {/* ── Row 1: Welcome Hero Bento ── */}
      <section className="dash-hero reveal">
        <div className="dash-welcome-card">
          <div>
            <h1>Welcome, {displayName}.</h1>
            <p className="subtitle">
              Keep practicing to achieve your target band score.
            </p>
          </div>
          <div className="dash-welcome-actions">
            <button
              className="btn btn-primary"
              onClick={() => navigate('/reading')}
            >
              Continue Practice
            </button>
            <button
              className="btn btn-outline"
              onClick={() => navigate('/reading/history')}
            >
              View Details
            </button>
          </div>
        </div>

        {/* Target Band Card */}
        <div className="dash-target-card">
          <p className="dash-target-label">Overall Target Band</p>
          <div className="dash-target-band">{targetBand}</div>
          <div className="dash-target-chip">
            <span className="material-symbols-outlined" style={{ fontSize: 16 }}>trending_up</span>
            Current Est. {currentBand}
          </div>
        </div>
      </section>

      {/* ── Row 2: Skill Breakdown ── */}
      <section className="reveal reveal-delay-1">
        <div className="dash-section-header">
          <h2>Skill Breakdown</h2>
          <a href="#" onClick={e => { e.preventDefault(); navigate('/reading/history'); }}>
            View History
          </a>
        </div>

        <div className="skill-progress-row">
          {overview?.skills ? (
            overview.skills.map(skill => (
              <SkillProgressCard
                key={skill.skill}
                skill={skill.skill}
                currentAvg={skill.currentAvg}
                targetScore={skill.targetScore}
                progressPercent={skill.progressPercent}
                totalTests={skill.totalTests}
                gap={skill.gap}
                status={skill.status}
              />
            ))
          ) : (
            ['READING', 'WRITING', 'LISTENING'].map(s => (
              <div key={s} className="skill-progress-card" style={{ cursor: 'pointer' }} onClick={() => navigate(`/${s.toLowerCase()}`)}>
                <div className="spc-header">
                  <div className="spc-icon-wrap">
                    <span className="material-symbols-outlined">
                      {s === 'READING' ? 'menu_book' : s === 'WRITING' ? 'edit_note' : 'headphones'}
                    </span>
                  </div>
                  <span className={`spc-band-score spc-band-${s.toLowerCase()}`}>—</span>
                </div>
                <p className="spc-skill-name">{s.charAt(0) + s.slice(1).toLowerCase()}</p>
                <p className="spc-count">Start practicing to track your progress</p>
                <div className="spc-progress-bar">
                  <div className={`spc-progress-fill spc-fill-${s.toLowerCase()}`} style={{ width: '0%' }} />
                </div>
              </div>
            ))
          )}
        </div>
      </section>

      {/* ── Row 3: Score Trend Chart ── */}
      <div className="trend-section reveal reveal-delay-2">
        <div className="trend-header">
          <h2>Score Trends</h2>
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
          {trendData && trendData.dataPoints?.length > 0 ? (
            <ScoreTrendChart
              dataPoints={trendData.dataPoints}
              targetScore={trendData.targetScore}
              skill={trendData.skill}
            />
          ) : (
            <div className="trend-empty">
              <p>No data available. Start practicing to see your progress!</p>
            </div>
          )}
        </div>
      </div>

      {/* ── Row 4: Recent History ── */}
      <div className="history-section reveal reveal-delay-3">
        <div className="history-header">
          <h2>Recent Activity</h2>
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
                    {item.recordedAt ? new Date(item.recordedAt).toLocaleDateString('en-US', {
                      day: 'numeric', month: 'short', year: 'numeric',
                    }) : ''}
                  </span>
                </div>
              ))}
            </div>
            {history.totalPages > 1 && (
              <div className="ht-pagination">
                <button className="btn btn-sm btn-outline" disabled={historyPage === 0}
                  onClick={() => setHistoryPage(p => Math.max(0, p - 1))}>Previous</button>
                <span className="ht-page-info">Page {historyPage + 1} / {history.totalPages}</span>
                <button className="btn btn-sm btn-outline" disabled={historyPage >= history.totalPages - 1}
                  onClick={() => setHistoryPage(p => p + 1)}>Next</button>
              </div>
            )}
          </>
        ) : (
          <div className="trend-empty"><p>No history available.</p></div>
        )}
      </div>
    </div>
  );
}
