import axiosClient from './axiosClient';

const analyticsApi = {
    getOverview: () =>
        axiosClient.get('/analytics/overview'),

    getScoreTrend: (skill) =>
        axiosClient.get('/analytics/score-trend', { params: { skill } }),

    getWeakness: (skill) => {
        const params = {};
        if (skill) params.skill = skill;
        return axiosClient.get('/analytics/weakness', { params });
    }
};

export default analyticsApi;
