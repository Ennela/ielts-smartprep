import { useMockTest } from '../../context/MockTestContext';
import QuestionPanel from '../questions/QuestionPanel';

/**
 * The shared question panel, bound to the mock test's state.
 *
 * Answers stay editable until the section is submitted, so `disabled` is never
 * set here — the section transition is what closes them, not a per-quiz flag.
 */
export default function MockTestQuestionPanel({ questions }) {
  const { answers, setAnswer } = useMockTest();

  return <QuestionPanel questions={questions} answers={answers} setAnswer={setAnswer} />;
}
