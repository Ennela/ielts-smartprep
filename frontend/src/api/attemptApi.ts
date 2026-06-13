import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, StartAttemptRequest, CompleteAttemptRequest, AttemptResponse } from './types';

/**
 * API client for exam attempt lifecycle (server-authoritative timer).
 */
const attemptApi = {
    /**
     * Start a new exam attempt or resume an existing one.
     */
    startAttempt: (data: StartAttemptRequest): Promise<AxiosResponse<ApiResponse<AttemptResponse>>> =>
        axiosClient.post('/attempts/start', data),

    /**
     * Get an existing attempt (for resume on page reload).
     */
    getAttempt: (attemptId: number | string): Promise<AxiosResponse<ApiResponse<AttemptResponse>>> =>
        axiosClient.get(`/attempts/${attemptId}`),

    /**
     * Complete an attempt (mark as submitted).
     */
    completeAttempt: (attemptId: number | string, data?: CompleteAttemptRequest): Promise<AxiosResponse<ApiResponse<AttemptResponse>>> =>
        axiosClient.post(`/attempts/${attemptId}/complete`, data || {}),
};

export default attemptApi;
