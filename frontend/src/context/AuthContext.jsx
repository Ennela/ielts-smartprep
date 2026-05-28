import { createContext, useContext, useState, useEffect } from 'react';
import authService from '../api/authService';
import axiosClient from '../api/axiosClient';

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
          localStorage.removeItem('token');
          localStorage.removeItem('user');
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (username, password) => {
    const res = await authService.login(username, password);
    const data = res.data.data;
    localStorage.setItem('token', data.token);
    setUser(data);
    return data;
  };

  const register = async (email, username, password) => {
    const res = await authService.register(email, username, password);
    const data = res.data.data;
    localStorage.setItem('token', data.token);
    setUser(data);
    return data;
  };

  const logout = () => {
    authService.logout();
    setUser(null);
  };

  const updateUser = async (data) => {
    const res = await authService.updateProfile(data);
    const updated = res.data.data;
    setUser((prev) => ({ ...prev, ...updated }));
    return updated;
  };

  return (
    <AuthContext.Provider value={{ user, loading, isAuthenticated: !!user, login, register, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
