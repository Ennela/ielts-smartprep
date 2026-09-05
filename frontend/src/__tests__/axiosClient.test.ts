import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('axiosClient auth refresh flow', () => {
  let axiosCreate: any;
  let axiosPost: any;
  let axiosInstance: any;
  let responseErrorInterceptor: any;

  beforeEach(() => {
    localStorage.clear();
    window.history.pushState({}, '', '/login');
  });

  afterEach(() => {
    vi.doUnmock('axios');
    vi.resetModules();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  async function loadAxiosClient() {
    vi.resetModules();

    responseErrorInterceptor = undefined;
    axiosPost = vi.fn();
    axiosInstance = vi.fn((config) => Promise.resolve({ data: { ok: true }, config }));
    axiosInstance.defaults = { headers: { common: {} } };
    axiosInstance.interceptors = {
      request: {
        use: vi.fn(),
      },
      response: {
        use: vi.fn((_success, error) => {
          responseErrorInterceptor = error;
        }),
      },
    };
    axiosCreate = vi.fn(() => axiosInstance);

    vi.doMock('axios', () => ({
      default: {
        create: axiosCreate,
        post: axiosPost,
      },
    }));

    const module = await import('../api/axiosClient');
    return module.default;
  }

  it('should create API client with credentials enabled for refresh cookie', async () => {
    await loadAxiosClient();

    expect(axiosCreate).toHaveBeenCalledWith(expect.objectContaining({
      withCredentials: true,
    }));
  });

  it('should refresh using the HttpOnly cookie and retry the original request', async () => {
    await loadAxiosClient();
    localStorage.setItem('token', 'old-access');
    localStorage.setItem('refreshToken', 'legacy-refresh');
    axiosPost.mockResolvedValueOnce({ data: { data: { token: 'new-access' } } });
    const originalRequest: any = { url: '/protected', headers: {} };

    await responseErrorInterceptor({
      response: { status: 401 },
      config: originalRequest,
    });

    expect(axiosPost).toHaveBeenCalledWith(
      '/api/v1/auth/refresh',
      {},
      { headers: { 'Content-Type': 'application/json' }, withCredentials: true }
    );
    expect(localStorage.getItem('token')).toBe('new-access');
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(axiosInstance.defaults.headers.common.Authorization).toBe('Bearer new-access');
    expect(originalRequest.headers.Authorization).toBe('Bearer new-access');
    expect(axiosInstance).toHaveBeenCalledWith(originalRequest);
  });

  it('should clear auth data when refresh fails', async () => {
    await loadAxiosClient();
    localStorage.setItem('token', 'old-access');
    localStorage.setItem('refreshToken', 'legacy-refresh');
    axiosPost.mockRejectedValueOnce(new Error('refresh failed'));

    await expect(responseErrorInterceptor({
      response: { status: 401 },
      config: { url: '/protected', headers: {} },
    })).rejects.toThrow('refresh failed');

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('should not attempt refresh when no access token is present', async () => {
    await loadAxiosClient();
    const error = {
      response: { status: 401 },
      config: { url: '/protected', headers: {} },
    };

    await expect(responseErrorInterceptor(error)).rejects.toBe(error);

    expect(axiosPost).not.toHaveBeenCalled();
  });

  it('should queue concurrent 401 requests behind one refresh call', async () => {
    await loadAxiosClient();
    localStorage.setItem('token', 'old-access');
    let resolveRefresh: any;
    axiosPost.mockReturnValueOnce(new Promise((resolve) => {
      resolveRefresh = resolve;
    }));
    const firstRequest: any = { url: '/first', headers: {} };
    const secondRequest: any = { url: '/second', headers: {} };

    const first = responseErrorInterceptor({
      response: { status: 401 },
      config: firstRequest,
    });
    const second = responseErrorInterceptor({
      response: { status: 401 },
      config: secondRequest,
    });

    expect(axiosPost).toHaveBeenCalledTimes(1);
    resolveRefresh({ data: { data: { token: 'new-access' } } });

    await Promise.all([first, second]);

    expect(firstRequest.headers.Authorization).toBe('Bearer new-access');
    expect(secondRequest.headers.Authorization).toBe('Bearer new-access');
    expect(axiosInstance).toHaveBeenCalledWith(firstRequest);
    expect(axiosInstance).toHaveBeenCalledWith(secondRequest);
  });
});
