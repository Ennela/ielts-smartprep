import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import authService from '../api/authService';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (!token) {
      setError('Invalid or missing reset token. Please request a new password reset.');
    }
  }, [token]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    if (newPassword.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setLoading(true);
    try {
      await authService.resetPassword(token, newPassword);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 3000);
    } catch (err) {
      setError(err.message || 'Failed to reset password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-left">
        <div className="auth-left-content">
          <div className="auth-shapes">
            <div className="auth-shape auth-shape-1"></div>
            <div className="auth-shape auth-shape-2"></div>
            <div className="auth-shape auth-shape-3"></div>
          </div>
          <h1>IELTS SmartPrep</h1>
          <p>Conquer IELTS with AI</p>
          <div className="auth-features">
            <div className="auth-feature">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
              <span>Secure password reset</span>
            </div>
            <div className="auth-feature">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>
              <span>Create a strong password</span>
            </div>
          </div>
        </div>
      </div>
      <div className="auth-right">
        {success ? (
          <div className="auth-form" style={{ textAlign: 'center' }}>
            <div className="reveal" style={{ fontSize: '48px', marginBottom: '16px' }}>✅</div>
            <h2 className="reveal">Password Reset!</h2>
            <p className="reveal reveal-delay-1" style={{ color: 'var(--text-secondary)', lineHeight: '1.6', marginBottom: '24px' }}>
              Your password has been changed successfully.
              Redirecting to login...
            </p>
            <Link to="/login" className="btn btn-primary reveal reveal-delay-2" style={{ display: 'inline-block', textDecoration: 'none' }}>
              Go to Login
            </Link>
          </div>
        ) : (
          <form className="auth-form" onSubmit={handleSubmit}>
            <h2 className="reveal">Reset Password</h2>
            <p className="reveal reveal-delay-1" style={{ color: 'var(--text-secondary)', marginBottom: '20px', lineHeight: '1.5' }}>
              Enter your new password below.
            </p>
            {error && <div className="error-msg reveal">{error}</div>}
            <div className="form-group floating reveal reveal-delay-2">
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
                minLength={6}
                id="reset-new-password"
                placeholder=" "
                disabled={!token}
              />
              <label htmlFor="reset-new-password">New Password</label>
            </div>
            <div className="form-group floating reveal reveal-delay-3">
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                minLength={6}
                id="reset-confirm-password"
                placeholder=" "
                disabled={!token}
              />
              <label htmlFor="reset-confirm-password">Confirm Password</label>
            </div>
            <button type="submit" className="btn btn-primary reveal reveal-delay-4" disabled={loading || !token} id="reset-submit-btn">
              {loading ? <><span className="spinner"></span> Resetting...</> : 'Reset Password'}
            </button>
            <p className="auth-link reveal reveal-delay-5">
              <Link to="/login">Back to Login</Link>
            </p>
          </form>
        )}
      </div>
    </div>
  );
}
