import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from '../context/AuthContext';
import ProtectedRoute from '../components/common/ProtectedRoute';

/*
 * On load AuthProvider validates the stored access token with GET /auth/me. Only an
 * auth failure (401 after the interceptor gave up refreshing, or 403) means the session
 * is gone; a network error or 5xx must keep the token and let the user retry, otherwise
 * a backend restart logs everyone out.
 */

vi.mock('../api/authService', () => ({
  default: { getProfile: vi.fn(), login: vi.fn(), logout: vi.fn() },
  clearAllAuthData: vi.fn(() => localStorage.removeItem('token')),
}));

import authService, { clearAllAuthData } from '../api/authService';

const profile = { id: 2, username: 'noah2005', role: 'STUDENT', emailVerified: true };

function renderGuarded() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/dashboard']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login page</div>} />
            <Route path="/dashboard" element={<ProtectedRoute><div>Dashboard</div></ProtectedRoute>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('AuthProvider session bootstrap', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem('token', 'access-token');
  });

  it('clears the token and redirects to login when /auth/me answers 401', async () => {
    authService.getProfile.mockRejectedValue(Object.assign(new Error('Unauthorized'), { status: 401 }));

    renderGuarded();

    expect(await screen.findByText('Login page')).toBeInTheDocument();
    expect(clearAllAuthData).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('keeps the token and offers a retry when /auth/me fails for a non-auth reason', async () => {
    authService.getProfile
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockResolvedValueOnce({ data: { success: true, data: profile } });

    renderGuarded();

    expect(await screen.findByText('Could not verify your session.')).toBeInTheDocument();
    expect(screen.getByText('Check your internet connection and try again.')).toBeInTheDocument();
    expect(screen.queryByText('Network Error')).not.toBeInTheDocument();
    expect(screen.queryByText('Login page')).not.toBeInTheDocument();
    expect(clearAllAuthData).not.toHaveBeenCalled();
    expect(localStorage.getItem('token')).toBe('access-token');

    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));

    expect(await screen.findByText('Dashboard')).toBeInTheDocument();
    await waitFor(() => expect(authService.getProfile).toHaveBeenCalledTimes(2));
  });

  it('treats a 5xx like an outage, not a logout', async () => {
    authService.getProfile.mockRejectedValue(Object.assign(new Error('Bad Gateway'), { status: 502 }));

    renderGuarded();

    expect(await screen.findByText('Could not verify your session.')).toBeInTheDocument();
    expect(clearAllAuthData).not.toHaveBeenCalled();
    expect(localStorage.getItem('token')).toBe('access-token');
  });
});

describe('AuthProvider and cached queries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem('token', 'access-token');
    authService.getProfile.mockResolvedValue({ data: { success: true, data: profile } });
  });

  function AccountSwitcher() {
    const { user, login, logout } = useAuth();
    return (
      <div>
        <span>Signed in: {user?.username || 'nobody'}</span>
        <button type="button" onClick={() => logout()}>Log out</button>
        <button type="button" onClick={() => login('other', 'pw')}>Log in as other</button>
      </div>
    );
  }

  function renderWithCache(queryClient) {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthProvider><AccountSwitcher /></AuthProvider>
      </QueryClientProvider>
    );
  }

  it("drops the last account's cached history on logout, so the next account never sees it", async () => {
    const queryClient = new QueryClient();
    renderWithCache(queryClient);
    expect(await screen.findByText('Signed in: noah2005')).toBeInTheDocument();
    queryClient.setQueryData(['reading', 'history'], ['noah2005 result']);

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(await screen.findByText('Signed in: nobody')).toBeInTheDocument();
    expect(queryClient.getQueryData(['reading', 'history'])).toBeUndefined();
  });

  it('drops cached queries when another account signs in, even without a logout first', async () => {
    authService.login.mockResolvedValue({ data: { data: { username: 'other', role: 'STUDENT', token: 't2' } } });
    const queryClient = new QueryClient();
    renderWithCache(queryClient);
    expect(await screen.findByText('Signed in: noah2005')).toBeInTheDocument();
    queryClient.setQueryData(['vocab', 'words'], ['noah2005 word']);

    fireEvent.click(screen.getByRole('button', { name: 'Log in as other' }));

    expect(await screen.findByText('Signed in: other')).toBeInTheDocument();
    expect(queryClient.getQueryData(['vocab', 'words'])).toBeUndefined();
  });
});
