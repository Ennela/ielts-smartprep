import '@testing-library/jest-dom';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import QuestionPanel from '../components/questions/QuestionPanel';

/*
 * FILL_BLANK, SHORT_ANSWER and DIAGRAM_LABEL_COMPLETION used to render
 * "Unsupported question type": no box to type in, so every one of them scored zero even
 * though the backend grades all three. The AI generator produces them, so did the seed.
 */

const question = (questionId, questionType) => ({
  questionId,
  questionType,
  questionText: `${questionId}. The answer goes here ______`,
  orderIndex: questionId,
  groupId: questionId, // one group per question, so each renders on its own
});

describe('QuestionPanel written-answer types', () => {
  it.each(['FILL_BLANK', 'SHORT_ANSWER', 'DIAGRAM_LABEL_COMPLETION'])(
    'renders an answer box for %s and records what is typed',
    (type) => {
      const setAnswer = vi.fn();
      render(<QuestionPanel questions={[question(1, type)]} answers={{}} setAnswer={setAnswer} />);

      expect(screen.queryByText('Unsupported question type')).not.toBeInTheDocument();

      const box = document.getElementById('completion-1');
      expect(box).toBeInTheDocument();
      fireEvent.change(box, { target: { value: ' rainfall ' } });
      fireEvent.blur(box);

      expect(setAnswer).toHaveBeenCalledWith(1, 'rainfall');
    }
  );

  it('shows the word limit on the box when the question has one', () => {
    const q = { ...question(1, 'FILL_BLANK'), wordLimit: 2 };
    render(<QuestionPanel questions={[q]} answers={{}} setAnswer={vi.fn()} />);

    expect(document.getElementById('completion-1')).toHaveAttribute('placeholder', 'Max 2 words');
  });

  it('still says so for a type it genuinely does not know', () => {
    render(<QuestionPanel questions={[question(1, 'SOMETHING_NEW')]} answers={{}} setAnswer={vi.fn()} />);

    expect(screen.getByText('Unsupported question type')).toBeInTheDocument();
  });
});
