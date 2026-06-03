import axiosClient from './axiosClient';

const statsApi = {
    getOverview: () =>
        axiosClient.get('/stats/overview'),

    getScoreTrend: (skill = 'READING', period = 'MONTHLY') =>
        axiosClient.get('/stats/trend', { params: { skill, period } }),

    getHistory: (skill, page = 0, size = 10) => {
        const params = { page, size };
        if (skill) params.skill = skill;
        return axiosClient.get('/stats/history', { params });
    },

    // Review detail APIs
    getHistoryDetail: (historyId) =>
        axiosClient.get(`/history/${historyId}/answers`),

    explainAnswer: (historyId, answerId) =>
        axiosClient.post(`/history/${historyId}/answers/${answerId}/explain`),
};

export default statsApi;
