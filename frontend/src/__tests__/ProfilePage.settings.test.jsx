import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ProfilePage from '../pages/ProfilePage';

/*
 * The settings page had four ways to mislead a learner, and these cover all four:
 * a preferences tab that reported success without saving anything, a password change that
 * left a dead session behind, a Last Name field that blocked anyone with a one-word
 * display name, and an avatar that showed a broken image when the file was gone.
 */

const updateUser = vi.fn();
const logout = vi.fn();
const toastSuccess = vi.fn();
const toastError = vi.fn();
let currentUser;

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: currentUser, updateUser, logout }),
}));

vi.mock('../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError }),
}));

vi.mock('../context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'light', setTheme: vi.fn() }),
}));

vi.mock('../api/authService', () => ({
  default: { changePassword: vi.fn(), uploadAvatar: vi.fn() },
}));

vi.mock('../api/analyticsApi', () => ({
  default: { getOverview: vi.fn().mockResolvedValue({ data: { data: { averageScores: {} } } }) },
}));

import authService from '../api/authService';

const renderAt = (tab) =>
  render(
    <MemoryRouter initialEntries={[`/profile?tab=${tab}`]}>
      <ProfilePage />
    </MemoryRouter>
  );

beforeEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
  currentUser = {
    userId: 2,
    username: 'noah2005',
    email: 'noah@example.com',
    displayName: 'Noah',
    avatarUrl: '/api/v1/auth/avatar/avatar_2.png',
    targetReadingScore: '8.0',
    targetWritingScore: '8.0',
    targetListeningScore: '8.0',
    emailNotifications: true,
  };
});

describe('Preferences tab', () => {
  it('saves the notification toggle to the account instead of this browser', async () => {
    updateUser.mockResolvedValue({});
    renderAt('prefs');

    fireEvent.click(screen.getByRole('checkbox', { name: /email notifications/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Save Preferences' }));

    await waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith(expect.objectContaining({ emailNotifications: false }))
    );
    await waitFor(() => expect(toastSuccess).toHaveBeenCalled());
  });

  it('does not claim success when the save fails', async () => {
    updateUser.mockRejectedValue(new Error('Service Unavailable'));
    renderAt('prefs');

    fireEvent.click(screen.getByRole('checkbox', { name: /email notifications/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Save Preferences' }));

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Service Unavailable'));
    expect(toastSuccess).not.toHaveBeenCalled();
  });

  it('reflects the saved preference rather than defaulting to on', () => {
    currentUser.emailNotifications = false;
    renderAt('prefs');

    expect(screen.getByRole('checkbox', { name: /email notifications/i })).not.toBeChecked();
    // Nothing changed yet, so there is nothing to save.
    expect(screen.getByRole('button', { name: 'Save Preferences' })).toBeDisabled();
  });

  it('shows the language control as disabled rather than pretending it works', () => {
    renderAt('prefs');

    expect(screen.getByLabelText('Interface language')).toBeDisabled();
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
  });
});

describe('Personal info tab', () => {
  it('lets a one-word display name be saved without inventing a last name', async () => {
    updateUser.mockResolvedValue({});
    renderAt('personal');

    // The account is "Noah", so Last Name starts empty. It must not be required.
    expect(screen.getByLabelText('Last Name')).not.toBeRequired();

    fireEvent.change(screen.getByLabelText('First Name'), { target: { value: 'Noah Tran' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Changes' }));

    await waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith(expect.objectContaining({ displayName: 'Noah Tran' }))
    );
  });

  it('ends the session after a password change instead of leaving a dead one', async () => {
    vi.useFakeTimers();
    authService.changePassword.mockResolvedValue({});
    renderAt('personal');

    fireEvent.change(screen.getByLabelText('Current Password'), { target: { value: 'oldpassword' } });
    fireEvent.change(screen.getByLabelText('New Password'), { target: { value: 'newpassword' } });
    fireEvent.click(screen.getByRole('button', { name: 'Change Password' }));

    await vi.waitFor(() => expect(authService.changePassword).toHaveBeenCalled());
    expect(logout).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1500);
    expect(logout).toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('falls back to the bundled avatar when the image cannot load', () => {
    renderAt('personal');
    const img = screen.getByAltText('User avatar');

    fireEvent.error(img);

    expect(img.getAttribute('src')).toContain('/assets/avatars/avatar_sarah.png');
  });

  it('does not commit unsaved name edits when a picture is uploaded', async () => {
    authService.uploadAvatar.mockResolvedValue({ data: { data: { avatarUrl: '/new.png' } } });
    updateUser.mockResolvedValue({});
    renderAt('personal');

    // Type a name but do not save it, then upload a picture.
    fireEvent.change(screen.getByLabelText('First Name'), { target: { value: 'Unsaved' } });
    const file = new File(['x'], 'a.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]');
    fireEvent.change(input, { target: { files: [file] } });

    await waitFor(() => expect(updateUser).toHaveBeenCalled());
    expect(updateUser).toHaveBeenCalledWith(
      expect.objectContaining({ displayName: 'Noah', avatarUrl: '/new.png' })
    );
  });
});
