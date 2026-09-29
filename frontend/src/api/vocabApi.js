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

    /**
     * The stored context-aware explanation for one word. Reads the row, so it costs
     * nothing and is safe to call whenever the learner opens a word.
     */
    getInsight: (vocabId) =>
        axiosClient.get(`/vocab/${vocabId}/insight`),

    /**
     * Asks the server to generate the explanation. Spends an AI call and is rate limited,
     * so the page only calls it when the learner asked for an explanation that is not there
     * yet, or explicitly asked for a new one.
     */
    generateInsight: (vocabId, refresh = false) =>
        axiosClient.post(`/vocab/${vocabId}/insight/generate`, null, { params: { refresh } }),

    deleteVocab: (vocabId) =>
        axiosClient.delete(`/vocab/${vocabId}`),
};

export default vocabApi;
