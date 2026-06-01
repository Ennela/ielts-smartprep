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

    listReadingQuizzes: (topic, difficulty, source, page = 0, size = 10) => {
        const params = { page, size };
        if (topic) params.topic = topic;
        if (difficulty) params.difficulty = difficulty;
        if (source) params.source = source;
        return axiosClient.get('/admin/reading-quizzes', { params });
    },

    createReadingQuiz: (data) =>
        axiosClient.post('/admin/reading-quizzes', data),

    updateReadingQuiz: (quizId, data) =>
        axiosClient.put(`/admin/reading-quizzes/${quizId}`, data),

    deleteReadingQuiz: (quizId) =>
        axiosClient.delete(`/admin/reading-quizzes/${quizId}`),

    listMockTests: (page = 0, size = 10) =>
        axiosClient.get('/admin/mock-tests', { params: { page, size } }),

    createMockTest: (data) =>
        axiosClient.post('/admin/mock-tests', data),

    updateMockTest: (id, data) =>
        axiosClient.put(`/admin/mock-tests/${id}`, data),

    deleteMockTest: (id) =>
        axiosClient.delete(`/admin/mock-tests/${id}`),
};

export default adminApi;
