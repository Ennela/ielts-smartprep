import axiosClient from './axiosClient';

const readingApi = {
    generateQuiz: (topic, difficulty) =>
        axiosClient.post('/reading/generate', { topic, difficulty }),

    getQuiz: (quizId) =>
        axiosClient.get(`/reading/${quizId}`),

    submitQuiz: (quizId, answers) =>
        axiosClient.post(`/reading/${quizId}/submit`, { answers }),

    getHistory: (page = 0, size = 10) =>
        axiosClient.get('/reading/history', { params: { page, size } }),
};

export default readingApi;
