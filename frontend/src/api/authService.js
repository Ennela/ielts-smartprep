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

    logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
    },
};

export default authService;
