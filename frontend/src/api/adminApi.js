import axiosClient from './axiosClient';

const adminApi = {
    getDashboard: () =>
        axiosClient.get('/admin/dashboard'),

    listUsers: (search, page = 0, size = 10) => {
        const params = { page, size };
        if (search) params.search = search;
        return axiosClient.get('/admin/users', { params });
    },

    getUserDetail: (userId) =>
        axiosClient.get(`/admin/users/${userId}`),

    listWritingPrompts: (essayType, page = 0, size = 10) => {
        const params = { page, size };
        if (essayType) params.essayType = essayType;
        return axiosClient.get('/admin/writing-prompts', { params });
    },

    createWritingPrompt: (data) =>
        axiosClient.post('/admin/writing-prompts', data),

    updateWritingPrompt: (promptId, data) =>
        axiosClient.put(`/admin/writing-prompts/${promptId}`, data),

    deleteWritingPrompt: (promptId) =>
        axiosClient.delete(`/admin/writing-prompts/${promptId}`),
};

export default adminApi;
