import axios from 'axios';
import type { InternalAxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';

interface FailedRequest {
  resolve: (token: string) => void;
  reject: (error: any) => void;
}

interface EnrichedError extends Error {
  status?: number;
  response?: AxiosResponse;
  /** What the page may show the user; undefined when the page's own wording fits better. */
  userMessage?: string;
}

// Error codes whose message the backend writes for the person using the app (see
// GlobalExceptionHandler). Every other message -- a framework reason naming a parameter or a
// content type, "Internal server error", "X not found: 12" -- is for developers.
const USER_FACING_CODES = new Set([
  'BAD_REQUEST', 'VALIDATION_ERROR', 'WORD_COUNT_TOO_LOW', 'ACCOUNT_LOCKED',
  'ACCOUNT_SUSPENDED', 'INVALID_TOKEN', 'RATE_LIMIT_EXCEEDED',
]);

function userMessageFor(error: AxiosError<any>): string | undefined {
  if (!error.response) {
    if (error.code === 'ERR_CANCELED') return undefined;
    return error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
      ? 'The server took too long to respond. Please try again.'
      : 'Could not reach the server. Check your internet connection and try again.';
  }
  const { status, data } = error.response;
  if (typeof data?.message === 'string' && data.message && USER_FACING_CODES.has(data.errorCode)) {
    return data.message;
  }
  if (status === 401) return 'Your session has expired. Please log in again.';
  if (status === 403) return 'You do not have permission to do that.';
  if (status === 413) return 'The file is too large to upload.';
  if (status === 429) return 'Too many requests. Please wait a moment and try again.';
  if (status === 502 || status === 503 || status === 504) {
    return 'The service is busy right now. Please try again in a few minutes.';
  }
  return undefined;
}

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

// ── Request interceptor: attach access token ────────────────────────────
axiosClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token');
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Response interceptor: auto-refresh on 401 ───────────────────────────
let isRefreshing = false;
let failedQueue: FailedRequest[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error);
    } else {
      resolve(token!);
    }
  });
  failedQueue = [];
};

axiosClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError<any>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Only attempt refresh for 401 errors, not on auth endpoints themselves
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/login') &&
      !originalRequest.url?.includes('/auth/refresh') &&
      !originalRequest.url?.includes('/auth/register')
    ) {
      if (isRefreshing) {
        // Another refresh is in-flight — queue this request
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${token}`;
            }
            return axiosClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const currentAccessToken = localStorage.getItem('token');
      if (!currentAccessToken) {
        isRefreshing = false;
        clearAuthAndRedirect();
        return Promise.reject(error);
      }

      try {
        const res = await axios.post(
          (import.meta.env.VITE_API_URL || '/api/v1') + '/auth/refresh',
          {},
          { headers: { 'Content-Type': 'application/json' }, withCredentials: true }
        );

        const { token: newAccessToken } = res.data.data;
        if (!newAccessToken) {
          throw new Error('Refresh response missing access token');
        }
        localStorage.setItem('token', newAccessToken);
        localStorage.removeItem('refreshToken');

        if (axiosClient.defaults.headers.common) {
          axiosClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`;
        }
        processQueue(null, newAccessToken);

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        }
        return axiosClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearAuthAndRedirect();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // Extract structured error message from backend
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'An unexpected error occurred';
    const enrichedError: EnrichedError = new Error(message);
    enrichedError.status = error.response?.status;
    enrichedError.response = error.response;
    enrichedError.userMessage = userMessageFor(error);
    return Promise.reject(enrichedError);
  }
);

function clearAuthAndRedirect() {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');
  const currentPath = window.location.pathname + window.location.search;
  if (currentPath === '/login') {
    return;
  }
  if (
    currentPath &&
    currentPath !== '/' &&
    !currentPath.includes('/login') &&
    !currentPath.includes('/register') &&
    !currentPath.includes('/forgot-password') &&
    !currentPath.includes('/reset-password') &&
    !currentPath.includes('/verify-email')
  ) {
    window.location.href = `/login?redirect=${encodeURIComponent(currentPath)}`;
  } else {
    window.location.href = '/login';
  }
}

export default axiosClient;
