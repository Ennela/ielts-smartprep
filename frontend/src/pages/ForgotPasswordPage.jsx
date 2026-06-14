import { useState } from 'react';
import { Link } from 'react-router-dom';
import authService from '../api/authService';
import { useToast } from '../context/ToastContext';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  
  const { success, error } = useToast();

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email.trim()) {
      error('Please enter your email address');
      return;
    }

    setLoading(true);
    try {
      await authService.forgotPassword(email);
      success('If an account exists, a password reset link has been sent.');
      setSent(true);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to send reset link';
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

      {/* Forgot Password Card */}
      <main className="bg-surface-container-lowest rounded-2xl shadow-ambient w-full max-w-md p-xl relative z-10 border border-outline-variant/30 animate-fade-in">
        {/* Header / Logo */}
        <div className="flex flex-col items-center mb-xl">
          <div className="bg-primary/10 p-sm rounded-lg mb-sm">
            <span className="material-symbols-outlined text-primary text-[32px]">lock_reset</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-primary text-center tracking-tight">SmartPrep</h1>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-sm">Reset Password</h2>
        </div>

        {sent ? (
          <div className="text-center space-y-md">
            <div className="text-[48px]">📧</div>
            <h3 className="font-headline-md text-on-surface">Check Your Email</h3>
            <p className="font-body-md text-on-surface-variant leading-relaxed">
              If an account with <strong>{email}</strong> exists, we've sent a password reset link to your email inbox.
            </p>
            <p className="font-body-md text-outline text-[12px]">
              Please check your spam or junk folder if you don't receive it in 5 minutes.
            </p>
            <div className="pt-md">
              <Link to="/login" className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container transition-colors duration-200">
                Back to Login
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-lg">
            <p className="font-body-md text-on-surface-variant text-center">
              Enter your email address and we'll send you a link to reset your password.
            </p>

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

            {/* Submit Button */}
            <button
              className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-all duration-200 active:scale-[0.98] mt-xl disabled:opacity-50 disabled:cursor-not-allowed"
              type="submit"
              disabled={loading}
              id="forgot-submit-btn"
            >
              {loading ? (
                <>
                  <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin mr-sm"></span>
                  Sending...
                </>
              ) : (
                'Send Reset Link'
              )}
            </button>

            {/* Footnote Link */}
            <p className="mt-xl text-center font-body-md text-body-md text-on-surface-variant">
              Remember your password?{' '}
              <Link className="font-title-lg text-[14px] text-primary hover:text-primary-container transition-colors font-semibold ml-xs" to="/login">Login here</Link>
            </p>
          </form>
        )}
      </main>
    </div>
  );
}
