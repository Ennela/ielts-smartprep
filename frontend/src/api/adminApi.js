import axiosClient from './axiosClient';

const DEFAULT_SIZE = 20;

const adminApi = {
    getDashboard: () =>
        axiosClient.get('/admin/dashboard'),

    listUsers: (search, page = 0, size = DEFAULT_SIZE, sort = 'createdAt,desc') => {
        const params = { page, size, sort };
        if (search) params.search = search;
        return axiosClient.get('/admin/users', { params });
    },

    getUserDetail: (userId) =>
        axiosClient.get(`/admin/users/${userId}`),

    listWritingPrompts: (essayType, page = 0, size = DEFAULT_SIZE, sort = 'createdAt,desc') => {
        const params = { page, size, sort };
        if (essayType) params.essayType = essayType;
        return axiosClient.get('/admin/writing-prompts', { params });
    },

    createWritingPrompt: (data) =>
        axiosClient.post('/admin/writing-prompts', data),

    updateWritingPrompt: (promptId, data) =>
        axiosClient.put(`/admin/writing-prompts/${promptId}`, data),

    deleteWritingPrompt: (promptId) =>
        axiosClient.delete(`/admin/writing-prompts/${promptId}`),

    listReadingQuizzes: (topic, difficulty, source, page = 0, size = DEFAULT_SIZE, sort = 'createdAt,desc') => {
        const params = { page, size, sort };
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

    listMockTests: (page = 0, size = DEFAULT_SIZE, sort = 'createdAt,desc') =>
        axiosClient.get('/admin/mock-tests', { params: { page, size, sort } }),

    createMockTest: (data) =>
        axiosClient.post('/admin/mock-tests', data),

    updateMockTest: (id, data) =>
        axiosClient.put(`/admin/mock-tests/${id}`, data),

    deleteMockTest: (id) =>
        axiosClient.delete(`/admin/mock-tests/${id}`),

    listListeningParts: (audioStatus, topic, page = 0, size = DEFAULT_SIZE, sort = 'createdAt,desc') => {
        const params = { page, size, sort };
        if (audioStatus) params.audioStatus = audioStatus;
        if (topic) params.topic = topic;
        return axiosClient.get('/admin/listening/parts', { params });
    },

    getListeningPartById: (id) =>
        axiosClient.get(`/admin/listening/parts/${id}`),

    createListeningPart: (data) =>
        axiosClient.post('/admin/listening/parts', data),

    updateListeningPart: (id, data) =>
        axiosClient.put(`/admin/listening/parts/${id}`, data),

    deleteListeningPart: (id) =>
        axiosClient.delete(`/admin/listening/parts/${id}`),

    regenerateListeningAudio: (id) =>
        axiosClient.post(`/admin/listening/parts/${id}/regenerate-audio`),

    retryFailedListeningAudio: () =>
        axiosClient.post('/admin/listening/parts/retry-failed-audio'),

    getListeningStats: () =>
        axiosClient.get('/admin/listening/stats'),

    getReadingQuizPreview: (quizId) =>
        axiosClient.get(`/admin/reading/${quizId}/preview`),

    getWritingPromptPreview: (promptId) =>
        axiosClient.get(`/admin/writing/${promptId}/preview`),

    getListeningPartPreview: (partId) =>
        axiosClient.get(`/admin/listening/${partId}/preview`),
};

export default adminApi;
