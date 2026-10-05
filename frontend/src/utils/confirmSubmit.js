// The dialog shown before an exam is handed in, naming the questions still blank.
// Timed-out auto-submits do not ask: there is nothing left to decide then.
export function submitAnswersPrompt(unanswered) {
  return {
    title: 'Submit your answers?',
    message: unanswered > 0
      ? `You have ${unanswered} unanswered question${unanswered === 1 ? '' : 's'}. You cannot change your answers after submitting.`
      : 'You cannot change your answers after submitting.',
    confirmLabel: unanswered > 0 ? 'Submit anyway' : 'Submit',
  };
}
