import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import speakingApi from '../api/speakingApi';
import { formatBand } from '../utils/formatBand';
import styles from '../styles/Speaking.module.css';

const PARTS = [
  { part: 1, label: 'Part 1', note: 'Short questions about yourself and familiar topics. Answer each one in a few sentences.' },
  { part: 2, label: 'Part 2', note: 'A cue card: one minute to prepare, then speak for up to two minutes.' },
  { part: 3, label: 'Part 3', note: 'A discussion of wider issues linked to the topic. Give reasons and examples.' },
];

const formatDuration = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

export default function SpeakingPage() {
  const [part, setPart] = useState(1);

  const promptsQuery = useQuery({
    queryKey: ['speaking', 'prompts', part],
    queryFn: () => speakingApi.getPrompts(part),
    select: (res) => res.data?.data || [],
  });
  const historyQuery = useQuery({
    queryKey: ['speaking', 'history'],
    queryFn: () => speakingApi.getHistory(0, 5),
    select: (res) => res.data?.data?.content || [],
  });

  const current = PARTS.find((p) => p.part === part);
  const prompts = promptsQuery.data || [];
  const history = historyQuery.data || [];

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>Speaking Practice</h1>
        <p className={styles.subtitle}>Record an answer and get an AI band score for each Speaking criterion.</p>
      </header>

      <div className={styles.tabs} role="tablist" aria-label="Speaking parts">
        {PARTS.map((p) => (
          <button
            key={p.part}
            type="button"
            role="tab"
            aria-selected={part === p.part}
            className={`${styles.tab} ${part === p.part ? styles.tabActive : ''}`}
            onClick={() => setPart(p.part)}
          >
            {p.label}
          </button>
        ))}
      </div>
      <p className={styles.partNote}>{current.note}</p>

      {promptsQuery.isLoading ? (
        <div className="loading-spinner"><div className="spinner" /></div>
      ) : promptsQuery.isError ? (
        <div className="error-msg" role="alert">
          Could not load the questions.{' '}
          <button type="button" className="btn btn-sm btn-outline" onClick={() => promptsQuery.refetch()}>Retry</button>
        </div>
      ) : prompts.length === 0 ? (
        <div className="empty-state"><p>No {current.label} questions yet.</p></div>
      ) : (
        <div className={styles.grid}>
          {prompts.map((prompt) => (
            <article key={prompt.promptId} className={styles.promptCard}>
              <h2 className={styles.promptTopic}>{prompt.topic}</h2>
              <p className={styles.promptPreview}>
                {prompt.part === 2 ? prompt.questions[0] : `${prompt.questions.length} questions · ${prompt.questions[0]}`}
              </p>
              <div className={styles.promptMeta}>
                {prompt.prepSeconds > 0 && <span>{prompt.prepSeconds / 60} min prep</span>}
                <span>
                  {prompt.part === 2
                    ? `Up to ${prompt.maxSpeakSeconds / 60} min speaking`
                    : `Up to ${prompt.maxSpeakSeconds} s per answer`}
                </span>
              </div>
              <Link className="btn btn-primary btn-sm" to={`/speaking/practice/${prompt.promptId}`}>
                Practise
              </Link>
            </article>
          ))}
        </div>
      )}

      <section className={styles.section} aria-labelledby="speaking-recent">
        <h2 id="speaking-recent" className={styles.sectionTitle}>Your recent answers</h2>
        {historyQuery.isLoading ? (
          <div className="loading-spinner"><div className="spinner" /></div>
        ) : history.length === 0 ? (
          <p className={styles.hint}>Your graded answers will appear here.</p>
        ) : (
          <div className={styles.historyList}>
            {history.map((item) => (
              <Link key={item.submissionId} to={`/speaking/result/${item.submissionId}`} className={styles.historyRow}>
                <span>
                  <strong>Part {item.part}: {item.topic}</strong>
                  <span className={styles.historyMeta}>
                    {' '}· {formatDuration(item.durationSeconds)} · {new Date(item.submittedAt).toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' })}
                  </span>
                </span>
                <span className={styles.band}>Band {formatBand(item.overallBand)}</span>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
