import '@testing-library/jest-dom';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MainLayout from '../components/common/MainLayout';

/*
 * The admin layout showed only the initial, even after the admin had uploaded a picture
 * on the profile page; the learner layout already showed it.
 */

let currentUser;
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: currentUser, logout: vi.fn(), isAdmin: true }),
}));

const renderLayout = () => render(<MemoryRouter><MainLayout /></MemoryRouter>);

describe('MainLayout avatar', () => {
  it('shows the uploaded picture in the sidebar and the top bar', () => {
    currentUser = { username: 'admin2026', avatarUrl: '/api/v1/auth/avatar/avatar_12.png' };
    const { container } = renderLayout();

    const images = container.querySelectorAll('img');
    expect(images).toHaveLength(2);
    images.forEach((img) => expect(img.getAttribute('src')).toBe('/api/v1/auth/avatar/avatar_12.png'));
  });

  it('falls back to the initial when there is no picture or it fails to load', () => {
    currentUser = { username: 'admin2026', avatarUrl: '/api/v1/auth/avatar/missing.png' };
    const { container } = renderLayout();

    fireEvent.error(container.querySelector('img'));

    expect(container.querySelectorAll('img')).toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Profile and settings' })).toHaveTextContent('A');
  });
});
