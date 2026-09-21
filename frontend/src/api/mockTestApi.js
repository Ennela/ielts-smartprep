import axiosClient from './axiosClient';

const mockTestApi = {
  getAllMockTests: () => 
    axiosClient.get('/mock-tests'),

  startMockTest: (id) => 
    axiosClient.post('/mock-tests', { mockTestId: id }),

  getCurrentSession: () => 
    axiosClient.get('/mock-tests/sessions/current'),

  getSession: (sessionId) =>
    axiosClient.get(`/mock-tests/${sessionId}`),

  saveProgress: (sessionId, currentSection, timeRemainingSeconds, progressJson) => 
    axiosClient.put(`/mock-tests/sessions/${sessionId}/progress`, {
      currentSection,
      timeRemainingSeconds,
      progressJson
    }),

  nextSection: (sessionId, currentSection, timeRemainingSeconds, progressJson) => 
    axiosClient.post(`/mock-tests/${sessionId}/submit-section`, {
      currentSection,
      timeRemainingSeconds,
      progressJson
    }),

  submitExam: (sessionId, progressJson) => 
    axiosClient.post(`/mock-tests/${sessionId}/finish`, {
      progressJson
    }),

  abandonSession: (sessionId) =>
    axiosClient.post(`/mock-tests/${sessionId}/abandon`),

  getSubmission: (submissionId) => 
    axiosClient.get(`/mock-tests/submissions/${submissionId}`),

  getGradingStatus: (submissionId) => 
    axiosClient.get(`/mock-tests/submissions/${submissionId}/status`),

  getAnalytics: (submissionId) =>
    axiosClient.get(`/mock-tests/submissions/${submissionId}/analytics`),

  regradeWriting: (submissionId) =>
    axiosClient.post(`/mock-tests/submissions/${submissionId}/regrade`),

  /**
   * One page of the user's mock test history; the rows are under `content`.
   * @returns {Promise<import('axios').AxiosResponse<import('./types').ApiResponse<import('./types').SpringPage<import('./types').MockTestHistoryItem>>>>}
   */
  getHistory: (page = 0, size = 10) =>
    axiosClient.get('/mock-tests/history', { params: { page, size } })
};

export default mockTestApi;
