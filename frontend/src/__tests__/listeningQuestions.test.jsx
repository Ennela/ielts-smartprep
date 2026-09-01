import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import McqQuestion from '../components/listening/McqQuestion';
import FillBlankQuestion from '../components/listening/FillBlankQuestion';

/*
 * These two components were duplicated in ListeningExamPage and MockTestSessionPage and
 * were merged into one. The copies differed, so the tests below pin the differences that
 * had to survive the merge as well as the behaviour that had to stay the same.
 */

const structuredQuestion = {
  questionId: 7,
  questionType: 'MCQ',
  questionText: 'Where is breakfast served?',
  options: [
    { optionId: 1, label: 'A', content: 'The Main Hall' },
    { optionId: 2, label: 'B', content: 'The Garden Restaurant' },
  ],
};

const textOnlyQuestion = {
  questionId: 8,
  questionType: 'MCQ',
  questionText: 'Where is breakfast served?\nA. The Main Hall\nB. The Garden Restaurant',
};

describe('McqQuestion', () => {
  it('renders choices supplied as structured options', () => {
    render(<McqQuestion question={structuredQuestion} value="" onChange={() => {}} />);

    expect(screen.getByText('The Main Hall')).toBeInTheDocument();
    expect(screen.getByText('The Garden Restaurant')).toBeInTheDocument();
    expect(screen.getByText('A')).toBeInTheDocument();
    expect(screen.getAllByRole('radio')).toHaveLength(2);
  });

  /*
   * The MockTestSessionPage copy lacked this branch entirely and always parsed
   * questionText, so structured options coming from the backend were ignored there.
   */
  it('falls back to parsing questionText when no options are supplied', () => {
    render(<McqQuestion question={textOnlyQuestion} value="" onChange={() => {}} />);

    expect(screen.getByText('Where is breakfast served?')).toBeInTheDocument();
    expect(screen.getByText('The Main Hall')).toBeInTheDocument();
    expect(screen.getAllByRole('radio')).toHaveLength(2);
  });

  it('reports the selected option letter', () => {
    const onChange = vi.fn();
    render(<McqQuestion question={structuredQuestion} value="" onChange={onChange} />);

    fireEvent.click(screen.getAllByRole('radio')[1]);

    expect(onChange).toHaveBeenCalledWith('B');
  });

  it('marks the option matching the current value as selected', () => {
    const { container } = render(
      <McqQuestion question={structuredQuestion} value="B" onChange={() => {}} />,
    );

    const selected = container.querySelectorAll('.mcq-option.selected');
    expect(selected).toHaveLength(1);
    expect(selected[0].textContent).toContain('The Garden Restaurant');
  });
});

const blankQuestion = {
  questionId: 9,
  questionType: 'FILL_BLANK',
  questionText: 'The caller wants a room for ___ nights.',
};

describe('FillBlankQuestion', () => {
  it('splits the sentence around the blank and reports typing', () => {
    const onChange = vi.fn();
    render(<FillBlankQuestion question={blankQuestion} value="" onChange={onChange} />);

    expect(screen.getByText(/The caller wants a room for/)).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'three' } });

    expect(onChange).toHaveBeenCalledWith('three');
  });

  /*
   * The listening exam styles the blank through the .fill-blank-input rule in index.css.
   * The mock test overrode it inline with a narrower monospace rule. Both looks had to
   * survive the merge, so the variant is asserted rather than assumed.
   */
  it('uses the boxed look by default', () => {
    const { container } = render(
      <FillBlankQuestion question={blankQuestion} value="" onChange={() => {}} />,
    );

    expect(container.firstChild).toHaveClass('fill-blank-question');
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('placeholder', 'your answer');
    expect(input.style.width).toBe('');
  });

  it('uses the underline look when asked for it', () => {
    const { container } = render(
      <FillBlankQuestion question={blankQuestion} value="" onChange={() => {}} variant="underline" />,
    );

    expect(container.firstChild).not.toHaveClass('fill-blank-question');
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('placeholder', 'your answer...');
    expect(input.style.width).toBe('120px');
    expect(input.style.borderBottom).toBe('2px solid var(--outline)');
  });

  it('keeps the shared .fill-blank-input class in both variants', () => {
    const { container: boxed } = render(
      <FillBlankQuestion question={blankQuestion} value="" onChange={() => {}} />,
    );
    const { container: underline } = render(
      <FillBlankQuestion question={blankQuestion} value="" onChange={() => {}} variant="underline" />,
    );

    expect(boxed.querySelector('input')).toHaveClass('fill-blank-input');
    expect(underline.querySelector('input')).toHaveClass('fill-blank-input');
  });
});
