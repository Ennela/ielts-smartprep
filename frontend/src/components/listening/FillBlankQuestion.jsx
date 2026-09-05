/**
 * Gap-fill listening question. The blank is written as ___ in questionText.
 *
 * Shared by ListeningExamPage and by the listening section of MockTestSessionPage,
 * which render the blank differently on purpose:
 *
 *   'boxed'     (default) — the .fill-blank-input rule in index.css: a 180px
 *                           dashed box, used by the standalone listening exam.
 *   'underline'           — a narrower monospace rule on a transparent ground,
 *                           which is what the denser mock test layout uses.
 *
 * Known limitation, inherited from both originals: a questionText containing more
 * than one ___ renders several inputs bound to the same value and the same
 * onChange, so typing in the second overwrites the first. Unchanged here.
 */

const UNDERLINE_PARAGRAPH = {
  display: 'inline-flex', flexWrap: 'wrap', alignItems: 'center', gap: '6px', margin: 0,
};

const UNDERLINE_SEGMENT = {
  display: 'inline-flex', alignItems: 'center', gap: '4px',
};

const UNDERLINE_INPUT = {
  border: 'none', borderBottom: '2px solid var(--outline)',
  background: 'transparent', outline: 'none', padding: '2px 8px',
  fontFamily: 'var(--font-mono)', fontWeight: 600, color: 'var(--primary)',
  textAlign: 'center', width: '120px',
};

export default function FillBlankQuestion({ question, value, onChange, variant = 'boxed' }) {
  const underline = variant === 'underline';
  const parts = question.questionText.split('___');
  return (
    <div className={underline ? undefined : 'fill-blank-question'}>
      <p className="question-text" style={underline ? UNDERLINE_PARAGRAPH : undefined}>
        {parts.map((part, i) => (
          <span key={i} style={underline ? UNDERLINE_SEGMENT : undefined}>
            {part}
            {i < parts.length - 1 && (
              <input type="text" className="fill-blank-input" value={value}
                onChange={e => onChange(e.target.value)}
                placeholder={underline ? 'your answer...' : 'your answer'}
                style={underline ? UNDERLINE_INPUT : undefined} />
            )}
          </span>
        ))}
      </p>
    </div>
  );
}
