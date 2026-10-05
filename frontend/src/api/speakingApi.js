import axiosClient from './axiosClient';

const speakingApi = {
    /** part: 1, 2 or 3; omitted for every prompt. */
    getPrompts: (part) =>
        axiosClient.get('/speaking/prompts', { params: part ? { part } : {} }),

    /**
     * Sends the recordings; the server grades them with Gemini before answering.
     * recordings: [{ blob, duration }] — Part 2: one; Part 1/3: one per question, in order.
     */
    grade: (promptId, recordings) => {
        const form = new FormData();
        form.append('promptId', promptId);
        recordings.forEach(({ blob, duration }, i) => {
            form.append('durationSeconds', Math.round(duration));
            const extension = blob.type.includes('ogg') ? 'ogg' : blob.type.includes('mp4') ? 'm4a' : 'webm';
            form.append('audio', blob, `answer-${i + 1}.${extension}`);
        });
        return axiosClient.post('/speaking/grade', form);
    },

    getHistory: (page = 0, size = 10) =>
        axiosClient.get('/speaking/submissions', { params: { page, size } }),

    getSubmission: (submissionId) =>
        axiosClient.get(`/speaking/submissions/${submissionId}`),

    /** The learner's own recording, fetched with the bearer token as a Blob. */
    getRecording: (submissionId) =>
        axiosClient.get(`/speaking/submissions/${submissionId}/audio`, { responseType: 'blob' }),

    /** One Part 1/3 answer's recording; questionIndex counts from 0. */
    getAnswerRecording: (submissionId, questionIndex) =>
        axiosClient.get(`/speaking/submissions/${submissionId}/answers/${questionIndex}/audio`, { responseType: 'blob' }),
};

export default speakingApi;
