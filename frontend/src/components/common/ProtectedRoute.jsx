import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, loading, profileError, retryProfile } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col justify-center items-center bg-background">
        <div className="w-10 h-10 border-4 border-outline-variant/30 border-t-primary rounded-full animate-spin"></div>
        <p className="mt-md text-on-surface-variant font-medium">Verifying session...</p>
      </div>
    );
  }

  // The session may still be valid — the profile request just did not get through.
  // Sending the user to /login here would look like a logout they never asked for.
  if (!isAuthenticated && profileError) {
    return (
      <div className="min-h-screen flex flex-col justify-center items-center bg-background">
        <p className="text-on-surface font-medium">Could not verify your session.</p>
        <p className="mt-sm text-on-surface-variant text-sm">{profileError.message}</p>
        <button type="button" className="btn btn-primary mt-md" onClick={retryProfile}>
          Try Again
        </button>
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirectPath = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirectPath}`} replace />;
  }

  return children;
}
