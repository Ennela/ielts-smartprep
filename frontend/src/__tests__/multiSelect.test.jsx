import '@testing-library/jest-dom';
import { useState } from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import QuestionPanel from '../components/questions/QuestionPanel';

/*
 * "Choose TWO letters, A-E" (V55): two question numbers, one set of checkboxes. The whole
 * choice is written to both rows; the server gives each row its mark.
 */

const options = ['cafe', 'gift shop', 'lake', 'museum', 'zoo'].map((content, i) => ({
  optionId: i + 1, label: 'ABCDE'[i], content,
}));

const task = [21, 22].map(n => ({
  questionId: n, questionType: 'MCQ', orderIndex: n, groupId: 5, selectCount: 2,
  questionText: 'Which TWO places are closed on Mondays?', options,
}));

function Harness() {
  const [answers, setAnswers] = useState({});
  const setAnswer = (id, value) => setAnswers(a => ({ ...a, [id]: value }));
  return (
    <>
      <QuestionPanel questions={task} answers={answers} setAnswer={setAnswer} />
      <output data-testid="answers">{JSON.stringify(answers)}</output>
    </>
  );
}

describe('QuestionPanel with a Choose-TWO task', () => {
  it('shows one task for both numbers', () => {
    render(<Harness />);
    expect(screen.getByText('Questions 21–22')).toBeInTheDocument();
    expect(screen.getAllByText('Which TWO places are closed on Mondays?')).toHaveLength(1);
    expect(screen.getAllByRole('checkbox')).toHaveLength(5);
  });

  it('writes the whole choice, in letter order, to every row', () => {
    render(<Harness />);
    fireEvent.click(screen.getByLabelText(/museum/));
    fireEvent.click(screen.getByLabelText(/cafe/));
    expect(JSON.parse(screen.getByTestId('answers').textContent)).toEqual({ 21: 'A,D', 22: 'A,D' });
  });

  it('takes no more letters than the task allows', () => {
    render(<Harness />);
    fireEvent.click(screen.getByLabelText(/cafe/));
    fireEvent.click(screen.getByLabelText(/lake/));
    expect(screen.getByLabelText(/zoo/)).toBeDisabled();
    fireEvent.click(screen.getByLabelText(/zoo/));
    expect(JSON.parse(screen.getByTestId('answers').textContent)[21]).toBe('A,C');
  });

  it('lets a letter be taken back', () => {
    render(<Harness />);
    fireEvent.click(screen.getByLabelText(/cafe/));
    fireEvent.click(screen.getByLabelText(/lake/));
    fireEvent.click(screen.getByLabelText(/cafe/));
    expect(JSON.parse(screen.getByTestId('answers').textContent)).toEqual({ 21: 'C', 22: 'C' });
  });
});
