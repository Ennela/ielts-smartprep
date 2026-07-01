import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, Quiz, QuizSubmissionResult, PaginatedResult } from './types';

interface GetTemplatesParams {
  page: number;
  size: number;
  topic?: string;
  difficulty?: string;
}

const readingApi = {
    generateQuiz: (topic: string, difficulty: string, passageCount?: number, moduleType?: string): Promise<AxiosResponse<ApiResponse<Quiz>>> =>
        axiosClient.post('/reading/generate', { topic, difficulty, passageCount, moduleType }),

    getQuiz: (quizId: number | string): Promise<AxiosResponse<ApiResponse<Quiz>>> =>
        axiosClient.get(`/reading/${quizId}`),

    getResult: (quizId: number | string): Promise<AxiosResponse<ApiResponse<QuizSubmissionResult>>> =>
        axiosClient.get(`/reading/${quizId}/result`),

    submitQuiz: (quizId: number | string, answers: Record<number, string>, attemptId?: number | null, autoSubmitted?: boolean): Promise<AxiosResponse<ApiResponse<QuizSubmissionResult>>> =>
        axiosClient.post(`/reading/${quizId}/submit`, { answers, attemptId: attemptId || undefined, autoSubmitted: autoSubmitted || false }),

    getHistory: (page = 0, size = 10): Promise<AxiosResponse<ApiResponse<PaginatedResult<any>>>> =>
        axiosClient.get('/reading/history', { params: { page, size } }),

    getTemplates: (topic?: string, difficulty?: string, page = 0, size = 10): Promise<AxiosResponse<ApiResponse<PaginatedResult<any>>>> => {
        const params: GetTemplatesParams = { page, size };
        if (topic) params.topic = topic;
        if (difficulty) params.difficulty = difficulty;
        return axiosClient.get('/reading/templates', { params });
    },

    startTemplateQuiz: (templateId: number | string): Promise<AxiosResponse<ApiResponse<Quiz>>> =>
        axiosClient.post(`/reading/templates/${templateId}/start`),

    assembleMockTest: (): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get('/reading/assemble'),

    submitFullQuiz: (quizIds: number[], answers: Record<number, string>, attemptId?: number | null, autoSubmitted?: boolean): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.post('/reading/submit-full', { quizIds, answers, attemptId: attemptId || undefined, autoSubmitted: autoSubmitted || false }),
};

export default readingApi;
