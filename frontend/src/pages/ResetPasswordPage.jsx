import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import authService from '../api/authService';
import { useToast } from '../context/ToastContext';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [successState, setSuccessState] = useState(false);
  
  const { success, error, warning } = useToast();

  useEffect(() => {
    if (!token) {
      warning('Invalid or missing reset token. Please request a new password reset.');
    }
  }, [token, warning]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!token) {
      error('Reset token is missing');
      return;
    }

    if (newPassword !== confirmPassword) {
      error('Passwords do not match');
      return;
    }

    if (newPassword.length < 6) {
      error('Password must be at least 6 characters');
      return;
    }

    setLoading(true);
    try {
      await authService.resetPassword(token, newPassword);
      success('Password reset successfully! Redirecting to login...');
      setSuccessState(true);
      setTimeout(() => navigate('/login', { replace: true }), 3000);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to reset password';
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

      {/* Reset Password Card */}
      <main className="bg-surface-container-lowest rounded-2xl shadow-ambient w-full max-w-md p-xl relative z-10 border border-outline-variant/30 animate-fade-in">
        {/* Header / Logo */}
        <div className="flex flex-col items-center mb-xl">
          <div className="bg-primary/10 p-sm rounded-lg mb-sm">
            <span className="material-symbols-outlined text-primary text-[32px]">lock_open</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-primary text-center tracking-tight">SmartPrep</h1>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-sm">Choose New Password</h2>
        </div>

        {successState ? (
          <div className="text-center space-y-md">
            <div className="text-[48px]">✅</div>
            <h3 className="font-headline-md text-on-surface">Password Updated!</h3>
            <p className="font-body-md text-on-surface-variant leading-relaxed">
              Your password has been changed successfully. You will be redirected to the login page shortly.
            </p>
            <div className="pt-md">
              <Link to="/login" className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container transition-colors duration-200">
                Go to Login
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-lg">
            <p className="font-body-md text-on-surface-variant text-center">
              Please enter your new password details below.
            </p>

            {/* New Password */}
            <div className="space-y-xs">
              <label className="block font-label-md text-label-md text-on-surface" htmlFor="new-password">New Password</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-sm flex items-center pointer-events-none">
                  <span className="material-symbols-outlined text-outline text-[20px]">lock</span>
                </div>
                <input
                  className="block w-full pl-[40px] pr-[40px] py-sm font-body-md text-body-md text-on-surface bg-surface-container-lowest border border-outline-variant rounded-lg focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-shadow disabled:opacity-50"
                  id="new-password"
                  name="new-password"
                  placeholder="••••••••"
                  required
                  type={showPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  disabled={loading || !token}
                />
              </div>
            </div>

            {/* Confirm Password */}
            <div className="space-y-xs">
              <label className="block font-label-md text-label-md text-on-surface" htmlFor="confirm-password">Confirm Password</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-sm flex items-center pointer-events-none">
                  <span className="material-symbols-outlined text-outline text-[20px]">lock</span>
                </div>
                <input
                  className="block w-full pl-[40px] pr-[40px] py-sm font-body-md text-body-md text-on-surface bg-surface-container-lowest border border-outline-variant rounded-lg focus:ring-2 focus:ring-primary focus:border-primary focus:outline-none transition-shadow disabled:opacity-50"
                  id="confirm-password"
                  name="confirm-password"
                  placeholder="••••••••"
                  required
                  type={showPassword ? "text" : "password"}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  disabled={loading || !token}
                />
                <div className="absolute inset-y-0 right-0 pr-sm flex items-center">
                  <button
                    className="text-outline hover:text-on-surface transition-colors focus:outline-none"
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    disabled={!token}
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
              disabled={loading || !token}
              id="reset-submit-btn"
            >
              {loading ? (
                <>
                  <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin mr-sm"></span>
                  Resetting...
                </>
              ) : (
                'Reset Password'
              )}
            </button>

            {/* Footnote Link */}
            <p className="mt-xl text-center font-body-md text-body-md text-on-surface-variant">
              <Link className="font-title-lg text-[14px] text-primary hover:text-primary-container transition-colors font-semibold" to="/login">Back to Login</Link>
            </p>
          </form>
        )}
      </main>
    </div>
  );
}
