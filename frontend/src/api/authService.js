import axiosClient from './axiosClient';

const authService = {
    register: (email, username, password) =>
        axiosClient.post('/auth/register', { email, username, password }),

    login: (username, password) =>
        axiosClient.post('/auth/login', { username, password }),

    getProfile: () =>
        axiosClient.get('/auth/me'),

    updateProfile: (data) =>
        axiosClient.put('/auth/profile', data),

    changePassword: (currentPassword, newPassword) =>
        axiosClient.put('/auth/password', { currentPassword, newPassword }),

    // ── Token Management ──────────────────────────────────────────────────

    refreshToken: (refreshToken) =>
        axiosClient.post('/auth/refresh', { refreshToken }),

    serverLogout: (refreshToken) =>
        axiosClient.post('/auth/logout', { refreshToken }),

    // ── Password Recovery ─────────────────────────────────────────────────

    forgotPassword: (email) =>
        axiosClient.post('/auth/forgot-password', { email }),

    resetPassword: (token, newPassword) =>
        axiosClient.post('/auth/reset-password', { token, newPassword }),

    // ── Email Verification ────────────────────────────────────────────────

    verifyEmail: (token) =>
        axiosClient.get('/auth/verify-email', { params: { token } }),

    resendVerification: () =>
        axiosClient.post('/auth/resend-verification'),

    // ── Local Storage Helpers ─────────────────────────────────────────────
    // WARNING: JWT is stored in localStorage for convenience.
    // Risk: XSS attacks can read localStorage. Mitigations applied:
    // - Access token is short-lived (15 min)
    // - CSP headers block inline/external script injection
    // - Refresh token rotation invalidates stolen tokens quickly

    logout: async () => {
        const refreshToken = localStorage.getItem('refreshToken');
        if (refreshToken) {
            try {
                await axiosClient.post('/auth/logout', { refreshToken });
            } catch (e) {
                // Ignore errors during logout — token may already be expired
            }
        }
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('user');
    },
};

export default authService;
