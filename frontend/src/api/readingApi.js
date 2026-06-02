import axiosClient from './axiosClient';

const readingApi = {
    generateQuiz: (topic, difficulty) =>
        axiosClient.post('/reading/generate', { topic, difficulty }),

    getQuiz: (quizId) =>
        axiosClient.get(`/reading/${quizId}`),

    getResult: (quizId) =>
        axiosClient.get(`/reading/${quizId}/result`),

    submitQuiz: (quizId, answers) =>
        axiosClient.post(`/reading/${quizId}/submit`, { answers }),

    getHistory: (page = 0, size = 10) =>
        axiosClient.get('/reading/history', { params: { page, size } }),

    getTemplates: (topic, difficulty, page = 0, size = 10) => {
        const params = { page, size };
        if (topic) params.topic = topic;
        if (difficulty) params.difficulty = difficulty;
        return axiosClient.get('/reading/templates', { params });
    },

    startTemplateQuiz: (templateId) =>
        axiosClient.post(`/reading/templates/${templateId}/start`),

    assembleMockTest: () =>
        axiosClient.get('/reading/assemble'),

    submitFullQuiz: (quizIds, answers) =>
        axiosClient.post('/reading/submit-full', { quizIds, answers }),
};

export default readingApi;
