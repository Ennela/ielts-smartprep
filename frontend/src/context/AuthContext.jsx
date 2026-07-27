import { createContext, useContext, useState, useEffect } from 'react';
import authService, { clearAllAuthData } from '../api/authService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      authService.getProfile()
        .then((res) => setUser(res.data.data))
        .catch(() => {
          clearAllAuthData();
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

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
      login, register, logout, updateUser
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
