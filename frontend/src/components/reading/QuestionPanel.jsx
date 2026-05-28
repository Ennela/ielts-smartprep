import { useReading } from '../../context/ReadingContext';

export default function QuestionPanel({ questions }) {
  const { answers, setAnswer, isSubmitted } = useReading();

  if (!questions || questions.length === 0) return null;

  return (
    <div className="question-panel" id="question-panel">
      <h3 className="question-panel-title">Questions</h3>
      {questions.map((q, idx) => (
        <div key={q.questionId} className="question-item">
          <div className="question-number">Question {q.orderIndex || idx + 1}</div>
          <p className="question-text">{q.questionText}</p>

          {q.questionType === 'MCQ' ? (
            <McqOptions
              question={q}
              selected={answers[q.questionId]}
              onChange={(val) => setAnswer(q.questionId, val)}
              disabled={isSubmitted}
            />
          ) : (
            <TfngOptions
              questionId={q.questionId}
              selected={answers[q.questionId]}
              onChange={(val) => setAnswer(q.questionId, val)}
              disabled={isSubmitted}
            />
          )}
        </div>
      ))}
    </div>
  );
}

function McqOptions({ question, selected, onChange, disabled }) {
  const options = [
    { key: 'A', text: question.optionA },
    { key: 'B', text: question.optionB },
    { key: 'C', text: question.optionC },
    { key: 'D', text: question.optionD },
  ].filter((o) => o.text);

  return (
    <div className="mcq-options">
      {options.map((opt) => (
        <label
          key={opt.key}
          className={`mcq-option ${selected === opt.key ? 'selected' : ''}`}
        >
          <input
            type="radio"
            name={`q-${question.questionId}`}
            value={opt.key}
            checked={selected === opt.key}
            onChange={() => onChange(opt.key)}
            disabled={disabled}
          />
          <span className="mcq-radio-mark"></span>
          <span className="mcq-label">{opt.text}</span>
        </label>
      ))}
    </div>
  );
}

function TfngOptions({ questionId, selected, onChange, disabled }) {
  const options = ['TRUE', 'FALSE', 'NOT GIVEN'];

  return (
    <div className="tfng-options">
      {options.map((opt) => (
        <button
          key={opt}
          type="button"
          className={`tfng-btn ${selected === opt ? 'active' : ''}`}
          onClick={() => onChange(opt)}
          disabled={disabled}
          id={`tfng-${questionId}-${opt.replace(' ', '-').toLowerCase()}`}
        >
          {opt}
        </button>
      ))}
    </div>
  );
}
