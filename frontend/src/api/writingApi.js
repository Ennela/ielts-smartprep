import axiosClient from './axiosClient';

const writingApi = {
    getPrompts: (essayType, page = 0, size = 20) => {
        const params = { page, size };
        if (essayType) params.essayType = essayType;
        return axiosClient.get('/writing/prompts', { params });
    },

    getPromptById: (promptId) =>
        axiosClient.get(`/writing/prompts/${promptId}`),

    gradeEssay: (promptId, essayText) =>
        axiosClient.post('/writing/grade', { promptId, essayText }),

    getHistory: (page = 0, size = 10) =>
        axiosClient.get('/writing/history', { params: { page, size } }),

    getSubmission: (submissionId) =>
        axiosClient.get(`/writing/submissions/${submissionId}`),
};

export default writingApi;
