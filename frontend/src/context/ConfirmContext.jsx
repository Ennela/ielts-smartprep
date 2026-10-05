import { createContext, useCallback, useContext, useRef, useState } from 'react';
import Modal from '../components/common/Modal';

const ConfirmContext = createContext(null);

/**
 * App-wide confirmation dialog, in place of window.confirm: the same look as the
 * admin delete dialogs, readable by screen readers, and it does not block the page.
 *
 *   const confirm = useConfirm();
 *   if (await confirm({ title: 'Submit?', message: '…', confirmLabel: 'Submit' })) { … }
 *
 * `tone: 'danger'` colours the confirm button for destructive actions.
 */
export function ConfirmProvider({ children }) {
  const [request, setRequest] = useState(null);
  const resolveRef = useRef(null);

  const confirm = useCallback((options) => new Promise((resolve) => {
    resolveRef.current = resolve;
    setRequest(typeof options === 'string' ? { message: options } : options);
  }), []);

  const settle = (answer) => {
    resolveRef.current?.(answer);
    resolveRef.current = null;
    setRequest(null);
  };

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {request && (
        <Modal ariaLabel={request.title || 'Confirm'} onClose={() => settle(false)} style={{ maxWidth: 460 }}>
          {request.title && (
            <h2 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.1rem', fontWeight: 700, marginBottom: 12 }}>
              {request.title}
            </h2>
          )}
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.92rem', lineHeight: 1.6, whiteSpace: 'pre-line' }}>
            {request.message}
          </p>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 24, flexWrap: 'wrap' }}>
            <button type="button" className="btn btn-outline" onClick={() => settle(false)}>
              {request.cancelLabel || 'Cancel'}
            </button>
            <button
              type="button"
              className={request.tone === 'danger' ? 'btn admin-btn-danger-fill' : 'btn btn-primary'}
              onClick={() => settle(true)}
            >
              {request.confirmLabel || 'Confirm'}
            </button>
          </div>
        </Modal>
      )}
    </ConfirmContext.Provider>
  );
}

// Outside a ConfirmProvider (a page rendered on its own, as the tests do) the native
// dialog stands in, so a page never loses its confirmation step.
const nativeConfirm = async (options) =>
  window.confirm(typeof options === 'string' ? options : [options.title, options.message].filter(Boolean).join('\n\n'));

export const useConfirm = () => useContext(ConfirmContext) || nativeConfirm;
