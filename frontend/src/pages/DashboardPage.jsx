import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import statsApi from '../api/statsApi';
import analyticsApi from '../api/analyticsApi';
import SkillProgressCard from '../components/common/SkillProgressCard';
import ScoreTrendChart from '../components/analytics/ScoreTrendChart';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar } from 'recharts';

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

  const [weaknessSkill, setWeaknessSkill] = useState('');
  const [weaknessData, setWeaknessData] = useState(null);

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

  useEffect(() => {
    analyticsApi.getWeakness(weaknessSkill)
      .then(res => setWeaknessData(res.data?.data))
      .catch(() => {});
  }, [weaknessSkill]);

  const displayName = user?.displayName || user?.username || 'You';
  const targetBand = overview?.targetBand || '8.0';
  const currentBand = overview?.currentEstimate || '—';

  // Bar Chart: Average vs. Target Score
  const barChartData = overview?.skills ? overview.skills.map(item => ({
    name: item.skill.charAt(0) + item.skill.slice(1).toLowerCase(),
    Average: parseFloat(item.currentAvg) || 0,
    Target: parseFloat(item.targetScore) || 0
  })) : [];

  // Radar Chart: Question Type Accuracy
  const radarChartData = weaknessData && weaknessData.accuracies ? Object.keys(weaknessData.accuracies).map(type => ({
    subject: type,
    Accuracy: weaknessData.accuracies[type],
    fullMark: 100
  })) : [];

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

      {/* ── Row 3: Charts Layout Grid (Score Trend & Comparisons) ── */}
      <section className="dash-charts-grid reveal reveal-delay-2">
        
        {/* Score Trend Line Chart */}
        <div className="trend-section" style={{ marginBottom: 0 }}>
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

        {/* Skill Averages vs Targets Bar Chart */}
        <div className="trend-section" style={{ marginBottom: 0 }}>
          <div className="trend-header">
            <h2>Average vs. Target Scores</h2>
          </div>
          <div style={{ width: '100%', height: '320px' }}>
            {barChartData.length > 0 ? (
              <ResponsiveContainer>
                <BarChart data={barChartData} margin={{ top: 20, right: 30, left: 10, bottom: 10 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border-subtle)" />
                  <XAxis dataKey="name" stroke="var(--color-text-muted)" fontSize={12} tickLine={false} />
                  <YAxis domain={[0, 9]} ticks={[0,1,2,3,4,5,6,7,8,9]} stroke="var(--color-text-muted)" fontSize={12} tickLine={false} />
                  <Tooltip contentStyle={{ background: 'var(--color-surface-solid)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', color: 'var(--color-text)' }} />
                  <Legend wrapperStyle={{ fontSize: 12, fontFamily: 'var(--font-heading)' }} />
                  <Bar dataKey="Average" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="Target" fill="var(--color-emerald)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="trend-empty">
                <p>Calculating skill averages...</p>
              </div>
            )}
          </div>
        </div>

      </section>

      {/* ── Row 4: Weakness & Question Types Analysis ── */}
      <section className="dash-charts-grid reveal reveal-delay-2">
        
        {/* Radar Chart Question Type Accuracy */}
        <div className="trend-section" style={{ marginBottom: 0 }}>
          <div className="trend-header">
            <h2>Question Type Performance</h2>
            <select
              value={weaknessSkill}
              onChange={e => setWeaknessSkill(e.target.value)}
              className="trend-period-select"
            >
              <option value="">All Skills</option>
              <option value="READING">Reading</option>
              <option value="LISTENING">Listening</option>
            </select>
          </div>
          <div style={{ width: '100%', height: '320px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            {radarChartData.length > 0 ? (
              <ResponsiveContainer>
                <RadarChart cx="50%" cy="50%" outerRadius="70%" data={radarChartData}>
                  <PolarGrid stroke="var(--color-border-subtle)" />
                  <PolarAngleAxis dataKey="subject" stroke="var(--color-text-muted)" tick={{ fontSize: 11 }} />
                  <PolarRadiusAxis angle={30} domain={[0, 100]} stroke="var(--color-text-muted)" tick={{ fontSize: 9 }} />
                  <Radar name="Accuracy %" dataKey="Accuracy" stroke="var(--color-primary)" fill="var(--color-primary)" fillOpacity={0.3} />
                  <Tooltip contentStyle={{ background: 'var(--color-surface-solid)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-sm)', color: 'var(--color-text)' }} />
                </RadarChart>
              </ResponsiveContainer>
            ) : (
              <div className="trend-empty">
                <p>No question type accuracy details found. Complete more practice sets!</p>
              </div>
            )}
          </div>
        </div>

        {/* Breakdown table & Smart recommendations */}
        <div className="trend-section" style={{ marginBottom: 0, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <div className="trend-header">
              <h2>Correctness Rates</h2>
            </div>
            <div style={{ maxHeight: '200px', overflowY: 'auto', marginBottom: '20px' }}>
              {weaknessData?.accuracies && Object.keys(weaknessData.accuracies).length > 0 ? (
                <div className="history-table" style={{ border: '1px solid var(--color-border)' }}>
                  <div className="ht-header" style={{ gridTemplateColumns: '1fr 1fr' }}>
                    <span>Question Type</span>
                    <span style={{ textAlign: 'right' }}>Accuracy %</span>
                  </div>
                  {Object.keys(weaknessData.accuracies).map((type, i) => {
                    const acc = weaknessData.accuracies[type];
                    return (
                      <div key={i} className="ht-row" style={{ gridTemplateColumns: '1fr 1fr' }}>
                        <span>{type}</span>
                        <span style={{ textAlign: 'right', fontWeight: 700, color: acc >= 85 ? 'var(--color-success)' : acc >= 50 ? 'var(--color-warning)' : 'var(--color-error)' }}>
                          {acc}%
                        </span>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="trend-empty" style={{ minHeight: '120px' }}>
                  <p>Practice attempts required to view detailed breakdown.</p>
                </div>
              )}
            </div>
          </div>

          {/* Smart Recommendation Tip */}
          {(weaknessData?.recommendation || overview?.improvementTip) && (
            <div style={{
              background: 'rgba(0, 63, 177, 0.04)',
              borderLeft: '4px solid var(--color-primary)',
              padding: '16px',
              borderRadius: '0 var(--radius-md) var(--radius-md) 0',
              fontSize: '0.9rem',
              color: 'var(--color-text-secondary)',
              lineHeight: '1.5'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', color: 'var(--color-primary)', fontWeight: 700 }}>
                <span className="material-symbols-outlined" style={{ fontSize: 20 }}>tips_and_updates</span>
                <span>Smart Recommendation</span>
              </div>
              {weaknessData?.recommendation || overview?.improvementTip}
            </div>
          )}
        </div>

      </section>

      {/* ── Row 5: Recent History ── */}
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
