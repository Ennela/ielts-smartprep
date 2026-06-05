import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, WeaknessAnalysis } from './types';

interface WeaknessParams {
  skill?: string;
}

const analyticsApi = {
    getOverview: (): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get('/analytics/overview'),

    getScoreTrend: (skill: string): Promise<AxiosResponse<ApiResponse<any>>> =>
        axiosClient.get('/analytics/score-trend', { params: { skill } }),

    getWeakness: (skill?: string): Promise<AxiosResponse<ApiResponse<WeaknessAnalysis>>> => {
        const params: WeaknessParams = {};
        if (skill) params.skill = skill;
        return axiosClient.get('/analytics/weakness', { params });
    }
};

export default analyticsApi;
