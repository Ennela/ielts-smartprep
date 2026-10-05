import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import speakingApi from '../api/speakingApi';
import { formatBand } from '../utils/formatBand';
import styles from '../styles/Speaking.module.css';

const CRITERIA = [
  { key: 'fluencyCoherence', band: 'fluencyBand', label: 'Fluency & Coherence' },
  { key: 'lexicalResource', band: 'lexicalBand', label: 'Lexical Resource' },
  { key: 'grammaticalRange', band: 'grammarBand', label: 'Grammatical Range & Accuracy' },
  { key: 'pronunciation', band: 'pronunciationBand', label: 'Pronunciation' },
];

export default function SpeakingResultPage() {
  const { submissionId } = useParams();
  const resultQuery = useQuery({
    queryKey: ['speaking', 'submission', submissionId],
    queryFn: () => speakingApi.getSubmission(submissionId),
    select: (res) => res.data?.data,
  });
  const result = resultQuery.data;

  // The recording needs the bearer token, so it is fetched as a Blob rather than linked.
  const [audioUrl, setAudioUrl] = useState(null);
  const [audioError, setAudioError] = useState(false);
  useEffect(() => {
    if (!result) return undefined;
    let url = null;
    let cancelled = false;
    speakingApi.getRecording(submissionId)
      .then((res) => {
        if (cancelled) return;
        url = URL.createObjectURL(res.data);
        setAudioUrl(url);
      })
      .catch(() => { if (!cancelled) setAudioError(true); });
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [result, submissionId]);

  if (resultQuery.isLoading) {
    return <div className="loading-spinner"><div className="spinner" /></div>;
  }
  if (resultQuery.isError || !result) {
    return (
      <div className={styles.page}>
        <div className="empty-state">
          <p>This result could not be loaded.</p>
          <Link className="btn btn-primary" to="/speaking">Back to Speaking</Link>
        </div>
      </div>
    );
  }

  const { prompt } = result;

  return (
    <div className={styles.page}>
      <Link to="/speaking" className="btn-back">
        <span aria-hidden="true" className="material-symbols-outlined" style={{ fontSize: 20 }}>arrow_back</span>
        Speaking
      </Link>
      <header className={styles.header}>
        <h1 className={styles.title}>Speaking Result</h1>
        <p className={styles.subtitle}>Part {prompt.part}: {prompt.topic}</p>
      </header>

      <section className={styles.stage} aria-labelledby="speaking-score">
        <h2 id="speaking-score" className="sr-only">Scores</h2>
        <div className={styles.scoreRow}>
          <div className={styles.overall}>
            <span className={styles.overallValue}>{formatBand(result.overallBand)}</span>
            <span className={styles.overallLabel}>Overall band</span>
          </div>
          <div className={styles.criteria}>
            {CRITERIA.map((c) => {
              const band = result[c.band];
              return (
                <div key={c.key}>
                  <div className={styles.criterionHead}>
                    <span>{c.label}</span>
                    <span>{formatBand(band)}</span>
                  </div>
                  <div className={styles.criterionBar} aria-hidden="true">
                    <div className={styles.criterionFill} style={{ width: `${(Number(band) / 9) * 100}%` }} />
                  </div>
                  {result.criteriaComments?.[c.key] && (
                    <p className={styles.criterionNote}>{result.criteriaComments[c.key]}</p>
                  )}
                </div>
              );
            })}
          </div>
        </div>
        {result.summary && <p>{result.summary}</p>}
      </section>

      <div className={`${styles.columns} ${styles.section}`}>
        <div className={styles.listCard}>
          <h3>What went well</h3>
          {result.strengths?.length ? (
            <ul>{result.strengths.map((s) => <li key={s}>{s}</li>)}</ul>
          ) : <p className={styles.hint}>—</p>}
        </div>
        <div className={styles.listCard}>
          <h3>What to work on</h3>
          {result.improvements?.length ? (
            <ul>{result.improvements.map((s) => <li key={s}>{s}</li>)}</ul>
          ) : <p className={styles.hint}>—</p>}
        </div>
      </div>

      <section className={styles.section} aria-labelledby="speaking-recording">
        <h2 id="speaking-recording" className={styles.sectionTitle}>Your answer</h2>
        {audioUrl ? (
          <audio className={styles.player} controls src={audioUrl} aria-label="Your recording" />
        ) : audioError ? (
          <p className={styles.hint}>The recording could not be loaded.</p>
        ) : (
          <p className={styles.hint}>Loading the recording…</p>
        )}
        {result.transcript && (
          <>
            <h3 className={styles.sectionTitle} style={{ fontSize: '1rem', marginTop: 16 }}>Transcript</h3>
            <p className={styles.transcript}>{result.transcript}</p>
          </>
        )}
      </section>

      <div className={`${styles.actions} ${styles.section}`}>
        <Link className="btn btn-primary" to={`/speaking/practice/${prompt.promptId}`}>Try this question again</Link>
        <Link className="btn btn-outline" to="/speaking">Choose another question</Link>
      </div>
    </div>
  );
}
