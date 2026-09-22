import { useReading } from '../../context/ReadingContext';
import QuestionPanel from '../questions/QuestionPanel';

/** The shared question panel, bound to the standalone Reading exam's state. */
export default function ReadingQuestionPanel({ questions, showCorrectAnswers }) {
  const { answers, setAnswer, isSubmitted } = useReading();

  return (
    <QuestionPanel
      questions={questions}
      answers={answers}
      setAnswer={setAnswer}
      disabled={isSubmitted}
      showCorrectAnswers={showCorrectAnswers}
    />
  );
}
