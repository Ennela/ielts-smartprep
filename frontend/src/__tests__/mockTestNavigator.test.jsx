import '@testing-library/jest-dom';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import QuestionNavigator from '../components/mocktest/QuestionNavigator';
import QuestionPanel from '../components/questions/QuestionPanel';

/*
 * The mock test now has the exam's question strip (answered / flagged at a glance,
 * jump to a question) and a "Flag" control per question. Practice pages pass no
 * flag handler and must look as before.
 */

const groups = [
  { index: 0, label: 'Part 1', items: [
    { questionId: 11, number: 1, answered: true, flagged: false },
    { questionId: 12, number: 2, answered: false, flagged: true },
  ] },
  { index: 1, label: 'Part 2', items: [{ questionId: 21, number: 3, answered: false, flagged: false }] },
];

describe('QuestionNavigator', () => {
  it('names each question with its state and jumps to the one pressed', () => {
    const onJump = vi.fn();
    render(<QuestionNavigator groups={groups} activeIndex={0} onJump={onJump} />);

    expect(screen.getByRole('navigation', { name: 'Question navigator' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Question 1, answered' })).toHaveClass('is-answered');
    expect(screen.getByRole('button', { name: 'Question 2, not answered, flagged' })).toHaveClass('is-flagged');

    fireEvent.click(screen.getByRole('button', { name: 'Question 3, not answered' }));
    expect(onJump).toHaveBeenCalledWith(1, 21);
  });
});

describe('QuestionPanel flags', () => {
  const questions = [{ questionId: 5, orderIndex: 1, questionType: 'TFNG', questionText: 'The sky is green.' }];

  it('shows no flag control on practice pages', () => {
    render(<QuestionPanel questions={questions} answers={{}} setAnswer={() => {}} />);
    expect(screen.queryByRole('button', { name: /Flag question/ })).not.toBeInTheDocument();
  });

  it('toggles the flag of a question in the mock test', () => {
    const onToggleFlag = vi.fn();
    const { rerender } = render(
      <QuestionPanel questions={questions} answers={{}} setAnswer={() => {}} flaggedIds={new Set()} onToggleFlag={onToggleFlag} />,
    );
    const flag = screen.getByRole('button', { name: 'Flag question 1 for review' });
    expect(flag).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(flag);
    expect(onToggleFlag).toHaveBeenCalledWith(5);

    rerender(
      <QuestionPanel questions={questions} answers={{}} setAnswer={() => {}} flaggedIds={new Set([5])} onToggleFlag={onToggleFlag} />,
    );
    expect(screen.getByRole('button', { name: 'Flag question 1 for review' })).toHaveAttribute('aria-pressed', 'true');
  });
});
