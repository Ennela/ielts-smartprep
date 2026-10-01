import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { SpringPage, ApiResponse, WritingPrompt, WritingGradeResult, WritingHistoryItem, WritingFullResult } from './types';

const writingApi = {
    // GET /writing/prompts returns the whole list (WritingController#getPrompts reads
    // only essayType); there is no paging to ask for.
    getPrompts: (essayType?: string): Promise<AxiosResponse<ApiResponse<WritingPrompt[]>>> =>
        axiosClient.get('/writing/prompts', { params: essayType ? { essayType } : {} }),

    getPromptById: (promptId: number | string): Promise<AxiosResponse<ApiResponse<WritingPrompt>>> =>
        axiosClient.get(`/writing/prompts/${promptId}`),

    /**
     * autoSubmitted: the timer ran out, so the server marks it even if it is short.
     * attemptId: the attempt timing the essay, whose deadline the server holds it to.
     */
    gradeEssay: (promptId: number | string, essayText: string, autoSubmitted = false, attemptId?: number | null): Promise<AxiosResponse<ApiResponse<WritingGradeResult>>> =>
        axiosClient.post('/writing/grade', { promptId, essayText, autoSubmitted, attemptId: attemptId || undefined }),

    getHistory: (page = 0, size = 10): Promise<AxiosResponse<ApiResponse<SpringPage<WritingHistoryItem>>>> =>
        axiosClient.get('/writing/history', { params: { page, size } }),

    getSubmission: (submissionId: number | string): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get(`/writing/submissions/${submissionId}`),

    assembleMockTest: (): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get('/writing/assemble'),

    submitFullWriting: (data: any): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.post('/writing/submit-full', data),

    generateMockTest: (data: { topic?: string; difficulty: string; moduleType?: string }): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.post('/writing/generate-mock', data),

    getFullHistory: (page = 0, size = 10): Promise<AxiosResponse<ApiResponse<SpringPage<WritingFullResult>>>> =>
        axiosClient.get('/writing/full-history', { params: { page, size } }),

    getFullSubmission: (id: number | string): Promise<AxiosResponse<ApiResponse<WritingFullResult>>> =>
        axiosClient.get(`/writing/full-submissions/${id}`),
};

export default writingApi;
