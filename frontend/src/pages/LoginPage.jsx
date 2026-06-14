import { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const { login } = useAuth();
  const { success, error, warning } = useToast();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      error('Please enter both username/email and password');
      return;
    }

    setLoading(true);
    try {
      const userData = await login(username, password);
      success('Logged in successfully!');
      
      // Check if user is unverified and show a warning
      if (userData.emailVerified === false) {
        warning('Your email address is not verified. Please verify your email.');
      }

      // Check redirect param
      const redirectUrl = searchParams.get('redirect');
      if (redirectUrl) {
        navigate(decodeURIComponent(redirectUrl), { replace: true });
      } else {
        if (userData.role === 'ADMIN') {
          navigate('/admin', { replace: true });
        } else {
          navigate('/dashboard', { replace: true });
        }
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Login failed';
      error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-background min-h-screen flex items-center justify-center p-md md:p-margin text-on-background relative overflow-hidden font-sans">
      {/* Decorative ambient background elements */}
      <div className="absolute top-[-10%] left-[-10%] w-1/2 h-1/2 bg-secondary-fixed-dim rounded-full blur-[120px] opacity-20 pointer-events-none"></div>
      <div className="absolute bottom-[-10%] right-[-10%] w-1/2 h-1/2 bg-primary-fixed-dim rounded-full blur-[120px] opacity-30 pointer-events-none"></div>

      {/* Login Card */}
      <main className="bg-surface-container-lowest rounded-2xl shadow-ambient w-full max-w-md p-xl relative z-10 border border-outline-variant/30 animate-fade-in">
        {/* Header / Logo */}
        <div className="flex flex-col items-center mb-xl">
          <div className="bg-primary/10 p-sm rounded-lg mb-sm">
            <span className="material-symbols-outlined text-primary text-[32px]" style={{ fontVariationSettings: "'FILL' 1" }}>menu_book</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-primary text-center tracking-tight">SmartPrep</h1>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-sm">Welcome Back</h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-xs text-center">Enter your details to access your dashboard.</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-lg">
          {/* Email/Username Input */}
          <div className="space-y-xs">
            <label className="block font-label-md text-label-md text-on-surface" htmlFor="username">Email or Username</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-sm flex items-center pointer-events-none">
                <span className="material-symbols-outlined text-outline text-[20px]">mail</span>
              </div>
              <input
                className="block w-full pl-[40px] pr-sm py-sm font-body-md text-body-md text-on-surface bg-surface-container-lowest border border-outline-variant rounded-lg focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-shadow"
                id="username"
                name="username"
                placeholder="student@example.com"
                required
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={loading}
              />
            </div>
          </div>

          {/* Password Input */}
          <div className="space-y-xs">
            <div className="flex items-center justify-between">
              <label className="block font-label-md text-label-md text-on-surface" htmlFor="password">Password</label>
              <Link className="font-label-md text-label-md text-primary hover:text-primary-container transition-colors" to="/forgot-password">Forgot Password?</Link>
            </div>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-sm flex items-center pointer-events-none">
                <span className="material-symbols-outlined text-outline text-[20px]">lock</span>
              </div>
              <input
                className="block w-full pl-[40px] pr-[40px] py-sm font-body-md text-body-md text-on-surface bg-surface-container-lowest border border-outline-variant rounded-lg focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-shadow"
                id="password"
                name="password"
                placeholder="••••••••"
                required
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={loading}
              />
              <div className="absolute inset-y-0 right-0 pr-sm flex items-center">
                <button
                  className="text-outline hover:text-on-surface transition-colors focus:outline-none"
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  <span className="material-symbols-outlined text-[20px]">{showPassword ? "visibility" : "visibility_off"}</span>
                </button>
              </div>
            </div>
          </div>

          {/* Remember Me */}
          <div className="flex items-center">
            <input
              className="h-4 w-4 text-primary focus:ring-primary border-outline-variant rounded text-primary cursor-pointer"
              id="remember-me"
              name="remember-me"
              type="checkbox"
            />
            <label className="ml-sm block font-body-md text-body-md text-on-surface-variant cursor-pointer select-none" htmlFor="remember-me">
              Remember me for 30 days
            </label>
          </div>

          {/* Submit Button */}
          <button
            className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-all duration-200 active:scale-[0.98] mt-xl disabled:opacity-50 disabled:cursor-not-allowed"
            type="submit"
            disabled={loading}
            id="login-submit-btn"
          >
            {loading ? (
              <>
                <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin mr-sm"></span>
                Logging in...
              </>
            ) : (
              'Login'
            )}
          </button>
        </form>

        {/* Divider */}
        <div className="mt-lg mb-lg relative">
          <div aria-hidden="true" className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-outline-variant"></div>
          </div>
          <div className="relative flex justify-center">
            <span className="px-sm bg-surface-container-lowest font-label-md text-label-md text-outline">OR</span>
          </div>
        </div>

        {/* Social Login */}
        <button
          className="w-full flex justify-center items-center py-sm px-md border border-outline-variant rounded-lg font-body-md text-body-md text-on-surface bg-surface-container-lowest hover:bg-surface-container-low transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary"
          type="button"
          onClick={() => warning('Google authentication is not yet enabled for this environment.')}
        >
          <svg className="h-5 w-5 mr-sm" fill="none" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"></path>
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"></path>
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"></path>
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"></path>
          </svg>
          Continue with Google
        </button>

        {/* Register Link */}
        <p className="mt-xl text-center font-body-md text-body-md text-on-surface-variant">
          Don't have an account?{' '}
          <Link className="font-title-lg text-[14px] text-primary hover:text-primary-container transition-colors font-semibold ml-xs" to="/register">Register here</Link>
        </p>
      </main>
    </div>
  );
}
