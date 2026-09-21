import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import authService, { clearAllAuthData } from '../api/authService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  // Set when GET /auth/me failed for a reason other than an invalid session
  // (network down, 5xx). The token is kept so the user is not logged out for
  // an outage that is not theirs; ProtectedRoute offers a retry instead.
  const [profileError, setProfileError] = useState(null);

  const loadProfile = useCallback(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      setLoading(false);
      return;
    }
    setLoading(true);
    setProfileError(null);
    authService.getProfile()
      .then((res) => setUser(res.data.data))
      .catch((err) => {
        const status = err.status ?? err.response?.status;
        if (status === 401 || status === 403) {
          clearAllAuthData();
        } else {
          setProfileError(err);
        }
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  // ── Cross-tab logout sync ──────────────────────────────────────────────
  // When another tab clears the access token (via logout or refresh failure),
  // this tab detects the storage event and resets its own auth state.
  useEffect(() => {
    const handleStorageChange = (e) => {
      if (e.key === 'token' && e.newValue === null) {
        setUser(null);
      }
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  const login = async (username, password) => {
    const res = await authService.login(username, password);
    const data = res.data.data;
    localStorage.setItem('token', data.token);
    localStorage.removeItem('refreshToken');
    setUser(data);
    return data;
  };

  const register = async (email, username, password) => {
    const res = await authService.register(email, username, password);
    const data = res.data.data;
    localStorage.setItem('token', data.token);
    localStorage.removeItem('refreshToken');
    setUser(data);
    return data;
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
  };

  const updateUser = async (data) => {
    const res = await authService.updateProfile(data);
    const updated = res.data.data;
    setUser((prev) => ({ ...prev, ...updated }));
    return updated;
  };

  const isAdmin = user?.role === 'ADMIN';
  const emailVerified = user?.emailVerified ?? false;

  return (
    <AuthContext.Provider value={{
      user, loading, isAuthenticated: !!user, isAdmin, emailVerified,
      profileError, retryProfile: loadProfile,
      login, register, logout, updateUser
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
