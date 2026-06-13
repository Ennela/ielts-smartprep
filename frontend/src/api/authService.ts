import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, AuthResponse, User } from './types';

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

    // ── Token Management ──────────────────────────────────────────────────

    refreshToken: (refreshToken: string): Promise<AxiosResponse<ApiResponse<{ token: string; refreshToken?: string }>>> =>
        axiosClient.post('/auth/refresh', { refreshToken }),

    serverLogout: (refreshToken: string): Promise<AxiosResponse<ApiResponse<void>>> =>
        axiosClient.post('/auth/logout', { refreshToken }),

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
    // WARNING: JWT is stored in localStorage for convenience.
    // Risk: XSS attacks can read localStorage. Mitigations applied:
    // - Access token is short-lived (15 min)
    // - CSP headers block inline/external script injection
    // - Refresh token rotation invalidates stolen tokens quickly

    logout: async (): Promise<void> => {
        const refreshToken = localStorage.getItem('refreshToken');
        if (refreshToken) {
            try {
                await axiosClient.post('/auth/logout', { refreshToken });
            } catch (_e) {
                // Ignore errors during logout — token may already be expired
            }
        }
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
    },
};

export default authService;
