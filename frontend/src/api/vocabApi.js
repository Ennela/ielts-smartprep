import axiosClient from './axiosClient';

const vocabApi = {
    addVocab: (data) =>
        axiosClient.post('/vocab', data),

    getDueVocab: (page = 0, size = 10) =>
        axiosClient.get(`/vocab/due?page=${page}&size=${size}`),

    getStats: () =>
        axiosClient.get('/vocab/stats'),

    /**
     * One page of the user's words. Filtering happens in SQL now; `q` matches the
     * word, its Vietnamese meaning or its part of speech, and 'ALL' is no filter.
     * @returns {Promise<import('axios').AxiosResponse<import('./types').ApiResponse<import('./types').SpringPage<any>>>>}
     */
    getVocab: ({ q, cefr, skill, page = 0, size = 20 } = {}) =>
        axiosClient.get('/vocab', {
            params: {
                page,
                size,
                ...(q ? { q } : {}),
                ...(cefr && cefr !== 'ALL' ? { cefr } : {}),
                ...(skill && skill !== 'ALL' ? { skill } : {}),
            },
        }),

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
