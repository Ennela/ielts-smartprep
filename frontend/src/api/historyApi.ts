import type { AxiosResponse } from 'axios';
import axiosClient from './axiosClient';
import type { ApiResponse, HistoryFeedItem, SpringPage } from './types';

export interface HistoryFeedParams {
  /** READING, LISTENING, WRITING or MOCK_TEST; omit for every skill */
  skill?: string;
  /** ISO date-time; only sittings submitted at or after it */
  from?: string;
  /** case-insensitive substring of the row's title */
  q?: string;
  page?: number;
  size?: number;
}

const historyApi = {
  /** One page of the user's merged history across all four skills, newest first. */
  getFeed: (params: HistoryFeedParams = {}): Promise<AxiosResponse<ApiResponse<SpringPage<HistoryFeedItem>>>> =>
    axiosClient.get('/history', { params }),
};

export default historyApi;
