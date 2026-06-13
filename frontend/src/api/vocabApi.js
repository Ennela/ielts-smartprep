import axiosClient from './axiosClient';

const vocabApi = {
    addVocab: (data) =>
        axiosClient.post('/vocab', data),

    getDueVocab: (page = 0, size = 10) =>
        axiosClient.get(`/vocab/due?page=${page}&size=${size}`),

    getStats: () =>
        axiosClient.get('/vocab/stats'),

    getAllVocab: () =>
        axiosClient.get('/vocab'),

    reviewVocab: (vocabId, grade) =>
        axiosClient.post(`/vocab/${vocabId}/review`, { grade }),

    aiSuggestVocab: (skillType, sourceId) =>
        axiosClient.post('/vocab/ai-suggest', { skillType, sourceId }),

    bulkSaveVocab: (vocabularies) =>
        axiosClient.post('/vocab/bulk-save', { vocabularies }),

    deleteVocab: (vocabId) =>
        axiosClient.delete(`/vocab/${vocabId}`),
};

export default vocabApi;
