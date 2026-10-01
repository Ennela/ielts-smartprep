import '@testing-library/jest-dom';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import QuestionPanel from '../components/questions/QuestionPanel';
import { usesGroupedLayout, questionOffset } from '../components/listening/groupedLayout';

/*
 * Listening questions can be grouped since V54: a box of options several questions choose
 * from, or notes with numbered gaps. Such a part is drawn with QuestionPanel; a part
 * written before keeps one card per question.
 */

describe('usesGroupedLayout', () => {
  it('keeps the cards for an ungrouped MCQ / gap part', () => {
    expect(usesGroupedLayout([
      { questionId: 1, questionType: 'MCQ' },
      { questionId: 2, questionType: 'FILL_BLANK', groupId: null },
    ])).toBe(false);
  });

  it('uses the grouped layout once any question is grouped', () => {
    expect(usesGroupedLayout([
      { questionId: 1, questionType: 'MCQ' },
      { questionId: 2, questionType: 'FILL_BLANK', groupId: 3 },
    ])).toBe(true);
  });

  it('uses the grouped layout for a type the cards cannot draw', () => {
    expect(usesGroupedLayout([{ questionId: 1, questionType: 'MATCHING_FEATURES' }])).toBe(true);
  });
});

describe('questionOffset', () => {
  it('counts the questions of the parts before', () => {
    const parts = [{ questions: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] }, { questions: [1, 2] }, { questions: [] }];
    expect(questionOffset(parts, 0)).toBe(0);
    expect(questionOffset(parts, 2)).toBe(12);
  });
});

describe('QuestionPanel with a Listening matching group', () => {
  const options = JSON.stringify(['A. car park', 'B. cafe', 'C. lake']);
  const group = [
    { questionId: 61, questionType: 'MATCHING_FEATURES', questionText: 'Toilets', orderIndex: 6,
      groupId: 2, groupLabel: 'Questions 16-17: Which location on the map?', optionsJson: options },
    { questionId: 62, questionType: 'MATCHING_FEATURES', questionText: 'Shop', orderIndex: 7,
      groupId: 2, groupLabel: 'Questions 16-17: Which location on the map?', optionsJson: options },
  ];

  it('shows the instructions once, numbers on from earlier parts, and answers with the letter', () => {
    const setAnswer = vi.fn();
    render(<QuestionPanel questions={group} answers={{}} setAnswer={setAnswer} numberOffset={10} />);

    expect(screen.getAllByText('Questions 16-17: Which location on the map?')).toHaveLength(1);
    expect(screen.getByText('Question 16')).toBeInTheDocument();
    expect(screen.getByText('Question 17')).toBeInTheDocument();

    fireEvent.change(document.getElementById('matching-61'), { target: { value: 'B' } });
    expect(setAnswer).toHaveBeenCalledWith(61, 'B');
  });
});

describe('QuestionPanel with Listening notes', () => {
  it('puts a box in each numbered gap of the shared text', () => {
    const setAnswer = vi.fn();
    const notes = 'Name: Sarah ___1___\nStreet: ___2___ Road';
    const questions = [1, 2].map(n => ({
      questionId: 100 + n, questionType: 'SUMMARY_COMPLETION', questionText: `Blank ${n}`,
      orderIndex: n, groupId: 1, groupContext: notes, wordLimit: 1,
    }));
    render(<QuestionPanel questions={questions} answers={{}} setAnswer={setAnswer} />);

    const gap = screen.getByPlaceholderText('(2)');
    fireEvent.change(gap, { target: { value: 'Hill' } });
    expect(setAnswer).toHaveBeenCalledWith(102, 'Hill');
    expect(screen.getByText('Maximum 1 words')).toBeInTheDocument();
  });
});
