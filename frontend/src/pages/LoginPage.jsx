import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import styles from '../styles/Auth.module.css';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(username, password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles['auth-page']}>
      <div className={styles['auth-left']}>
        <div className={styles['auth-left-content']}>
          <div className={styles['auth-shapes']}>
            <div className={`${styles['auth-shape']} ${styles['auth-shape-1']}`}></div>
            <div className={`${styles['auth-shape']} ${styles['auth-shape-2']}`}></div>
            <div className={`${styles['auth-shape']} ${styles['auth-shape-3']}`}></div>
          </div>
          <h1>IELTS SmartPrep</h1>
          <p>Conquer IELTS with AI</p>
          <div className={styles['auth-features']}>
            <div className={styles['auth-feature']}>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" /><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" /></svg>
              <span>AI-generated Reading tests</span>
            </div>
            <div className={styles['auth-feature']}>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /></svg>
              <span>Smart Writing grading</span>
            </div>
            <div className={styles['auth-feature']}>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 18v-6a9 9 0 0 1 18 0v6" /><path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" /></svg>
              <span>Listening Practice & Mock Tests</span>
            </div>
          </div>
        </div>
      </div>
      <div className={styles['auth-right']}>
        <form className={styles['auth-form']} onSubmit={handleSubmit}>
          <h2 className="reveal">Login</h2>
          {error && <div className={`${styles['error-msg']} reveal`}>{error}</div>}
          <div className={`${styles['form-group']} ${styles['floating']} reveal reveal-delay-1`}>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              id="login-username"
              placeholder=" "
            />
            <label htmlFor="login-username">Username</label>
          </div>
          <div className={`${styles['form-group']} ${styles['floating']} reveal reveal-delay-2`}>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              id="login-password"
              placeholder=" "
            />
            <label htmlFor="login-password">Password</label>
          </div>
          <button type="submit" className="btn btn-primary reveal reveal-delay-3" disabled={loading} id="login-submit-btn">
            {loading ? <><span className="spinner"></span> Logging in...</> : 'Login'}
          </button>
          <div className="reveal reveal-delay-4" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '4px' }}>
            <Link to="/forgot-password" style={{ fontSize: '14px', color: 'var(--primary)', textDecoration: 'none' }}>Forgot password?</Link>
          </div>
          <p className={`${styles['auth-link']} reveal reveal-delay-5`}>
            Don't have an account? <Link to="/register">Register</Link>
          </p>
        </form>
      </div>
    </div>
  );
}
