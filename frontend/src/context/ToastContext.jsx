import { createContext, useContext, useState, useCallback } from 'react';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const show = useCallback((message, type = 'info', duration = 4000) => {
    const id = Date.now() + Math.random().toString(36).substring(2, 9);
    setToasts(prev => [...prev, { id, message, type, duration }]);
    
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, duration);
  }, []);

  const success = useCallback((msg, dur) => show(msg, 'success', dur), [show]);
  const error = useCallback((msg, dur) => show(msg, 'error', dur), [show]);
  const info = useCallback((msg, dur) => show(msg, 'info', dur), [show]);
  const warning = useCallback((msg, dur) => show(msg, 'warning', dur), [show]);

  const remove = useCallback((id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ success, error, info, warning }}>
      {children}
      {/* Toast container overlay */}
      <div className="fixed top-5 right-5 z-[9999] flex flex-col gap-sm max-w-sm w-full pointer-events-none px-4 md:px-0">
        {toasts.map(t => (
          <div
            key={t.id}
            className={`
              pointer-events-auto flex items-start gap-md p-md rounded-xl shadow-ambient border transition-all duration-300 animate-slide-in
              ${t.type === 'success' ? 'bg-white border-emerald/20 text-on-surface' : ''}
              ${t.type === 'error' ? 'bg-white border-error/20 text-on-surface' : ''}
              ${t.type === 'warning' ? 'bg-white border-tertiary/20 text-on-surface' : ''}
              ${t.type === 'info' ? 'bg-white border-outline-variant/30 text-on-surface' : ''}
            `}
          >
            {/* Icons matching Google Material Symbols */}
            {t.type === 'success' && (
              <span className="material-symbols-outlined text-emerald text-[24px]" style={{ fontVariationSettings: "'FILL' 1" }}>check_circle</span>
            )}
            {t.type === 'error' && (
              <span className="material-symbols-outlined text-error text-[24px]" style={{ fontVariationSettings: "'FILL' 1" }}>error</span>
            )}
            {t.type === 'warning' && (
              <span className="material-symbols-outlined text-tertiary text-[24px]" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
            )}
            {t.type === 'info' && (
              <span className="material-symbols-outlined text-primary text-[24px]">info</span>
            )}
            
            <div className="flex-1 flex flex-col gap-xs">
              <span className="text-body-md font-medium">{t.message}</span>
            </div>

            <button 
              onClick={() => remove(t.id)}
              className="text-outline hover:text-on-surface-variant transition-colors flex-shrink-0"
            >
              <span className="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context;
};
