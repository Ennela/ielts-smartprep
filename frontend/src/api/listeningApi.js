import axiosClient from './axiosClient';

const listeningApi = {
    getParts: (partNumber, topic) => {
        const params = {};
        if (partNumber) params.partNumber = partNumber;
        if (topic) params.topic = topic;
        return axiosClient.get('/listening/parts', { params });
    },

    getAllParts: () =>
        axiosClient.get('/listening/parts'),

    getPartById: (partId) =>
        axiosClient.get(`/listening/parts/${partId}`),

    startMockTest: () =>
        axiosClient.post('/listening/mock-test/start'),

    assembleMockTest: () =>
        axiosClient.get('/listening/mock-test'),

    submitTest: (testMode, partIds, answers, attemptId, autoSubmitted) =>
        axiosClient.post('/listening/submit', {
            testMode, partIds, answers,
            attemptId: attemptId || undefined,
            autoSubmitted: autoSubmitted || false,
        }),

    submitMockTest: (testId, answers) =>
        axiosClient.post(`/listening/mock-test/${testId}/submit`, { answers }),

    submitPractice: (partId, answers) =>
        axiosClient.post('/listening/practice/submit', { partId, answers }),

    getHistory: () =>
        axiosClient.get('/listening/history'),

    analyzeQuestion: (questionId) =>
        axiosClient.post(`/listening/ai-analyze/${questionId}`),

    extractVocabulary: (partId) =>
        axiosClient.post(`/listening/vocabulary/${partId}`),

    generatePart: (partNumber, topic) =>
        axiosClient.post('/listening/generate', { partNumber, topic }),

    generateMockTest: (topic) =>
        axiosClient.post('/listening/generate-mock', { topic }),
};

export default listeningApi;
