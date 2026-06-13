import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, WritingPrompt, EssayGradingResult, PaginatedResult } from './types';

interface GetPromptsParams {
    page: number;
    size: number;
    essayType?: string;
}

const writingApi = {
    getPrompts: (essayType?: string, page = 0, size = 20): Promise<AxiosResponse<ApiResponse<PaginatedResult<WritingPrompt>>>> => {
        const params: GetPromptsParams = { page, size };
        if (essayType) params.essayType = essayType;
        return axiosClient.get('/writing/prompts', { params });
    },

    getPromptById: (promptId: number | string): Promise<AxiosResponse<ApiResponse<WritingPrompt>>> =>
        axiosClient.get(`/writing/prompts/${promptId}`),

    gradeEssay: (promptId: number | string, essayText: string): Promise<AxiosResponse<ApiResponse<EssayGradingResult>>> =>
        axiosClient.post('/writing/grade', { promptId, essayText }),

    getHistory: (page = 0, size = 10): Promise<AxiosResponse<ApiResponse<PaginatedResult<any>>>> =>
        axiosClient.get('/writing/history', { params: { page, size } }),

    getSubmission: (submissionId: number | string): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get(`/writing/submissions/${submissionId}`),

    assembleMockTest: (): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get('/writing/assemble'),

    submitFullWriting: (data: any): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.post('/writing/submit-full', data),

    generateMockTest: (data: { topic?: string; difficulty: string; moduleType?: string }): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.post('/writing/generate-mock', data),

    getFullHistory: (): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get('/writing/full-history'),

    getFullSubmission: (id: number | string): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get(`/writing/full-submissions/${id}`),
};

export default writingApi;
