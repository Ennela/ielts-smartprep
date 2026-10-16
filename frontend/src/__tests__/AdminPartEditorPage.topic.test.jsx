import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AdminPartEditorPage from '../pages/AdminPartEditorPage';

/*
 * A part's topic is free text, but the select only knew five upper-case keys, so a part
 * saved as "Science" showed "Accommodation / Booking", and a new part defaulted to it.
 */

vi.mock('../api/adminApi', () => ({
  default: { getListeningPartById: vi.fn(), createListeningPart: vi.fn(), updateListeningPart: vi.fn() },
}));

import adminApi from '../api/adminApi';

const part = (topic) => ({ data: { data: { partId: 7, partNumber: 4, title: 'Lecture', topic, transcriptText: 'x', questions: [] } } });

function renderEdit() {
  return render(
    <MemoryRouter initialEntries={['/admin/listening/edit/7']}>
      <Routes><Route path="/admin/listening/edit/:partId" element={<AdminPartEditorPage />} /></Routes>
    </MemoryRouter>
  );
}

const topicSelect = () => screen.getAllByRole('combobox')[1];

describe('AdminPartEditorPage topic', () => {
  beforeEach(() => vi.clearAllMocks());

  it('selects the key a stored topic matches apart from case', async () => {
    adminApi.getListeningPartById.mockResolvedValue(part('Science'));
    renderEdit();
    await screen.findByDisplayValue('Lecture');
    expect(topicSelect()).toHaveValue('SCIENCE');
  });

  it('shows a free-text topic as it is rather than the first option', async () => {
    adminApi.getListeningPartById.mockResolvedValue(part('Accommodation booking'));
    renderEdit();
    await screen.findByDisplayValue('Lecture');
    expect(topicSelect()).toHaveValue('Accommodation booking');
    expect(screen.getByRole('option', { name: 'Accommodation booking (current)' })).toBeInTheDocument();
  });

  it('makes a new part choose a topic instead of defaulting to Accommodation', async () => {
    render(<MemoryRouter><AdminPartEditorPage /></MemoryRouter>);
    expect(topicSelect()).toHaveValue('');

    fireEvent.change(screen.getAllByRole('textbox')[0], { target: { value: 'New part' } });
    fireEvent.submit(topicSelect().closest('form'));

    expect(await screen.findByText('Topic is required')).toBeInTheDocument();
    expect(adminApi.createListeningPart).not.toHaveBeenCalled();
  });
});
