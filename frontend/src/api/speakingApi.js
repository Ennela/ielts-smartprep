import axiosClient from './axiosClient';

const speakingApi = {
    /** part: 1, 2 or 3; omitted for every prompt. */
    getPrompts: (part) =>
        axiosClient.get('/speaking/prompts', { params: part ? { part } : {} }),

    /** Sends the recording; the server grades it with Gemini before answering. */
    grade: (promptId, audioBlob, durationSeconds) => {
        const form = new FormData();
        form.append('promptId', promptId);
        form.append('durationSeconds', Math.round(durationSeconds));
        const extension = audioBlob.type.includes('ogg') ? 'ogg' : audioBlob.type.includes('mp4') ? 'm4a' : 'webm';
        form.append('audio', audioBlob, `answer.${extension}`);
        return axiosClient.post('/speaking/grade', form);
    },

    getHistory: (page = 0, size = 10) =>
        axiosClient.get('/speaking/submissions', { params: { page, size } }),

    getSubmission: (submissionId) =>
        axiosClient.get(`/speaking/submissions/${submissionId}`),

    /** The learner's own recording, fetched with the bearer token as a Blob. */
    getRecording: (submissionId) =>
        axiosClient.get(`/speaking/submissions/${submissionId}/audio`, { responseType: 'blob' }),
};

export default speakingApi;
