// Asks before an exam is handed in, naming the questions still blank. Timed-out
// auto-submits do not go through this: there is nothing left to decide then.
export function confirmSubmitAnswers(unanswered) {
  const message = unanswered > 0
    ? `You have ${unanswered} unanswered question${unanswered === 1 ? '' : 's'}. Submit anyway?`
    : 'Submit your answers? You cannot change them afterwards.';
  return window.confirm(message);
}
