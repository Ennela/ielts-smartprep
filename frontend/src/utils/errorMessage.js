/**
 * The text to show for a failed action. Only axiosClient's userMessage is trusted; a raw
 * server reason ("Content-Type ... is not supported"), "Network Error" or a script error
 * ("Cannot read properties of undefined") never reaches the page. Otherwise the caller's own
 * wording, which knows what was being done, is used.
 */
export default function errorMessage(err, fallback = 'Something went wrong. Please try again.') {
  return err?.userMessage || fallback;
}
