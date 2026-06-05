import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, StatsOverview, ScoreTrend, PaginatedResult, HistoryItem } from './types';

interface GetHistoryParams {
  page: number;
  size: number;
  skill?: string;
}

const statsApi = {
    getOverview: (): Promise<AxiosResponse<ApiResponse<StatsOverview>>> =>
        axiosClient.get('/stats/overview'),

    getScoreTrend: (skill = 'READING', period = 'MONTHLY'): Promise<AxiosResponse<ApiResponse<ScoreTrend>>> =>
        axiosClient.get('/stats/trend', { params: { skill, period } }),

    getHistory: (skill?: string, page = 0, size = 10): Promise<AxiosResponse<ApiResponse<PaginatedResult<HistoryItem>>>> => {
        const params: GetHistoryParams = { page, size };
        if (skill) params.skill = skill;
        return axiosClient.get('/stats/history', { params });
    },

    // Review detail APIs
    getHistoryDetail: (historyId: number | string): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get(`/history/${historyId}/answers`),

    explainAnswer: (historyId: number | string, answerId: number | string): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.post(`/history/${historyId}/answers/${answerId}/explain`),
};

export default statsApi;
