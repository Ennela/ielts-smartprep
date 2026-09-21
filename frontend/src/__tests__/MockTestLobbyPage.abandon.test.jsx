import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MockTestProvider } from '../context/MockTestContext';
import MockTestLobbyPage from '../pages/MockTestLobbyPage';

/*
 * "Abandon Exam" only cleared client state. The server session stayed IN_PROGRESS, so the
 * next Start resumed it and a reload showed "Test in Progress" again. It now retires the
 * session through POST /mock-tests/{id}/abandon before forgetting it locally.
 */

vi.mock('../api/mockTestApi', () => ({
  default: {
    getAllMockTests: vi.fn(),
    getCurrentSession: vi.fn(),
    getHistory: vi.fn(),
    abandonSession: vi.fn(),
  },
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));

import mockTestApi from '../api/mockTestApi';

const ok = (data) => ({ data: { success: true, data } });

describe('MockTestLobbyPage abandon', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    mockTestApi.getAllMockTests.mockResolvedValue(ok([]));
    mockTestApi.getHistory.mockResolvedValue(ok({ content: [], totalElements: 0, totalPages: 0 }));
    mockTestApi.getCurrentSession.mockResolvedValue(ok({
      sessionId: 4, status: 'IN_PROGRESS', currentSection: 'LISTENING', title: 'Cambridge 19 Test 1',
      timeRemainingSeconds: 1200, progressJson: '{}',
    }));
    mockTestApi.abandonSession.mockResolvedValue(ok({ sessionId: 4, status: 'EXPIRED' }));
    localStorage.setItem('mock_session_4', JSON.stringify({ answers: { q1: 'A' }, timestamp: Date.now() }));
  });

  it('retires the session on the server before dropping it locally', async () => {
    render(
      <MemoryRouter>
        <MockTestProvider><MockTestLobbyPage /></MockTestProvider>
      </MemoryRouter>
    );

    fireEvent.click(await screen.findByText('Abandon Exam'));

    await waitFor(() => expect(mockTestApi.abandonSession).toHaveBeenCalledWith(4));
    await waitFor(() => expect(screen.queryByText('Test in Progress')).not.toBeInTheDocument());
    expect(localStorage.getItem('mock_session_4')).toBeNull();
  });

  it('keeps the session when the server refuses', async () => {
    mockTestApi.abandonSession.mockRejectedValue(new Error('Network Error'));
    render(
      <MemoryRouter>
        <MockTestProvider><MockTestLobbyPage /></MockTestProvider>
      </MemoryRouter>
    );

    fireEvent.click(await screen.findByText('Abandon Exam'));

    await waitFor(() => expect(mockTestApi.abandonSession).toHaveBeenCalledTimes(1));
    expect(screen.getByText('Test in Progress')).toBeInTheDocument();
    expect(localStorage.getItem('mock_session_4')).not.toBeNull();
  });
});
