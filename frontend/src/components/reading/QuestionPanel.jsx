import { useMemo, useState } from 'react';
import { useReading } from '../../context/ReadingContext';

// ============================================================
// Main QuestionPanel — groups questions by groupId
// ============================================================
export default function QuestionPanel({ questions }) {
  const { answers, setAnswer, isSubmitted } = useReading();

  if (!questions || questions.length === 0) return null;

  // Group questions by groupId, preserving order
  const groups = useMemo(() => {
    const groupMap = new Map();
    questions.forEach((q) => {
      const gid = q.groupId || 0;
      if (!groupMap.has(gid)) {
        groupMap.set(gid, {
          groupId: gid,
          groupLabel: q.groupLabel || '',
          groupType: q.questionType,
          groupContext: q.groupContext || null,
          wordLimit: q.wordLimit || null,
          optionsJson: q.optionsJson || null,
          questions: [],
        });
      }
      groupMap.get(gid).questions.push(q);
    });
    return Array.from(groupMap.values());
  }, [questions]);

  return (
    <div className="question-panel" id="question-panel">
      <h3 className="question-panel-title">Questions</h3>
      {groups.map((group) => (
        <QuestionGroup
          key={group.groupId}
          group={group}
          answers={answers}
          setAnswer={setAnswer}
          disabled={isSubmitted}
        />
      ))}
    </div>
  );
}

// ============================================================
// QuestionGroup — renders a group label + context + questions
// ============================================================
function QuestionGroup({ group, answers, setAnswer, disabled }) {
  // Parse group-level options once
  const groupOptions = useMemo(() => {
    if (!group.optionsJson) return null;
    try {
      return JSON.parse(group.optionsJson);
    } catch {
      return null;
    }
  }, [group.optionsJson]);

  return (
    <div className="question-group">
      {group.groupLabel && (
        <div className="group-label">{group.groupLabel}</div>
      )}

      {/* Word limit badge */}
      {group.wordLimit && (
        <div className="word-limit-badge">
          Maximum {group.wordLimit} words
        </div>
      )}

      {/* Summary/Note context block with inline blanks */}
      {group.groupContext && group.groupType === 'SUMMARY_COMPLETION' && (
        <SummaryBlock
          context={group.groupContext}
          questions={group.questions}
          answers={answers}
          setAnswer={setAnswer}
          disabled={disabled}
          groupOptions={groupOptions}
        />
      )}

      {/* Render individual questions */}
      {group.questions.map((q, idx) => {
        // For SUMMARY_COMPLETION with context, blanks are in the SummaryBlock
        if (group.groupContext && group.groupType === 'SUMMARY_COMPLETION') {
          return null; // Already rendered inline
        }

        return (
          <div key={q.questionId} className="question-item">
            <div className="question-number">Question {q.orderIndex || idx + 1}</div>
            <p className="question-text">{q.questionText}</p>

            <QuestionInput
              question={q}
              selected={answers[q.questionId]}
              onChange={(val) => setAnswer(q.questionId, val)}
              disabled={disabled}
              groupOptions={groupOptions}
            />
          </div>
        );
      })}
    </div>
  );
}

// ============================================================
// QuestionInput — routes to the correct input component by type
// ============================================================
function QuestionInput({ question, selected, onChange, disabled, groupOptions }) {
  switch (question.questionType) {
    case 'MCQ':
      return (
        <McqOptions
          question={question}
          selected={selected}
          onChange={onChange}
          disabled={disabled}
        />
      );
    case 'TFNG':
      return (
        <TfngOptions
          questionId={question.questionId}
          selected={selected}
          onChange={onChange}
          disabled={disabled}
        />
      );
    case 'YNNG':
      return (
        <YnngOptions
          questionId={question.questionId}
          selected={selected}
          onChange={onChange}
          disabled={disabled}
        />
      );
    case 'SENTENCE_COMPLETION':
    case 'SUMMARY_COMPLETION':
      return (
        <CompletionInput
          questionId={question.questionId}
          selected={selected}
          onChange={onChange}
          disabled={disabled}
          wordLimit={question.wordLimit}
          groupOptions={groupOptions}
        />
      );
    case 'MATCHING_HEADINGS':
    case 'MATCHING_INFORMATION':
    case 'MATCHING_FEATURES':
    case 'MATCHING_SENTENCE_ENDINGS':
      return (
        <MatchingSelect
          questionId={question.questionId}
          selected={selected}
          onChange={onChange}
          disabled={disabled}
          options={groupOptions}
          questionType={question.questionType}
        />
      );
    default:
      return <p className="text-muted">Unsupported question type</p>;
  }
}

// ============================================================
// MCQ — Multiple Choice (radio buttons A/B/C/D)
// ============================================================
function McqOptions({ question, selected, onChange, disabled }) {
  let options = [];
  if (question.options && question.options.length > 0) {
    options = question.options.map(opt => ({
      key: opt.label,
      text: opt.content
    }));
  } else {
    options = [
      { key: 'A', text: question.optionA },
      { key: 'B', text: question.optionB },
      { key: 'C', text: question.optionC },
      { key: 'D', text: question.optionD },
    ].filter((o) => o.text);
  }

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
          <span className="mcq-key">{opt.key}.</span>
          <span className="mcq-label">{opt.text}</span>
        </label>
      ))}
    </div>
  );
}

// ============================================================
// TFNG — True / False / Not Given
// ============================================================
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

// ============================================================
// YNNG — Yes / No / Not Given
// ============================================================
function YnngOptions({ questionId, selected, onChange, disabled }) {
  const options = ['YES', 'NO', 'NOT GIVEN'];

  return (
    <div className="tfng-options ynng-options">
      {options.map((opt) => (
        <button
          key={opt}
          type="button"
          className={`tfng-btn ${selected === opt ? 'active' : ''}`}
          onClick={() => onChange(opt)}
          disabled={disabled}
          id={`ynng-${questionId}-${opt.replace(' ', '-').toLowerCase()}`}
        >
          {opt}
        </button>
      ))}
    </div>
  );
}

// ============================================================
// CompletionInput — text input for Sentence/Summary Completion
// ============================================================
function CompletionInput({ questionId, selected, onChange, disabled, wordLimit, groupOptions }) {
  // If there's a word bank (groupOptions), render as a select dropdown
  if (groupOptions && groupOptions.length > 0) {
    return (
      <select
        className="matching-select completion-select"
        value={selected || ''}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        id={`completion-${questionId}`}
      >
        <option value="">— Select word —</option>
        {groupOptions.map((opt, idx) => (
          <option key={idx} value={opt}>{opt}</option>
        ))}
      </select>
    );
  }

  // Otherwise: free-text input
  const [localVal, setLocalVal] = useState(selected || '');

  const handleBlur = () => {
    onChange(localVal.trim());
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      onChange(localVal.trim());
    }
  };

  return (
    <div className="completion-input-wrapper">
      <input
        type="text"
        className="completion-input"
        value={localVal}
        onChange={(e) => setLocalVal(e.target.value)}
        onBlur={handleBlur}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        placeholder={wordLimit ? `Max ${wordLimit} words` : 'Enter answer...'}
        id={`completion-${questionId}`}
      />
    </div>
  );
}

// ============================================================
// MatchingSelect — dropdown for all matching types
// ============================================================
function MatchingSelect({ questionId, selected, onChange, disabled, options, questionType }) {
  const parsedOptions = useMemo(() => {
    if (!options) return [];
    if (Array.isArray(options)) return options;
    try {
      return JSON.parse(options);
    } catch {
      return [];
    }
  }, [options]);

  const placeholderText = {
    MATCHING_HEADINGS: '— Select heading —',
    MATCHING_INFORMATION: '— Select section —',
    MATCHING_FEATURES: '— Select —',
    MATCHING_SENTENCE_ENDINGS: '— Select ending —',
  }[questionType] || '— Select —';

  return (
    <select
      className="matching-select"
      value={selected || ''}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      id={`matching-${questionId}`}
    >
      <option value="">{placeholderText}</option>
      {parsedOptions.map((opt, idx) => {
        // For matching types, the value to submit depends on the type
        let submitValue = opt;
        if (questionType === 'MATCHING_HEADINGS') {
          // Extract roman numeral: "iv. The impact of..." -> "iv"
          const match = opt.match(/^([ivxlcdm]+)\./i);
          if (match) submitValue = match[1].toLowerCase();
        } else if (questionType === 'MATCHING_INFORMATION' || questionType === 'MATCHING_FEATURES') {
          // Extract letter: "A. Dr. Smith" -> "A"  or just "A" -> "A"
          const match = opt.match(/^([A-Z])/i);
          if (match) submitValue = match[1].toUpperCase();
        } else if (questionType === 'MATCHING_SENTENCE_ENDINGS') {
          // Extract letter: "A. has led to..." -> "A"
          const match = opt.match(/^([A-Z])/i);
          if (match) submitValue = match[1].toUpperCase();
        }

        return (
          <option key={idx} value={submitValue}>
            {opt}
          </option>
        );
      })}
    </select>
  );
}

// ============================================================
// SummaryBlock — summary text with inline blanks
// ============================================================
function SummaryBlock({ context, questions, answers, setAnswer, disabled, groupOptions }) {
  if (!context) return null;

  // Replace ___N___ patterns with inline inputs
  // Build a map of blank number -> question
  const blankMap = {};
  questions.forEach((q) => {
    // Extract blank number from questionText: "Blank 9" -> 9
    const match = q.questionText.match(/(\d+)/);
    if (match) {
      blankMap[match[1]] = q;
    }
  });

  // Split context by blank pattern ___N___
  const parts = context.split(/(___\d+___)/g);

  return (
    <div className="summary-block">
      <div className="summary-text">
        {parts.map((part, idx) => {
          const blankMatch = part.match(/___(\d+)___/);
          if (blankMatch) {
            const blankNum = blankMatch[1];
            const question = blankMap[blankNum];
            if (!question) return <span key={idx}>{part}</span>;

            if (groupOptions && groupOptions.length > 0) {
              // Word bank: dropdown
              return (
                <select
                  key={idx}
                  className="summary-blank summary-blank-select"
                  value={answers[question.questionId] || ''}
                  onChange={(e) => setAnswer(question.questionId, e.target.value)}
                  disabled={disabled}
                >
                  <option value="">({blankNum})</option>
                  {groupOptions.map((opt, oidx) => (
                    <option key={oidx} value={opt}>{opt}</option>
                  ))}
                </select>
              );
            }

            // Free text input
            return (
              <input
                key={idx}
                type="text"
                className="summary-blank"
                placeholder={`(${blankNum})`}
                value={answers[question.questionId] || ''}
                onChange={(e) => setAnswer(question.questionId, e.target.value)}
                onBlur={(e) => setAnswer(question.questionId, e.target.value.trim())}
                disabled={disabled}
              />
            );
          }
          return <span key={idx}>{part}</span>;
        })}
      </div>
    </div>
  );
}
