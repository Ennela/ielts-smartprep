import axiosClient from './axiosClient';

const listeningApi = {
    getAllParts: () =>
        axiosClient.get('/listening/parts'),

    getPartById: (partId) =>
        axiosClient.get(`/listening/parts/${partId}`),

    assembleMockTest: () =>
        axiosClient.get('/listening/mock-test'),

    submitTest: (testMode, partIds, answers, attemptId, autoSubmitted) =>
        axiosClient.post('/listening/submit', {
            testMode, partIds, answers,
            attemptId: attemptId || undefined,
            autoSubmitted: autoSubmitted || false,
        }),

    /**
     * One page of the user's listening history; the rows are under `content`.
     * @returns {Promise<import('axios').AxiosResponse<import('./types').ApiResponse<import('./types').SpringPage<import('./types').ListeningHistoryItem>>>>}
     */
    getHistory: (page = 0, size = 12) =>
        axiosClient.get('/listening/history', { params: { page, size } }),

    getTestResult: (testId) =>
        axiosClient.get(`/listening/${testId}/result`),

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
