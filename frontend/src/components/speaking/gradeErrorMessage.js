import errorMessage from '../../utils/errorMessage';

/**
 * What to tell the learner when grading fails: errorMessage's rules, plus a reminder that
 * nothing recorded was lost when the cause is on our side.
 *
 * kept: what is still on the page, e.g. "your recording is still here".
 */
export default function gradeErrorMessage(err, kept) {
  const status = err?.status ?? err?.response?.status;
  if (status === 502 || status === 503 || status === 504) {
    return `Grading is not available right now. Try again in a few minutes; ${kept}.`;
  }
  return errorMessage(err, `Grading failed. Try again; ${kept}.`);
}
