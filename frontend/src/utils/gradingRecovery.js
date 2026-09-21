/**
 * AI grading is synchronous and slow (one essay ~30 s, a two-task sitting 70-80 s).
 * The request can die on the way back — gateway timeout, dropped connection — while
 * the backend goes on to grade and save. Re-enabling the submit button then grades
 * the same essay twice (nginx.conf records this happening).
 *
 * A page notes the newest history row before it submits; after a failure that may
 * have graded anyway, it polls the history until a newer row appears and goes to it.
 */

/** A response the server never sent (network/timeout) or a gateway/server error. */
export const mayHaveGradedAnyway = (err) => !err?.status || err.status >= 500;

/**
 * The id of the newest history row, `null` when there is none yet, `undefined`
 * when the history could not be read (recovery is then not possible).
 */
export async function latestSubmissionId(fetchPage, idField) {
  try {
    const res = await fetchPage(0, 1);
    return res.data?.data?.content?.[0]?.[idField] ?? null;
  } catch {
    return undefined;
  }
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Polls the history for a row newer than `beforeId` that satisfies `matches`.
 * Resolves with the row, or `null` once every try is used up.
 */
export async function findNewSubmission(fetchPage, idField, beforeId, matches = () => true,
  { tries = 12, intervalMs = 5000 } = {}) {
  if (beforeId === undefined) return null;
  for (let i = 0; i < tries; i++) {
    await wait(intervalMs);
    try {
      const res = await fetchPage(0, 1);
      const latest = res.data?.data?.content?.[0];
      if (latest && latest[idField] !== beforeId && matches(latest)) return latest;
    } catch {
      // keep polling — the outage that lost the grade response may still be on
    }
  }
  return null;
}
