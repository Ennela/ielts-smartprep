import '@testing-library/jest-dom';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  TASK1_TYPES, TASK2_TYPES, ALL_ESSAY_TYPES, isTask1Type, formatEssayType,
} from '../constants/examTypes';
import QuestionPanel from '../components/questions/QuestionPanel';

/*
 * The Task 1 list was copied into six files and the panel into two. The copies had
 * drifted: none of them listed LETTER, which EssayType.isTask1() counts as Task 1,
 * so a General Training letter was shown as "Task 2" and held to 250 words.
 */

describe('essay type constants', () => {
  it('counts LETTER as Task 1, like the backend enum', () => {
    expect(isTask1Type('LETTER')).toBe(true);
    expect(isTask1Type('OPINION')).toBe(false);
  });

  it('covers every EssayType value the backend can send', () => {
    const backendEssayTypes = [
      'OPINION', 'DISCUSSION', 'CAUSE_AND_EFFECT', 'PROBLEM_AND_SOLUTION',
      'ADVANTAGES_DISADVANTAGES', 'TWO_PART_QUESTION',
      'LINE_GRAPH', 'BAR_CHART', 'PIE_CHART', 'TABLE', 'MAP', 'DIAGRAM', 'LETTER',
    ];
    expect([...ALL_ESSAY_TYPES].sort()).toEqual([...backendEssayTypes].sort());
    expect(TASK1_TYPES.some((t) => TASK2_TYPES.includes(t))).toBe(false);
  });

  it('labels known types and title-cases anything new', () => {
    expect(formatEssayType('ADVANTAGES_DISADVANTAGES')).toBe('Advantages & Disadvantages');
    expect(formatEssayType('LETTER')).toBe('Letter');
    expect(formatEssayType('SOMETHING_NEW')).toBe('Something New');
    expect(formatEssayType(undefined)).toBe('');
  });
});

describe('shared QuestionPanel', () => {
  const questions = [
    { questionId: 1, groupId: 1, orderIndex: 1, questionType: 'MCQ', questionText: 'Where?', correctAnswer: 'B', options: [{ optionId: 1, label: 'A', content: 'Hall' }, { optionId: 2, label: 'B', content: 'Garden' }] },
  ];

  it('reports answers through the handler it was given', () => {
    const setAnswer = vi.fn();
    render(<QuestionPanel questions={questions} answers={{}} setAnswer={setAnswer} />);

    fireEvent.click(screen.getByText(/Garden/));

    expect(setAnswer).toHaveBeenCalledWith(1, 'B');
  });

  it('hides the answer key unless the caller asks for it', () => {
    const { rerender } = render(<QuestionPanel questions={questions} answers={{}} setAnswer={() => {}} />);
    expect(screen.queryByText(/Đáp án đúng/)).not.toBeInTheDocument();

    rerender(<QuestionPanel questions={questions} answers={{}} setAnswer={() => {}} showCorrectAnswers />);
    expect(screen.getByText(/Đáp án đúng/)).toBeInTheDocument();
  });

  it('renders nothing for an empty question list', () => {
    const { container } = render(<QuestionPanel questions={[]} answers={{}} setAnswer={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });
});
