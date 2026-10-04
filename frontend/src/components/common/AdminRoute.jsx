import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import FullPageLoader from './FullPageLoader';

export default function AdminRoute({ children }) {
  const { isAuthenticated, isAdmin, loading } = useAuth();

  if (loading) {
    return <FullPageLoader message="Verifying session..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return children;
}
