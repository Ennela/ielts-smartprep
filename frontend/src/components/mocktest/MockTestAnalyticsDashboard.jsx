import { useNavigate } from 'react-router-dom';
import ProgressTimelineChart from './ProgressTimelineChart';
import styles from '../../styles/MockTestAnalytics.module.css';

const SKILL_LABEL = { LISTENING: 'Listening', READING: 'Reading', WRITING: 'Writing', OVERALL: 'Overall' };

const LEVEL_LABEL = { WEAK: 'Weak', DEVELOPING: 'Developing', STRONG: 'Strong' };

// Status colours carry a text chip beside them everywhere they appear, so a level is
// never communicated by colour alone.
const LEVEL_COLOR = {
  WEAK: 'var(--error)',
  DEVELOPING: 'var(--tertiary-container)',
  STRONG: 'var(--secondary)',
};

const fmtBand = (v) => (v === null || v === undefined ? '—' : Number(v).toFixed(1));
const fmtPct = (v) => (v === null || v === undefined ? '—' : `${Math.round(Number(v))}%`);

function LevelChip({ level }) {
  if (!level) {
    return <span className={`${styles['level-chip']} ${styles['level-pending']}`}>Pending</span>;
  }
  return (
    <span className={`${styles['level-chip']} ${styles[`level-${level.toLowerCase()}`]}`}>
      {LEVEL_LABEL[level] || level}
    </span>
  );
}

function Bar({ value, max, level, target }) {
  const pct = value === null || value === undefined ? 0 : Math.min(100, (Number(value) / max) * 100);
  const targetPct = target === null || target === undefined ? null : Math.min(100, (Number(target) / max) * 100);
  return (
    <div className={styles['bar-track']} aria-hidden="true">
      <div className={styles['bar-fill']} style={{ width: `${pct}%`, background: LEVEL_COLOR[level] || 'var(--outline)' }} />
      {targetPct !== null && <div className={styles['bar-target']} style={{ left: `calc(${targetPct}% - 1px)` }} />}
    </div>
  );
}

function GroupList({ groups, emptyText }) {
  if (!groups || groups.length === 0) {
    return <p className={styles.empty}>{emptyText}</p>;
  }
  return (
    <ul className={styles['group-list']}>
      {groups.map((g) => (
        <li key={g.key} className={styles['group-item']}>
          <span className={styles['group-label']}>
            {g.label}{' '}
            <span className={styles['group-count']}>
              {g.correct}/{g.total} · {fmtPct(g.accuracy)}
            </span>
          </span>
          <LevelChip level={g.level} />
          <div className={styles['group-bar']}>
            <Bar value={g.accuracy} max={100} level={g.level} />
          </div>
        </li>
      ))}
    </ul>
  );
}

function CriterionList({ criteria }) {
  if (!criteria || criteria.length === 0) {
    return <p className={styles.empty}>Writing criteria appear once grading has finished.</p>;
  }
  return (
    <ul className={styles['group-list']}>
      {criteria.map((c) => (
        <li key={c.key} className={styles['group-item']}>
          <span className={styles['group-label']}>
            {c.label} <span className={styles['group-count']}>Band {fmtBand(c.band)}</span>
          </span>
          <LevelChip level={c.level} />
          <div className={styles['group-bar']}>
            <Bar value={c.band} max={9} level={c.level} />
          </div>
        </li>
      ))}
    </ul>
  );
}

function TrendCard({ trend }) {
  const delta = trend.delta === null || trend.delta === undefined ? null : Number(trend.delta);
  let deltaText = '';
  let deltaClass = styles['trend-flat'];
  if (trend.direction === 'FIRST_ATTEMPT') {
    deltaText = 'First attempt';
  } else if (trend.direction === 'PENDING') {
    deltaText = 'Pending';
  } else if (delta !== null) {
    if (delta > 0) {
      deltaText = `▲ +${delta.toFixed(1)} vs last`;
      deltaClass = styles['trend-up'];
    } else if (delta < 0) {
      deltaText = `▼ ${delta.toFixed(1)} vs last`;
      deltaClass = styles['trend-down'];
    } else {
      deltaText = '= same as last';
    }
  }
  return (
    <div className={styles['trend-card']}>
      <div className={styles['trend-skill']}>{SKILL_LABEL[trend.skill] || trend.skill}</div>
      <div className={styles['trend-value']}>{fmtBand(trend.current)}</div>
      <div className={`${styles['trend-delta']} ${deltaClass}`}>{deltaText}</div>
    </div>
  );
}

/**
 * The result dashboard: five sections, each answering one question, in reading order.
 *
 * @param analytics   MockTestAnalyticsResponse from GET /mock-tests/submissions/{id}/analytics
 * @param onReviewSkill  called with 'listening' | 'reading' | 'writing' to open that
 *                       skill's detailed question review lower on the page
 */
export default function MockTestAnalyticsDashboard({ analytics, onReviewSkill }) {
  const navigate = useNavigate();
  if (!analytics) return null;

  const { summary, skills = [], listening, reading, writing, weaknesses = [], progress, recommendations = [] } = analytics;
  const weakest = new Set(summary?.weakestSkills || []);
  const grading = analytics.status === 'GRADING';

  const bandCards = [
    { skill: 'LISTENING', band: summary?.listeningBand, meta: listening ? `${listening.correct}/${listening.total} correct` : '' },
    { skill: 'READING', band: summary?.readingBand, meta: reading ? `${reading.correct}/${reading.total} correct` : '' },
    { skill: 'WRITING', band: summary?.writingBand, meta: grading ? 'Grading in progress' : writing ? 'Task 2 weighted double' : 'Not graded' },
  ];

  const topWeaknesses = weaknesses.slice(0, 3);

  return (
    <div className={styles.dashboard} data-testid="mock-analytics">
      {/* ── 1. Summary ── */}
      <section className={styles.section} aria-labelledby="ra-summary">
        <h2 id="ra-summary" className={styles['section-title']}>Summary</h2>
        <p className={styles['section-question']}>How did I score?</p>
        <div className={styles['summary-grid']}>
          <div className={styles['overall-card']}>
            <div className={styles['overall-band']}>{grading ? '…' : fmtBand(summary?.overallBand)}</div>
            <div className={styles['overall-label']}>Overall band (3 skills)</div>
            <p className={styles['overall-note']}>{summary?.overallNote}</p>
          </div>
          <div className={styles['band-grid']}>
            {bandCards.map((c) => (
              <div
                key={c.skill}
                className={`${styles['band-card']} ${weakest.has(c.skill) ? styles.weakest : ''}`}
                data-testid={`band-${c.skill.toLowerCase()}`}
              >
                <div className={styles['band-skill']}>{SKILL_LABEL[c.skill]}</div>
                <div className={`${styles['band-value']} ${c.band === null || c.band === undefined ? styles.muted : ''}`}>
                  {c.band === null || c.band === undefined ? (grading ? 'Pending' : '—') : fmtBand(c.band)}
                </div>
                <div className={styles['band-meta']}>{c.meta}</div>
                {weakest.has(c.skill) && <span className={styles['weakest-tag']}>Pulling score down</span>}
              </div>
            ))}
            <div className={`${styles['band-card']} ${styles.excluded}`} data-testid="band-speaking">
              <div className={styles['band-skill']}>Speaking</div>
              <div className={`${styles['band-value']} ${styles.muted}`}>Not included</div>
              <div className={styles['band-meta']}>{summary?.speakingNote || 'Speaking: Not included'}</div>
            </div>
          </div>
        </div>
      </section>

      {/* ── 2. Skill breakdown ── */}
      <section className={styles.section} aria-labelledby="ra-skills">
        <h2 id="ra-skills" className={styles['section-title']}>Skill breakdown</h2>
        <p className={styles['section-question']}>How far is each skill from my target?</p>
        <div className={styles['skill-list']}>
          {skills.map((s) => {
            const gap = s.gapToTarget === null || s.gapToTarget === undefined ? null : Number(s.gapToTarget);
            return (
              <div key={s.skill} className={styles['skill-row']}>
                <div className={styles['skill-head']}>
                  <span className={styles['skill-name']}>
                    {SKILL_LABEL[s.skill]} <LevelChip level={s.graded ? s.level : null} />
                  </span>
                  <span className={styles['skill-figures']}>
                    {s.graded ? `Band ${fmtBand(s.band)}` : 'Grading…'}
                    {s.accuracy !== null && s.accuracy !== undefined && ` · ${s.correct}/${s.total} (${fmtPct(s.accuracy)})`}
                    {` · Target ${fmtBand(s.targetBand)}`}
                    {gap !== null && (gap > 0 ? ` · Need +${gap.toFixed(1)}` : ' · Target met')}
                  </span>
                </div>
                <Bar value={s.graded ? s.band : null} max={9} level={s.level} target={s.targetBand} />
              </div>
            );
          })}
        </div>
      </section>

      {/* ── 3. Weakness ── */}
      <section className={styles.section} aria-labelledby="ra-weakness">
        <h2 id="ra-weakness" className={styles['section-title']}>Weakness</h2>
        <p className={styles['section-question']}>
          {topWeaknesses.length === 0
            ? 'No weak areas detected in this sitting.'
            : `Weakest first: ${topWeaknesses.map((w) => `${SKILL_LABEL[w.skill]} – ${w.label}`).join('; ')}.`}
        </p>
        <div className={styles['weakness-grid']}>
          <div className={styles['weakness-column']}>
            <h4>Listening by part</h4>
            <GroupList groups={listening?.byPart} emptyText="No listening questions in this test." />
            {listening?.byQuestionType?.length > 0 && (
              <>
                <h4 style={{ marginTop: 16 }}>Listening by question type</h4>
                <GroupList groups={listening.byQuestionType} />
              </>
            )}
            {listening?.wrongQuestions?.length > 0 && onReviewSkill && (
              <button type="button" className="btn btn-outline btn-sm" style={{ marginTop: 12 }} onClick={() => onReviewSkill('listening')}>
                Review {listening.wrongQuestions.length} wrong answers
              </button>
            )}
          </div>
          <div className={styles['weakness-column']}>
            <h4>Reading by question type</h4>
            <GroupList groups={reading?.byQuestionType} emptyText="No reading questions in this test." />
            {reading?.wrongQuestions?.length > 0 && onReviewSkill && (
              <button type="button" className="btn btn-outline btn-sm" style={{ marginTop: 12 }} onClick={() => onReviewSkill('reading')}>
                Review {reading.wrongQuestions.length} wrong answers
              </button>
            )}
          </div>
          <div className={styles['weakness-column']}>
            <h4>Writing by criterion</h4>
            <CriterionList criteria={writing?.criteria} />
            {writing && onReviewSkill && (
              <button type="button" className="btn btn-outline btn-sm" style={{ marginTop: 12 }} onClick={() => onReviewSkill('writing')}>
                Read essay feedback
              </button>
            )}
          </div>
        </div>
      </section>

      {/* ── 4. Progress ── */}
      <section className={styles.section} aria-labelledby="ra-progress">
        <h2 id="ra-progress" className={styles['section-title']}>Progress</h2>
        <p className={styles['section-question']}>
          {progress?.attemptNumber > 0
            ? `Attempt ${progress.attemptNumber} of ${progress.totalAttempts} completed. Am I improving?`
            : `${progress?.totalAttempts || 0} completed attempt${progress?.totalAttempts === 1 ? '' : 's'} so far. Am I improving?`}
        </p>
        <div className={styles['trend-row']}>
          {(progress?.trends || []).map((t) => (
            <TrendCard key={t.skill} trend={t} />
          ))}
        </div>
        <ProgressTimelineChart timeline={progress?.timeline || []} />
        {progress?.timeline?.length > 0 && (
          <div className={styles['timeline-table-wrap']}>
            <table className={styles['timeline-table']}>
              <thead>
                <tr>
                  <th>Attempt</th>
                  <th>Date</th>
                  <th>Listening</th>
                  <th>Reading</th>
                  <th>Writing</th>
                  <th>Overall</th>
                </tr>
              </thead>
              <tbody>
                {progress.timeline.map((p) => (
                  <tr key={p.submissionId} className={p.current ? styles.current : ''}>
                    <td>
                      {p.current ? (
                        `Attempt ${p.attemptNumber} (this one)`
                      ) : (
                        <a href={`/mock-tests/result/${p.submissionId}`} onClick={(e) => { e.preventDefault(); navigate(`/mock-tests/result/${p.submissionId}`); }}>
                          Attempt {p.attemptNumber}
                        </a>
                      )}
                    </td>
                    <td>{p.submittedAt ? new Date(p.submittedAt).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'}</td>
                    <td>{fmtBand(p.listeningBand)}</td>
                    <td>{fmtBand(p.readingBand)}</td>
                    <td>{fmtBand(p.writingBand)}</td>
                    <td>{fmtBand(p.overallBand)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ── 5. Recommended next step ── */}
      <section className={styles.section} aria-labelledby="ra-next">
        <h2 id="ra-next" className={styles['section-title']}>Recommended next step</h2>
        <p className={styles['section-question']}>What should I practise next?</p>
        <div className={styles['rec-grid']}>
          {recommendations.map((r, i) => (
            <div key={`${r.actionPath}-${i}`} className={styles['rec-card']}>
              <p className={styles['rec-title']}>{r.title}</p>
              <p className={styles['rec-reason']}>{r.reason}</p>
              <button type="button" className={`btn btn-primary btn-sm ${styles['rec-action']}`} onClick={() => navigate(r.actionPath)}>
                Start
              </button>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
