import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Mock axiosClient before importing authService
vi.mock('../api/axiosClient', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    put: vi.fn(),
  },
}));

import authService, { clearAllAuthData } from '../api/authService';
import axiosClient from '../api/axiosClient';

describe('authService', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('clearAllAuthData', () => {
    it('should remove all auth-related keys from localStorage', () => {
      localStorage.setItem('token', 'access-token');
      localStorage.setItem('refreshToken', 'refresh-token');
      localStorage.setItem('user', JSON.stringify({ id: 1 }));
      localStorage.setItem('otherKey', 'should-remain');

      clearAllAuthData();

      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
      expect(localStorage.getItem('user')).toBeNull();
      expect(localStorage.getItem('otherKey')).toBe('should-remain');
    });

    it('should not throw when keys do not exist', () => {
      expect(() => clearAllAuthData()).not.toThrow();
    });
  });

  describe('logout', () => {
    it('should send refresh token to server and clear localStorage', async () => {
      localStorage.setItem('token', 'access-token');
      localStorage.setItem('refreshToken', 'refresh-token');
      localStorage.setItem('user', '{"id":1}');

      const mockPost = vi.mocked(axiosClient.post);
      mockPost.mockResolvedValueOnce({ data: { data: null } } as any);

      await authService.logout();

      expect(mockPost).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'refresh-token' });
      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
      expect(localStorage.getItem('user')).toBeNull();
    });

    it('should clear localStorage even if server call fails', async () => {
      localStorage.setItem('token', 'access-token');
      localStorage.setItem('refreshToken', 'refresh-token');

      const mockPost = vi.mocked(axiosClient.post);
      mockPost.mockRejectedValueOnce(new Error('Network error'));

      await authService.logout();

      expect(localStorage.getItem('token')).toBeNull();
      expect(localStorage.getItem('refreshToken')).toBeNull();
    });

    it('should send cookie-backed logout and clear storage when no refresh token exists', async () => {
      localStorage.setItem('token', 'access-token');
      // No refreshToken set

      const mockPost = vi.mocked(axiosClient.post);
      mockPost.mockResolvedValueOnce({ data: { data: null } } as any);

      await authService.logout();

      expect(mockPost).toHaveBeenCalledWith('/auth/logout', {});
      expect(localStorage.getItem('token')).toBeNull();
    });
  });
});
