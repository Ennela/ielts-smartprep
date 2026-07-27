import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, AuthResponse, User } from './types';

/**
 * Remove all auth-related keys from localStorage.
 * Exported so axiosClient (and any other module) can call it without circular imports.
 */
export function clearAllAuthData(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
}

const authService = {
    register: (email: string, username: string, password: string): Promise<AxiosResponse<ApiResponse<AuthResponse>>> =>
        axiosClient.post('/auth/register', { email, username, password }),

    login: (username: string, password: string): Promise<AxiosResponse<ApiResponse<AuthResponse>>> =>
        axiosClient.post('/auth/login', { username, password }),

    getProfile: (): Promise<AxiosResponse<ApiResponse<User>>> =>
        axiosClient.get('/auth/me'),

    updateProfile: (data: Partial<User> & { password?: string }): Promise<AxiosResponse<ApiResponse<User>>> =>
        axiosClient.put('/auth/profile', data),

    changePassword: (currentPassword: string, newPassword: string): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.put('/auth/password', { currentPassword, newPassword }),

    uploadAvatar: (file: File): Promise<AxiosResponse<ApiResponse<{ avatarUrl: string }>>> => {
        const formData = new FormData();
        formData.append('file', file);
        return axiosClient.post('/auth/avatar', formData, {
            headers: {
                'Content-Type': 'multipart/form-data',
            },
        });
    },


    // ── Token Management ──────────────────────────────────────────────────

    refreshToken: (refreshToken?: string): Promise<AxiosResponse<ApiResponse<{ token: string }>>> =>
        axiosClient.post('/auth/refresh', refreshToken ? { refreshToken } : {}),

    serverLogout: (refreshToken?: string): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.post('/auth/logout', refreshToken ? { refreshToken } : {}),

    // ── Password Recovery ─────────────────────────────────────────────────

    forgotPassword: (email: string): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.post('/auth/forgot-password', { email }),

    resetPassword: (token: string, newPassword: string): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.post('/auth/reset-password', { token, newPassword }),

    // ── Email Verification ────────────────────────────────────────────────

    verifyEmail: (token: string): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.get('/auth/verify-email', { params: { token } }),

    resendVerification: (): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.post('/auth/resend-verification'),

    // ── Local Storage Helpers ─────────────────────────────────────────────
    // Access tokens remain in localStorage for bearer auth.
    // Refresh tokens are stored by the backend in an HttpOnly cookie.

    logout: async (): Promise<void> => {
        const refreshToken = localStorage.getItem('refreshToken');
        try {
            await axiosClient.post('/auth/logout', refreshToken ? { refreshToken } : {});
        } catch (_e) {
                // Ignore errors during logout — token may already be expired
        }
        clearAllAuthData();
    },
};

export default authService;
