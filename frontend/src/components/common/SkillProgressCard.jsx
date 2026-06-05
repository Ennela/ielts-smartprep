const SKILL_ICONS = {
  READING: 'menu_book',
  WRITING: 'edit_note',
  LISTENING: 'headphones',
};

const SKILL_COLOR_CLASS = {
  READING: 'reading',
  WRITING: 'writing',
  LISTENING: 'listening',
};

import styles from '../../styles/Dashboard.module.css';

export default function SkillProgressCard({ skill, currentAvg, targetScore, progressPercent, totalTests, gap, status }) {
  const pct = Math.min(100, progressPercent || 0);
  const key = skill?.toUpperCase() || 'READING';
  const colorKey = SKILL_COLOR_CLASS[key] || 'reading';
  const icon = SKILL_ICONS[key] || 'school';
  const band = currentAvg?.toFixed ? currentAvg.toFixed(1) : '0.0';

  return (
    <div className={styles['skill-progress-card']} id={`spc-${skill?.toLowerCase()}`}>
      {/* Header: icon + band score */}
      <div className={styles['spc-header']}>
        <div className={styles['spc-icon-wrap']}>
          <span className="material-symbols-outlined">{icon}</span>
        </div>
        <span className={`${styles['spc-band-score']} ${styles[`spc-band-${colorKey}`]}`}>{band}</span>
      </div>

      {/* Skill name + test count */}
      <p className={styles['spc-skill-name']}>
        {skill ? skill.charAt(0) + skill.slice(1).toLowerCase() : ''}
      </p>
      <p className={styles['spc-count']}>
        {totalTests || 0} tests &middot; Target {targetScore?.toFixed ? targetScore.toFixed(1) : '6.5'}
      </p>

      {/* Progress bar */}
      <div className={styles['spc-progress-label']}>
        <span style={{ color: 'var(--on-surface-variant)' }}>Progress</span>
        <span className={`${styles['spc-pct']} ${styles[`spc-pct-${colorKey}`]}`}>{pct}%</span>
      </div>
      <div className={styles['spc-progress-bar']}>
        <div
          className={`${styles['spc-progress-fill']} ${styles[`spc-fill-${colorKey}`]}`}
          style={{ width: `${pct}%` }}
        />
      </div>

      {/* Footer gap info */}
      {status !== 'TARGET_MET' && gap > 0 && (
        <p style={{ marginTop: 8, fontSize: '0.8rem', color: 'var(--tertiary)' }}>
          Need {gap?.toFixed ? gap.toFixed(1) : '0'} more
        </p>
      )}
    </div>
  );
}
