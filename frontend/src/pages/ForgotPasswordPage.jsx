import { useState } from 'react';
import { Link } from 'react-router-dom';
import authService from '../api/authService';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await authService.forgotPassword(email);
      setSent(true);
    } catch (err) {
      setError(err.message || 'Failed to send reset email');
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
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              <span>Secure password recovery</span>
            </div>
            <div className="auth-feature">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
              <span>Reset link via email</span>
            </div>
          </div>
        </div>
      </div>
      <div className="auth-right">
        {sent ? (
          <div className="auth-form" style={{ textAlign: 'center' }}>
            <div className="reveal" style={{ fontSize: '48px', marginBottom: '16px' }}>📧</div>
            <h2 className="reveal">Check Your Email</h2>
            <p className="reveal reveal-delay-1" style={{ color: 'var(--text-secondary)', lineHeight: '1.6', marginBottom: '24px' }}>
              If an account with <strong>{email}</strong> exists, we've sent a password reset link.
              Please check your inbox (and spam folder).
            </p>
            <p className="reveal reveal-delay-2" style={{ color: 'var(--text-secondary)', fontSize: '14px' }}>
              The link expires in 15 minutes.
            </p>
            <Link to="/login" className="btn btn-primary reveal reveal-delay-3" style={{ display: 'inline-block', marginTop: '16px', textDecoration: 'none' }}>
              Back to Login
            </Link>
          </div>
        ) : (
          <form className="auth-form" onSubmit={handleSubmit}>
            <h2 className="reveal">Forgot Password</h2>
            <p className="reveal reveal-delay-1" style={{ color: 'var(--text-secondary)', marginBottom: '20px', lineHeight: '1.5' }}>
              Enter your email address and we'll send you a link to reset your password.
            </p>
            {error && <div className="error-msg reveal">{error}</div>}
            <div className="form-group floating reveal reveal-delay-2">
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                id="forgot-email"
                placeholder=" "
              />
              <label htmlFor="forgot-email">Email Address</label>
            </div>
            <button type="submit" className="btn btn-primary reveal reveal-delay-3" disabled={loading} id="forgot-submit-btn">
              {loading ? <><span className="spinner"></span> Sending...</> : 'Send Reset Link'}
            </button>
            <p className="auth-link reveal reveal-delay-4">
              Remember your password? <Link to="/login">Login</Link>
            </p>
          </form>
        )}
      </div>
    </div>
  );
}
