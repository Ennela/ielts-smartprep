/**
 * Multiple-choice listening question.
 *
 * Shared by ListeningExamPage and by the listening section of MockTestSessionPage.
 * Both are fed ListeningPartResponse.QuestionDto, so both can receive structured
 * options; the fallback below exists only for questions whose choices are still
 * embedded as lines of questionText.
 */
export default function McqQuestion({ question, value, onChange }) {
  if (question.options && question.options.length > 0) {
    return (
      <div className="mcq-question">
        <p className="question-text">{question.questionText}</p>
        <div className="mcq-options">
          {question.options.map((opt, i) => {
            const letter = opt.label;
            return (
              <label key={opt.optionId || i} className={`mcq-option ${value === letter ? 'selected' : ''}`}>
                <input type="radio" name={`q-${question.questionId}`} value={letter}
                  checked={value === letter} onChange={() => onChange(letter)} />
                <span className="mcq-letter">{letter}</span>
                <span className="mcq-label">{opt.content}</span>
              </label>
            );
          })}
        </div>
      </div>
    );
  }

  const lines = question.questionText.split('\n');
  const stem = lines[0];
  const options = lines.slice(1).filter(l => l.trim());
  return (
    <div className="mcq-question">
      <p className="question-text">{stem}</p>
      <div className="mcq-options">
        {options.map((opt, i) => {
          const letter = opt.trim().charAt(0);
          return (
            <label key={i} className={`mcq-option ${value === letter ? 'selected' : ''}`}>
              <input type="radio" name={`q-${question.questionId}`} value={letter}
                checked={value === letter} onChange={() => onChange(letter)} />
              <span className="mcq-letter">{letter}</span>
              <span className="mcq-label">{opt.trim().substring(2).trim()}</span>
            </label>
          );
        })}
      </div>
    </div>
  );
}
