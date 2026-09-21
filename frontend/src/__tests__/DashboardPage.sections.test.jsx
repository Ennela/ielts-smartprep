import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import DashboardPage from '../pages/DashboardPage';

/*
 * The dashboard loaded its four blocks with Promise.all, so one failing block blanked the
 * whole page, and the per-filter refetches logged errors to the console and let a slow
 * response for an earlier filter overwrite the data of the one selected now.
 */

vi.mock('../api/statsApi', () => ({
  default: { getOverview: vi.fn(), getScoreTrend: vi.fn(), getHistory: vi.fn() },
}));
vi.mock('../api/analyticsApi', () => ({
  default: { getWeakness: vi.fn() },
}));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { username: 'noah2005', displayName: 'Noah' } }),
}));
// recharts needs layout; the chart itself is not under test.
vi.mock('../components/analytics/ScoreTrendChart', () => ({
  default: ({ skill, dataPoints }) => <div data-testid="trend">{skill}:{dataPoints.length}</div>,
}));

import statsApi from '../api/statsApi';
import analyticsApi from '../api/analyticsApi';

const ok = (data) => ({ data: { success: true, data } });
const trend = (skill, n) => ok({ skill, targetScore: 7, dataPoints: Array.from({ length: n }, (_, i) => ({ date: `d${i}`, score: 6 })) });
const emptyHistory = ok({ items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 });

function renderDashboard() {
  return render(<MemoryRouter><DashboardPage /></MemoryRouter>);
}

describe('DashboardPage sections', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    statsApi.getOverview.mockResolvedValue(ok({ totalTests: 3, targetBand: '7.5', currentEstimate: '6.5', skills: [] }));
    statsApi.getScoreTrend.mockResolvedValue(trend('READING', 2));
    statsApi.getHistory.mockResolvedValue(emptyHistory);
    analyticsApi.getWeakness.mockResolvedValue(ok({ weakestType: 'MATCHING', weakestAccuracy: 40, accuracies: { MATCHING: 40 } }));
  });

  it('renders the page with an inline retry when only the trend request fails', async () => {
    statsApi.getScoreTrend
      .mockRejectedValueOnce(new Error('Trend service down'))
      .mockResolvedValueOnce(trend('READING', 3));

    renderDashboard();

    expect(await screen.findByText('Trend service down')).toBeInTheDocument();
    // The rest of the dashboard is still there.
    expect(screen.getByText('MATCHING')).toBeInTheDocument();
    expect(screen.queryByText('Something went wrong')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByTestId('trend')).toHaveTextContent('READING:3');
  });

  it('shows the data of the filter selected last, not of a slower earlier request', async () => {
    let resolveWriting;
    statsApi.getScoreTrend.mockImplementation((skill) => {
      if (skill === 'WRITING') return new Promise((resolve) => { resolveWriting = resolve; });
      return Promise.resolve(trend(skill, skill === 'LISTENING' ? 5 : 2));
    });

    renderDashboard();
    expect(await screen.findByTestId('trend')).toHaveTextContent('READING:2');

    const [skillSelect] = screen.getAllByRole('combobox');
    fireEvent.change(skillSelect, { target: { value: 'WRITING' } });
    fireEvent.change(skillSelect, { target: { value: 'LISTENING' } });

    await waitFor(() => expect(screen.getByTestId('trend')).toHaveTextContent('LISTENING:5'));

    // The WRITING response arrives late and must be ignored.
    await act(async () => { resolveWriting(trend('WRITING', 9)); });
    expect(screen.getByTestId('trend')).toHaveTextContent('LISTENING:5');
  });

  it('still takes the whole page down when the overview itself fails', async () => {
    statsApi.getOverview.mockRejectedValue(new Error('Overview down'));

    renderDashboard();

    expect(await screen.findByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Overview down')).toBeInTheDocument();
  });
});
