import { createContext, useContext, useState, useEffect, useCallback } from 'react';

const ThemeContext = createContext(null);

const STORAGE_KEY = 'smartprep-theme';

/**
 * Resolves 'system' preference to actual 'light' or 'dark' based on OS setting.
 */
function resolveTheme(preference) {
  if (preference === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  return preference === 'dark' ? 'dark' : 'light';
}

/**
 * Applies the resolved theme to the <html> element via data-theme attribute.
 */
function applyTheme(resolved) {
  if (resolved === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark');
  } else {
    document.documentElement.removeAttribute('data-theme');
  }
}

export function ThemeProvider({ children }) {
  // Initialize from localStorage (already applied by inline script for FOUC prevention)
  const [theme, setThemeState] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'dark' || stored === 'system') return stored;
    return 'light';
  });

  const resolved = resolveTheme(theme);

  // Apply theme to DOM whenever it changes
  useEffect(() => {
    applyTheme(resolved);
  }, [resolved]);

  // Listen for OS color scheme changes when in 'system' mode
  useEffect(() => {
    if (theme !== 'system') return;

    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => {
      applyTheme(resolveTheme('system'));
    };
    mql.addEventListener('change', handler);
    return () => mql.removeEventListener('change', handler);
  }, [theme]);

  // Multi-tab sync via storage event
  useEffect(() => {
    const handler = (e) => {
      if (e.key === STORAGE_KEY && e.newValue) {
        const newTheme = e.newValue;
        setThemeState(newTheme);
        applyTheme(resolveTheme(newTheme));
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, []);

  const setTheme = useCallback((newTheme) => {
    const valid = ['light', 'dark', 'system'].includes(newTheme) ? newTheme : 'light';
    setThemeState(valid);
    localStorage.setItem(STORAGE_KEY, valid);
    applyTheme(resolveTheme(valid));
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme: resolved, setTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};
