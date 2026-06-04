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

  getSubmission: (submissionId) => 
    axiosClient.get(`/mock-tests/submissions/${submissionId}`),

  getGradingStatus: (submissionId) => 
    axiosClient.get(`/mock-tests/submissions/${submissionId}/status`),

  getHistory: () => 
    axiosClient.get('/mock-tests/history')
};

export default mockTestApi;
