export default function SkillProgressCard({ skill, currentAvg, targetScore, progressPercent, totalTests, gap, status }) {
  const getProgressColor = (pct) => {
    if (pct >= 80) return 'var(--color-success)';
    if (pct >= 50) return 'var(--color-warning)';
    return 'var(--color-error)';
  };

  const skillIcons = {
    READING: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
        <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
      </svg>
    ),
    WRITING: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />
        <path d="m15 5 4 4" />
      </svg>
    ),
    LISTENING: (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
        <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
      </svg>
    ),
  };

  const pct = Math.min(100, progressPercent || 0);

  return (
    <div className="spc card" id={`spc-${skill?.toLowerCase()}`}>
      <div className="spc-header">
        <span className="spc-icon">{skillIcons[skill]}</span>
        <span className="spc-skill-name">{skill}</span>
        <span className={`spc-status ${status === 'TARGET_MET' ? 'status-met' : 'status-progress'}`}>
          {status === 'TARGET_MET' ? 'Target Met' : 'In Progress'}
        </span>
      </div>

      <div className="spc-scores">
        <div className="spc-current">
          <span className="spc-score-value">{currentAvg?.toFixed ? currentAvg.toFixed(1) : '0.0'}</span>
          <span className="spc-score-label">Current</span>
        </div>
        <div className="spc-separator">/</div>
        <div className="spc-target">
          <span className="spc-score-value">{targetScore?.toFixed ? targetScore.toFixed(1) : '6.5'}</span>
          <span className="spc-score-label">Target</span>
        </div>
      </div>

      <div className="spc-progress-bar">
        <div
          className="spc-progress-fill"
          style={{
            width: `${pct}%`,
            background: getProgressColor(pct),
          }}
        />
      </div>

      <div className="spc-footer">
        <span>{totalTests || 0} tests taken</span>
        {status !== 'TARGET_MET' && (
          <span className="spc-gap">Gap: {gap?.toFixed ? gap.toFixed(1) : '0.0'}</span>
        )}
      </div>
    </div>
  );
}
