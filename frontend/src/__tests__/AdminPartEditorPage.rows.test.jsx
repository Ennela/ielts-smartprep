import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AdminPartEditorPage from '../pages/AdminPartEditorPage';

/*
 * The question rows were keyed by array index, so deleting one from the middle
 * shifted every key up: React kept the DOM nodes of the rows above it and dropped
 * the last one instead. The values still looked right — they are controlled — but
 * the surviving rows were re-attached to different nodes, taking focus, scroll
 * position and any in-flight IME composition with them.
 *
 * Keying by a per-row id keeps each question on its own node.
 */

vi.mock('../api/adminApi', () => ({
  default: { getListeningPartById: vi.fn(), createListeningPart: vi.fn(), updateListeningPart: vi.fn() },
}));

const questionBoxes = () => screen.getAllByPlaceholderText(/What type of accommodation/);

describe('AdminPartEditorPage question rows', () => {
  beforeEach(() => vi.clearAllMocks());

  it('keeps the surviving questions on their own nodes when one is deleted from the middle', () => {
    render(<MemoryRouter><AdminPartEditorPage /></MemoryRouter>);

    const addQuestion = screen.getByRole('button', { name: '+ Add Question' });
    fireEvent.click(addQuestion);
    fireEvent.click(addQuestion);
    fireEvent.click(addQuestion);

    const [first, second, third] = questionBoxes();
    fireEvent.change(first, { target: { value: 'first' } });
    fireEvent.change(second, { target: { value: 'second' } });
    fireEvent.change(third, { target: { value: 'third' } });
    third.focus();

    fireEvent.click(screen.getAllByRole('button', { name: 'Delete Question' })[1]);

    const remaining = questionBoxes();
    expect(remaining.map((el) => el.value)).toEqual(['first', 'third']);
    // The same two elements, not re-used nodes: with index keys the third row's
    // node is the one React unmounts, so this is what actually regressed.
    expect(remaining[0]).toBe(first);
    expect(remaining[1]).toBe(third);
    expect(document.activeElement).toBe(third);
  });
});
