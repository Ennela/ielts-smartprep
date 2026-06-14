import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import statsApi from '../api/statsApi';
import analyticsApi from '../api/analyticsApi';
import ScoreTrendChart from '../components/analytics/ScoreTrendChart';

export default function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [overview, setOverview] = useState(null);
  const [trendSkill, setTrendSkill] = useState('READING');
  const [trendPeriod, setTrendPeriod] = useState('MONTHLY');
  const [trendData, setTrendData] = useState(null);
  const [history, setHistory] = useState(null);
  const [historySkill, setHistorySkill] = useState('');
  const [historyPage, setHistoryPage] = useState(0);

  const [weaknessSkill, setWeaknessSkill] = useState('');
  const [weaknessData, setWeaknessData] = useState(null);

  const fetchDashboardData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [overviewRes, trendRes, historyRes, weaknessRes] = await Promise.all([
        statsApi.getOverview(),
        statsApi.getScoreTrend(trendSkill, trendPeriod),
        statsApi.getHistory(historySkill || undefined, historyPage, 5),
        analyticsApi.getWeakness(weaknessSkill || undefined)
      ]);

      setOverview(overviewRes.data?.data);
      setTrendData(trendRes.data?.data);
      setHistory(historyRes.data?.data);
      setWeaknessData(weaknessRes.data?.data);
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.message || err.message || 'Failed to load dashboard data. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, []);

  // Fetch trend data on parameter changes
  useEffect(() => {
    if (loading) return;
    statsApi.getScoreTrend(trendSkill, trendPeriod)
      .then(res => setTrendData(res.data?.data))
      .catch(err => console.error(err));
  }, [trendSkill, trendPeriod]);

  // Fetch history data on parameter changes
  useEffect(() => {
    if (loading) return;
    statsApi.getHistory(historySkill || undefined, historyPage, 5)
      .then(res => setHistory(res.data?.data))
      .catch(err => console.error(err));
  }, [historySkill, historyPage]);

  // Fetch weakness data on parameter changes
  useEffect(() => {
    if (loading) return;
    analyticsApi.getWeakness(weaknessSkill || undefined)
      .then(res => setWeaknessData(res.data?.data))
      .catch(err => console.error(err));
  }, [weaknessSkill]);

  const displayName = user?.displayName || user?.username || 'Student';
  
  // Handlers
  const handleRetry = () => {
    fetchDashboardData();
  };

  if (loading) {
    return <DashboardSkeleton />;
  }

  if (error) {
    return <DashboardError message={error} onRetry={handleRetry} />;
  }

  // Check if user has zero tests completed
  const totalTestsCount = overview?.totalTests || 0;
  if (totalTestsCount === 0) {
    return <DashboardEmptyState displayName={displayName} navigate={navigate} />;
  }

  const targetBand = overview?.targetBand || '7.5';
  const currentBand = overview?.currentEstimate || '6.5';
  
  // Default fallback lists if null
  const skillsList = overview?.skills && overview.skills.length > 0 ? overview.skills : [
    { skill: 'READING', currentAvg: 6.5, targetScore: 7.5, progressPercent: 86, gap: 1.0, status: 'IN_PROGRESS' },
    { skill: 'WRITING', currentAvg: 6.0, targetScore: 7.0, progressPercent: 85, gap: 1.0, status: 'IN_PROGRESS' },
    { skill: 'LISTENING', currentAvg: 7.0, targetScore: 7.5, progressPercent: 93, gap: 0.5, status: 'IN_PROGRESS' }
  ];

  const getSkillConfig = (skillName) => {
    switch(skillName.toUpperCase()) {
      case 'READING':
        return { label: 'Reading', border: 'border-t-secondary', text: 'text-secondary', icon: 'menu_book', progressBg: 'bg-secondary' };
      case 'WRITING':
        return { label: 'Writing', border: 'border-t-tertiary-container', text: 'text-tertiary-container', icon: 'edit_note', progressBg: 'bg-tertiary-container' };
      case 'LISTENING':
        return { label: 'Listening', border: 'border-t-primary-container', text: 'text-primary-container', icon: 'headset', progressBg: 'bg-primary-container' };
      default:
        return { label: skillName, border: 'border-t-primary', text: 'text-primary', icon: 'school', progressBg: 'bg-primary' };
    }
  };

  // Weakness metrics
  const weakestType = weaknessData?.weakestType || 'None';
  const weakestAccuracy = weaknessData?.weakestAccuracy !== undefined ? Math.round(weaknessData.weakestAccuracy) : 100;
  
  // Calculate overall correctness as an average of weakness accuracies
  const accuracies = weaknessData?.accuracies || {};
  const accuracyValues = Object.values(accuracies);
  const overallAccuracy = accuracyValues.length > 0
    ? Math.round(accuracyValues.reduce((sum, val) => sum + val, 0) / accuracyValues.length)
    : 72;

  return (
    <div className="space-y-lg animate-fade-in">
      {/* Welcome Header */}
      <header className="mb-lg">
        <h1 className="font-headline-md text-headline-md text-on-surface mb-xs">Welcome back, {displayName}!</h1>
        <p className="font-body-lg text-body-lg text-on-surface-variant">Keep practicing to reach your {targetBand} target.</p>
      </header>

      {/* Bento Grid Layout */}
      <div className="grid grid-cols-12 gap-gutter">
        {/* Overall Target Card (Span 12 mobile, 4 desktop) */}
        <div className="col-span-12 lg:col-span-4 bg-primary-container text-on-primary rounded-xl p-lg shadow-sm border border-outline-variant/20 relative overflow-hidden flex flex-col justify-between min-h-[220px]">
          {/* Background Blobs decoration */}
          <div className="absolute -right-8 -top-8 w-48 h-48 bg-primary rounded-full opacity-50 blur-3xl"></div>
          <div className="absolute -left-8 -bottom-8 w-32 h-32 bg-secondary rounded-full opacity-50 blur-2xl"></div>
          
          <div className="relative z-10">
            <h2 className="font-title-lg text-title-lg text-primary-fixed-dim mb-xs">Overall Target Band</h2>
            <div className="font-display-lg text-[64px] leading-none font-extrabold mb-sm text-on-primary tracking-tighter">{targetBand}</div>
          </div>
          
          <div className="relative z-10 flex justify-between items-end border-t border-primary/30 pt-md mt-auto">
            <div>
              <span className="font-label-md text-label-md text-primary-fixed-dim block mb-1">Current Estimate</span>
              <span className="font-headline-md text-headline-md text-on-primary">{currentBand}</span>
            </div>
            <span className="material-symbols-outlined text-[32px] text-tertiary-fixed-dim">trending_up</span>
          </div>
        </div>

        {/* Skill Breakdown Cards */}
        <div className="col-span-12 lg:col-span-8 grid grid-cols-1 md:grid-cols-3 gap-gutter">
          {skillsList.map((skillItem) => {
            const config = getSkillConfig(skillItem.skill);
            const currentVal = parseFloat(skillItem.currentAvg) || 0;
            const targetVal = parseFloat(skillItem.targetScore) || 7.0;
            const progress = Math.min(100, Math.round(skillItem.progressPercent));
            const gap = parseFloat(skillItem.gap) || 0;

            return (
              <div 
                key={skillItem.skill}
                onClick={() => navigate(`/${skillItem.skill.toLowerCase()}`)}
                className={`bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 border-t-[4px] ${config.border} overflow-hidden flex flex-col hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 cursor-pointer`}
              >
                <div className="p-md border-b border-outline-variant/30 flex justify-between items-center bg-surface-bright">
                  <div className="flex items-center gap-sm">
                    <span className={`material-symbols-outlined ${config.text}`}>{config.icon}</span>
                    <h3 className="font-title-lg text-title-lg text-on-surface">{config.label}</h3>
                  </div>
                </div>
                
                <div className="p-md flex-grow flex flex-col justify-center">
                  <div className="flex justify-between items-end mb-sm">
                    <div>
                      <span className="font-display-lg text-[40px] leading-none font-bold text-on-surface tracking-tight">
                        {currentVal > 0 ? currentVal.toFixed(1) : '—'}
                      </span>
                      <span className="font-label-md text-label-md text-on-surface-variant ml-1">Current</span>
                    </div>
                    <div className="text-right">
                      <span className="font-body-md text-body-md text-outline">Target: {targetVal.toFixed(1)}</span>
                    </div>
                  </div>
                  
                  <div className="w-full bg-surface-container-high h-2 rounded-full overflow-hidden mb-xs">
                    <div className={`${config.progressBg} h-full rounded-full`} style={{ width: `${progress}%` }}></div>
                  </div>
                  
                  <p className={`font-label-md text-label-md text-right ${gap > 0 ? 'text-error' : 'text-primary'}`}>
                    {gap > 0 ? `Need ${gap.toFixed(1)} more` : 'Target met!'}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-gutter mt-sm">
        {/* Score Trends Line Chart */}
        <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 p-lg flex flex-col">
          <div className="flex flex-wrap justify-between items-center gap-sm mb-lg">
            <h3 className="font-headline-md text-headline-md text-on-surface">Score Trends</h3>
            <div className="flex items-center gap-xs">
              <select 
                value={trendSkill}
                onChange={(e) => setTrendSkill(e.target.value)}
                className="bg-surface-container-low text-on-surface font-label-md text-label-md border-outline-variant rounded-md px-sm py-1 cursor-pointer focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="READING">Reading</option>
                <option value="WRITING">Writing</option>
                <option value="LISTENING">Listening</option>
              </select>
              <select 
                value={trendPeriod}
                onChange={(e) => setTrendPeriod(e.target.value)}
                className="bg-surface-container-low text-on-surface font-label-md text-label-md border-outline-variant rounded-md px-sm py-1 cursor-pointer focus:outline-none focus:ring-1 focus:ring-primary"
              >
                <option value="MONTHLY">Last 6 Months</option>
                <option value="WEEKLY">Last 3 Months</option>
              </select>
            </div>
          </div>
          
          <div className="w-full h-[320px] rounded-md overflow-hidden bg-surface-bright flex items-center justify-center p-xs relative">
            {trendData && trendData.dataPoints?.length > 0 ? (
              <ScoreTrendChart
                dataPoints={trendData.dataPoints}
                targetScore={trendData.targetScore}
                skill={trendData.skill}
              />
            ) : (
              <div className="flex flex-col items-center justify-center text-center p-md">
                <span className="material-symbols-outlined text-[48px] text-outline-variant mb-sm">show_chart</span>
                <p className="font-body-md text-body-md text-outline">No score trends available for this skill. Try practicing!</p>
              </div>
            )}
          </div>
        </div>

        {/* Average vs Target Progress Comparison */}
        <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 p-lg flex flex-col justify-between">
          <h3 className="font-headline-md text-headline-md text-on-surface mb-lg">Average vs. Target</h3>
          
          <div className="flex-grow flex flex-col justify-center space-y-md my-sm">
            {skillsList.map((skillItem) => {
              const config = getSkillConfig(skillItem.skill);
              const currentVal = parseFloat(skillItem.currentAvg) || 0;
              const targetVal = parseFloat(skillItem.targetScore) || 7.0;
              
              const currentPct = Math.min(100, (currentVal / 9) * 100);
              const targetPct = Math.min(100, (targetVal / 9) * 100);

              return (
                <div key={skillItem.skill}>
                  <div className="flex justify-between mb-xs">
                    <span className="font-label-md text-label-md text-on-surface font-semibold">{config.label}</span>
                    <span className="font-label-md text-label-md text-outline">{currentVal.toFixed(1)} / {targetVal.toFixed(1)}</span>
                  </div>
                  <div className="relative w-full h-3 bg-surface-container-high rounded-full overflow-hidden">
                    {/* Target dashed line wrapper */}
                    <div 
                      className="absolute top-0 left-0 h-full bg-surface-variant rounded-full opacity-50 border-r-2 border-outline-variant border-dashed"
                      style={{ width: `${targetPct}%` }}
                    ></div>
                    {/* Current Score Fill bar */}
                    <div 
                      className={`absolute top-0 left-0 h-full ${config.progressBg} rounded-full`}
                      style={{ width: `${currentPct}%` }}
                    ></div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="mt-md flex items-center justify-center gap-md text-label-md text-outline">
            <div className="flex items-center gap-xs"><div className="w-3 h-3 bg-primary-container rounded-sm"></div> Current</div>
            <div className="flex items-center gap-xs"><div className="w-3 h-3 bg-surface-variant border border-outline-variant border-dashed rounded-sm"></div> Target</div>
          </div>
        </div>
      </div>

      {/* Performance Overview circular + weakness widgets */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-gutter mt-sm">
        {/* Overall Correctness Circle */}
        <div className="bg-surface-container-lowest rounded-xl shadow-sm p-md flex items-center justify-between border border-outline-variant/20 hover:shadow-md transition-shadow">
          <div>
            <h4 className="font-title-lg text-title-lg text-on-surface mb-xs">Overall Correctness</h4>
            <p className="font-body-md text-body-md text-on-surface-variant">Based on latest mock practices</p>
          </div>
          <div className="relative w-20 h-20 flex items-center justify-center flex-shrink-0">
            <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
              <path className="text-surface-container-high" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" stroke-width="3"></path>
              <path className="text-primary-container" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" stroke-dasharray={`${overallAccuracy}, 100`} stroke-linecap="round" stroke-width="3"></path>
            </svg>
            <span className="absolute font-headline-md text-headline-md text-primary-container font-extrabold">{overallAccuracy}%</span>
          </div>
        </div>

        {/* Weakest Area card */}
        <div className="bg-surface-container-lowest rounded-xl shadow-sm p-md flex flex-col justify-center border border-outline-variant/20 hover:shadow-md transition-shadow">
          <h4 className="font-title-lg text-title-lg text-on-surface mb-sm flex items-center gap-sm">
            <span className="material-symbols-outlined text-tertiary-container icon-fill">warning</span>
            Focus Area Needed
          </h4>
          <div className="flex justify-between items-center bg-surface-bright p-sm rounded-md border border-outline-variant/30">
            <span className="font-body-md text-body-md text-on-surface font-bold">{weakestType}</span>
            <span className="font-label-md text-label-md bg-error-container text-on-error-container px-2.5 py-1 rounded-full font-bold">
              {weakestAccuracy}% Accuracy
            </span>
          </div>
        </div>
      </div>

      {/* Recent Activity Table */}
      <div className="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/20 p-lg flex flex-col mt-sm">
        <div className="flex flex-wrap justify-between items-center gap-sm mb-lg">
          <h2 className="font-headline-md text-headline-md text-on-surface">Recent Activity</h2>
          <select
            value={historySkill}
            onChange={e => { setHistorySkill(e.target.value); setHistoryPage(0); }}
            className="bg-surface-container-low text-on-surface font-label-md text-label-md border-outline-variant rounded-md px-sm py-1 cursor-pointer focus:outline-none focus:ring-1 focus:ring-primary"
          >
            <option value="">All Skills</option>
            <option value="READING">Reading</option>
            <option value="WRITING">Writing</option>
            <option value="LISTENING">Listening</option>
          </select>
        </div>

        {history?.items?.length > 0 ? (
          <div className="overflow-x-auto w-full">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-outline-variant/30 text-outline font-label-md text-label-md pb-sm">
                  <th className="py-3 px-md font-bold">Skill</th>
                  <th className="py-3 px-md font-bold">Score</th>
                  <th className="py-3 px-md font-bold">Recorded Date</th>
                  <th className="py-3 px-md font-bold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/10">
                {history.items.map((item, index) => {
                  const config = getSkillConfig(item.skillType);
                  return (
                    <tr 
                      key={item.historyId || index}
                      onClick={() => navigate(`/history/${item.historyId}/review`)}
                      className="hover:bg-surface-container-low/30 cursor-pointer transition-colors duration-150 group"
                    >
                      <td className="py-4 px-md">
                        <span className={`inline-flex items-center gap-xs font-bold text-sm ${config.text}`}>
                          <span className="material-symbols-outlined text-[18px]">{config.icon}</span>
                          {config.label}
                        </span>
                      </td>
                      <td className="py-4 px-md font-bold text-on-surface text-sm">
                        Band {typeof item.score === 'number' ? item.score.toFixed(1) : item.score}
                      </td>
                      <td className="py-4 px-md text-on-surface-variant text-sm">
                        {item.recordedAt ? new Date(item.recordedAt).toLocaleDateString('en-US', {
                          day: 'numeric', month: 'short', year: 'numeric',
                          hour: '2-digit', minute: '2-digit'
                        }) : '—'}
                      </td>
                      <td className="py-4 px-md text-right">
                        <button className="text-primary hover:text-primary-container font-bold text-sm flex items-center gap-xs ml-auto group-hover:underline">
                          Review Answers
                          <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* Pagination Controls */}
            {history.totalPages > 1 && (
              <div className="flex justify-between items-center pt-lg border-t border-outline-variant/30 mt-md">
                <button 
                  disabled={historyPage === 0}
                  onClick={() => setHistoryPage(p => Math.max(0, p - 1))}
                  className="px-4 py-2 rounded-lg font-bold text-sm bg-surface-container-low hover:bg-surface-container-high border border-outline-variant/30 text-primary transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Previous
                </button>
                <span className="font-label-md text-label-md text-outline">
                  Page {historyPage + 1} of {history.totalPages}
                </span>
                <button 
                  disabled={historyPage >= history.totalPages - 1}
                  onClick={() => setHistoryPage(p => p + 1)}
                  className="px-4 py-2 rounded-lg font-bold text-sm bg-surface-container-low hover:bg-surface-container-high border border-outline-variant/30 text-primary transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-margin border border-dashed border-outline-variant/50 rounded-xl bg-surface-bright text-center">
            <span className="material-symbols-outlined text-[48px] text-outline-variant mb-sm">history</span>
            <p className="font-body-md text-body-md text-outline">No recent activity found for the selected skill filter.</p>
          </div>
        )}
      </div>
    </div>
  );
}

// Skeletons, Errors and Empty Views
function DashboardSkeleton() {
  return (
    <div className="animate-pulse space-y-lg">
      <div className="mb-lg">
        <div className="h-8 bg-surface-container-high rounded w-48 mb-xs"></div>
        <div className="h-5 bg-surface-container rounded w-72"></div>
      </div>

      <div className="grid grid-cols-12 gap-gutter">
        <div className="col-span-12 lg:col-span-4 bg-surface-container-low rounded-xl p-lg h-[220px] flex flex-col justify-between">
          <div>
            <div className="h-6 bg-surface-container rounded w-32 mb-sm"></div>
            <div className="h-16 bg-surface-container rounded w-24"></div>
          </div>
          <div className="h-10 bg-surface-container rounded w-full"></div>
        </div>

        <div className="col-span-12 lg:col-span-8 grid grid-cols-1 md:grid-cols-3 gap-gutter">
          {[1, 2, 3].map((i) => (
            <div key={i} className="bg-surface-container-low rounded-xl h-[220px] p-md flex flex-col justify-between">
              <div className="h-6 bg-surface-container rounded w-24"></div>
              <div className="space-y-sm">
                <div className="h-8 bg-surface-container rounded w-16"></div>
                <div className="h-2 bg-surface-container rounded w-full"></div>
              </div>
              <div className="h-4 bg-surface-container rounded w-12 self-end"></div>
            </div>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-gutter">
        <div className="bg-surface-container-low rounded-xl p-lg h-[340px]"></div>
        <div className="bg-surface-container-low rounded-xl p-lg h-[340px]"></div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-gutter">
        <div className="bg-surface-container-low rounded-xl p-md h-[120px]"></div>
        <div className="bg-surface-container-low rounded-xl p-md h-[120px]"></div>
      </div>
    </div>
  );
}

function DashboardError({ message, onRetry }) {
  return (
    <div className="bg-surface-container-lowest border border-error/30 rounded-xl p-xl shadow-sm text-center max-w-md mx-auto my-12 flex flex-col items-center gap-md animate-fade-in">
      <span className="material-symbols-outlined text-[64px] text-error icon-fill">error</span>
      <h2 className="font-headline-md text-headline-md text-on-surface">Something went wrong</h2>
      <p className="font-body-md text-body-md text-on-surface-variant">
        {message || 'Unable to load statistics. Please check your network and try again.'}
      </p>
      <button 
        onClick={onRetry}
        className="px-6 py-2.5 mt-sm rounded-lg font-bold text-sm bg-primary text-on-primary hover:bg-primary-container transition-all shadow-sm"
      >
        Try Again
      </button>
    </div>
  );
}

function DashboardEmptyState({ displayName, navigate }) {
  return (
    <div className="space-y-lg animate-fade-in">
      <header className="mb-lg">
        <h1 className="font-headline-md text-headline-md text-on-surface mb-xs">Welcome, {displayName}!</h1>
        <p className="font-body-lg text-body-lg text-on-surface-variant">Let's start your IELTS preparation journey.</p>
      </header>

      <div className="bg-surface-container-lowest border border-outline-variant/30 rounded-xl p-xl shadow-sm text-center max-w-2xl mx-auto my-12 flex flex-col items-center gap-md">
        <span className="material-symbols-outlined text-[64px] text-primary icon-fill animate-bounce">school</span>
        <h2 className="font-headline-md text-headline-md text-on-surface">Welcome to IELTS SmartPrep!</h2>
        <p className="font-body-lg text-body-lg text-on-surface-variant max-w-md">
          You haven't completed any practice tests yet. Start practicing Reading, Writing, Listening or take a Full Mock Test to receive dynamic feedback and progress analytics.
        </p>
        <div className="flex flex-wrap gap-md justify-center mt-sm">
          <button 
            onClick={() => navigate('/mock-tests')}
            className="px-6 py-3 rounded-lg font-bold text-sm bg-primary text-on-primary hover:bg-primary-container transition-all shadow-sm"
          >
            Take Full Mock Test
          </button>
          <button 
            onClick={() => navigate('/reading')}
            className="px-6 py-3 rounded-lg font-bold text-sm bg-surface-container-low hover:bg-surface-container-high text-primary border border-outline-variant/30 transition-all"
          >
            Reading Practice
          </button>
          <button 
            onClick={() => navigate('/listening')}
            className="px-6 py-3 rounded-lg font-bold text-sm bg-surface-container-low hover:bg-surface-container-high text-primary border border-outline-variant/30 transition-all"
          >
            Listening Practice
          </button>
        </div>
      </div>
    </div>
  );
}
