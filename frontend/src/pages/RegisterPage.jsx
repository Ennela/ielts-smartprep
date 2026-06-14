import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const { register } = useAuth();
  const { success, error } = useToast();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim() || !username.trim() || !password) {
      error('Please fill in all fields');
      return;
    }

    if (password.length < 6) {
      error('Password must be at least 6 characters');
      return;
    }

    setLoading(true);
    try {
      await register(email, username, password);
      success('Account created successfully! A verification email has been sent.');
      navigate('/dashboard');
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Registration failed';
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

      {/* Registration Card */}
      <main className="bg-surface-container-lowest rounded-2xl shadow-ambient w-full max-w-md p-xl relative z-10 border border-outline-variant/30 animate-fade-in">
        {/* Header / Logo */}
        <div className="flex flex-col items-center mb-xl">
          <div className="bg-primary/10 p-sm rounded-lg mb-sm">
            <span className="material-symbols-outlined text-primary text-[32px]" style={{ fontVariationSettings: "'FILL' 1" }}>menu_book</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-primary text-center tracking-tight">SmartPrep</h1>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-sm">Create Account</h2>
          <p className="font-body-md text-body-md text-on-surface-variant mt-xs text-center">Sign up to start your personalized IELTS preparation.</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-lg">
          {/* Email Address */}
          <div className="space-y-xs">
            <label className="block font-label-md text-label-md text-on-surface" htmlFor="email">Email Address</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-sm flex items-center pointer-events-none">
                <span className="material-symbols-outlined text-outline text-[20px]">mail</span>
              </div>
              <input
                className="block w-full pl-[40px] pr-sm py-sm font-body-md text-body-md text-on-surface bg-surface-container-lowest border border-outline-variant rounded-lg focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-shadow"
                id="email"
                name="email"
                placeholder="student@example.com"
                required
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={loading}
              />
            </div>
          </div>

          {/* Username */}
          <div className="space-y-xs">
            <label className="block font-label-md text-label-md text-on-surface" htmlFor="username">Username</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-sm flex items-center pointer-events-none">
                <span className="material-symbols-outlined text-outline text-[20px]">person</span>
              </div>
              <input
                className="block w-full pl-[40px] pr-sm py-sm font-body-md text-body-md text-on-surface bg-surface-container-lowest border border-outline-variant rounded-lg focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-shadow"
                id="username"
                name="username"
                placeholder="username"
                required
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={loading}
              />
            </div>
          </div>

          {/* Password */}
          <div className="space-y-xs">
            <label className="block font-label-md text-label-md text-on-surface" htmlFor="password">Password</label>
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

          {/* Submit Button */}
          <button
            className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-all duration-200 active:scale-[0.98] mt-xl disabled:opacity-50 disabled:cursor-not-allowed"
            type="submit"
            disabled={loading}
            id="register-submit-btn"
          >
            {loading ? (
              <>
                <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin mr-sm"></span>
                Creating account...
              </>
            ) : (
              'Create Account'
            )}
          </button>
        </form>

        {/* Footnote Link */}
        <p className="mt-xl text-center font-body-md text-body-md text-on-surface-variant">
          Already have an account?{' '}
          <Link className="font-title-lg text-[14px] text-primary hover:text-primary-container transition-colors font-semibold ml-xs" to="/login">Login here</Link>
        </p>
      </main>
    </div>
  );
}
