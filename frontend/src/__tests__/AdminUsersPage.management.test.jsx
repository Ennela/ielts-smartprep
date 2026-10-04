import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfirmProvider } from '../context/ConfirmContext';
import AdminUsersPage from '../pages/AdminUsersPage';

/*
 * The Students page lists students only by default (admins were mixed in), can switch to
 * admins or everyone, and lets an admin suspend, reactivate or change the role of another
 * account after a confirmation, but never their own.
 */

const learner = { userId: 7, username: 'learner', email: 'l@test.com', role: 'STUDENT', suspended: false, totalTests: 3, avgScore: 6, createdAt: '2026-09-01T00:00:00' };

vi.mock('../api/adminApi', () => ({
  default: {
    listUsers: vi.fn(),
    getUserDetail: vi.fn(),
    updateUser: vi.fn(),
  },
}));
vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: () => {}, error: () => {}, info: () => {}, warning: () => {} }),
}));
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { userId: 1, username: 'boss' } }),
}));

import adminApi from '../api/adminApi';

const ok = (data) => Promise.resolve({ data: { success: true, data } });

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ConfirmProvider>
        <MemoryRouter><AdminUsersPage /></MemoryRouter>
      </ConfirmProvider>
    </QueryClientProvider>
  );
}

describe('AdminUsersPage account management', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    adminApi.listUsers.mockImplementation(() => ok({ content: [learner], totalPages: 1, totalElements: 1 }));
    adminApi.getUserDetail.mockImplementation((id) => ok({ ...learner, userId: id }));
    adminApi.updateUser.mockImplementation((id, body) => ok({ ...learner, userId: id, ...body }));
  });

  it('lists students by default and can switch to admins', async () => {
    renderPage();

    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled());
    expect(adminApi.listUsers.mock.calls[0][4]).toBe('STUDENT');
    expect(await screen.findByText('Active')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Admins' }));
    await waitFor(() => expect(adminApi.listUsers.mock.calls.at(-1)[4]).toBe('ADMIN'));
  });

  it('suspends another account after the confirmation', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Details' }));

    const details = await screen.findByRole('dialog', { name: 'Student details' });
    fireEvent.click(await within(details).findByRole('button', { name: 'Suspend' }));

    const confirmDialog = await screen.findByRole('dialog', { name: 'Suspend this account?' });
    fireEvent.click(within(confirmDialog).getByRole('button', { name: 'Suspend' }));

    await waitFor(() => expect(adminApi.updateUser).toHaveBeenCalledWith(7, { suspended: true }));
    expect(await within(details).findByRole('button', { name: 'Reactivate' })).toBeInTheDocument();
  });

  it('offers no account actions on the signed-in admin', async () => {
    adminApi.listUsers.mockImplementation(() => ok({ content: [{ ...learner, userId: 1, username: 'boss', role: 'ADMIN' }], totalPages: 1, totalElements: 1 }));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Details' }));

    const details = await screen.findByRole('dialog', { name: 'Student details' });
    expect(await within(details).findByText(/This is your own account/)).toBeInTheDocument();
    expect(within(details).queryByRole('button', { name: 'Suspend' })).not.toBeInTheDocument();
  });
});
