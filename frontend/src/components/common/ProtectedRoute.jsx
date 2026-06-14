import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col justify-center items-center bg-background">
        <div className="w-10 h-10 border-4 border-outline-variant/30 border-t-primary rounded-full animate-spin"></div>
        <p className="mt-md text-on-surface-variant font-medium">Verifying session...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirectPath = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirectPath}`} replace />;
  }

  return children;
}
