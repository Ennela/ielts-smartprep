/**
 * The numbered strip along the bottom of the mock test, as on the computer-based
 * exam: one button per question in the current section, showing which are answered
 * and which are flagged for review, and jumping to a question when pressed.
 *
 * `groups` is one entry per part or passage: { label, index, items: [{ questionId,
 * number, answered, flagged }] }.
 */
export default function QuestionNavigator({ groups, activeIndex, onJump }) {
  return (
    <nav className="question-nav" aria-label="Question navigator">
      {groups.map((group) => (
        <div
          key={group.index}
          className={`question-nav-group ${group.index === activeIndex ? 'is-current' : ''}`}
        >
          <span className="question-nav-label">{group.label}</span>
          <div className="question-nav-items">
            {group.items.map((item) => {
              const state = [item.answered ? 'answered' : 'not answered', item.flagged ? 'flagged' : null]
                .filter(Boolean).join(', ');
              return (
                <button
                  key={item.questionId}
                  type="button"
                  className={`question-nav-btn ${item.answered ? 'is-answered' : ''} ${item.flagged ? 'is-flagged' : ''}`}
                  onClick={() => onJump(group.index, item.questionId)}
                  aria-label={`Question ${item.number}, ${state}`}
                  title={`Question ${item.number}: ${state}`}
                >
                  {item.number}
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </nav>
  );
}
