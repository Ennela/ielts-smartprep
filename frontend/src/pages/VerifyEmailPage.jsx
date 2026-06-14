import { useState, useEffect } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import authService from '../api/authService';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  
  const { user, isAuthenticated, updateUser } = useAuth();
  const { success, error, info } = useToast();
  
  const [verifying, setVerifying] = useState(!!token);
  const [verifyStatus, setVerifyStatus] = useState(null); // 'success' | 'error' | null
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  // Auto-verify if token exists
  useEffect(() => {
    if (token) {
      const verify = async () => {
        try {
          await authService.verifyEmail(token);
          setVerifyStatus('success');
          success('Email verified successfully!');
          // If logged in, update local profile state
          if (isAuthenticated) {
            updateUser({ emailVerified: true });
          }
        } catch (err) {
          setVerifyStatus('error');
          const msg = err.response?.data?.message || err.message || 'Verification failed';
          error(msg);
        } finally {
          setVerifying(false);
        }
      };
      verify();
    }
  }, [token, isAuthenticated, success, error, updateUser]);

  // Cooldown timer for resending verification email
  useEffect(() => {
    if (cooldown > 0) {
      const timer = setTimeout(() => setCooldown(cooldown - 1), 1000);
      return () => clearTimeout(timer);
    }
  }, [cooldown]);

  const handleResend = async () => {
    if (resending || cooldown > 0) return;
    setResending(true);
    try {
      await authService.resendVerification();
      success('Verification email resent! Please check your inbox.');
      setCooldown(60); // 60 seconds cooldown
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to resend verification email';
      error(msg);
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="bg-background min-h-screen flex items-center justify-center p-md md:p-margin text-on-background relative overflow-hidden font-sans">
      {/* Decorative ambient background elements */}
      <div className="absolute top-[-10%] left-[-10%] w-1/2 h-1/2 bg-secondary-fixed-dim rounded-full blur-[120px] opacity-20 pointer-events-none"></div>
      <div className="absolute bottom-[-10%] right-[-10%] w-1/2 h-1/2 bg-primary-fixed-dim rounded-full blur-[120px] opacity-30 pointer-events-none"></div>

      {/* Verify Email Card */}
      <main className="bg-surface-container-lowest rounded-2xl shadow-ambient w-full max-w-md p-xl relative z-10 border border-outline-variant/30 animate-fade-in">
        {/* Header / Logo */}
        <div className="flex flex-col items-center mb-xl">
          <div className="bg-primary/10 p-sm rounded-lg mb-sm">
            <span className="material-symbols-outlined text-primary text-[32px]">mark_email_read</span>
          </div>
          <h1 className="font-display-lg text-display-lg text-primary text-center tracking-tight">SmartPrep</h1>
          <h2 className="font-headline-md text-headline-md text-on-surface mt-sm">Email Verification</h2>
        </div>

        {verifying ? (
          /* Case 1: Verifying in progress */
          <div className="text-center space-y-md py-md">
            <div className="w-12 h-12 border-4 border-outline-variant/30 border-t-primary rounded-full animate-spin mx-auto"></div>
            <p className="font-body-md text-on-surface-variant font-medium">Verifying your email address...</p>
          </div>
        ) : verifyStatus === 'success' ? (
          /* Case 2: Verification succeeded */
          <div className="text-center space-y-md">
            <div className="text-[48px]">✅</div>
            <h3 className="font-headline-md text-on-surface">Verification Successful!</h3>
            <p className="font-body-md text-on-surface-variant leading-relaxed">
              Your email address has been verified. You can now access all features.
            </p>
            <div className="pt-md">
              <Link to="/dashboard" className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container transition-colors duration-200">
                Go to Dashboard
              </Link>
            </div>
          </div>
        ) : verifyStatus === 'error' ? (
          /* Case 3: Verification failed */
          <div className="text-center space-y-md">
            <div className="text-[48px]">❌</div>
            <h3 className="font-headline-md text-on-surface">Verification Failed</h3>
            <p className="font-body-md text-on-surface-variant leading-relaxed">
              The verification token is invalid or has expired. Please request a new verification email.
            </p>
            
            {isAuthenticated ? (
              <div className="pt-md space-y-sm">
                <button
                  onClick={handleResend}
                  disabled={resending || cooldown > 0}
                  className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {resending ? (
                    <>
                      <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin mr-sm"></span>
                      Resending...
                    </>
                  ) : cooldown > 0 ? (
                    `Resend in ${cooldown}s`
                  ) : (
                    'Resend Verification Email'
                  )}
                </button>
                <Link to="/dashboard" className="block text-primary hover:underline font-medium">Go to Dashboard</Link>
              </div>
            ) : (
              <div className="pt-md">
                <Link to="/login" className="w-full flex justify-center items-center py-sm px-md border border-outline-variant rounded-lg font-body-md text-body-md text-on-surface bg-surface-container-lowest hover:bg-surface-container-low transition-colors duration-200">
                  Log in to Resend Email
                </Link>
              </div>
            )}
          </div>
        ) : (
          /* Case 4: No token, landing page for unverified logged-in users */
          <div className="text-center space-y-md">
            <div className="text-[48px]">✉️</div>
            <h3 className="font-headline-md text-on-surface">Verify Your Email</h3>
            <p className="font-body-md text-on-surface-variant leading-relaxed">
              We need to confirm your email address. Please click the link in the verification email sent to your inbox.
            </p>
            
            {isAuthenticated ? (
              <div className="pt-md space-y-sm">
                <button
                  onClick={handleResend}
                  disabled={resending || cooldown > 0}
                  className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {resending ? (
                    <>
                      <span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin mr-sm"></span>
                      Resending...
                    </>
                  ) : cooldown > 0 ? (
                    `Resend in ${cooldown}s`
                  ) : (
                    'Resend Verification Email'
                  )}
                </button>
                <Link to="/dashboard" className="block text-primary hover:underline font-medium">Go to Dashboard</Link>
              </div>
            ) : (
              <div className="pt-md">
                <Link to="/login" className="w-full flex justify-center items-center py-sm px-md border border-transparent rounded-lg shadow-sm font-title-lg text-title-lg text-on-primary bg-primary hover:bg-primary-container transition-colors duration-200">
                  Back to Login
                </Link>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
