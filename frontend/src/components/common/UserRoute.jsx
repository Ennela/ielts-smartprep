import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import FullPageLoader from './FullPageLoader';

export default function UserRoute({ children }) {
  const { isAuthenticated, isAdmin, loading } = useAuth();

  if (loading) {
    return <FullPageLoader message="Verifying session..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (isAdmin && !window.location.search.includes('preview=true')) {
    return <Navigate to="/admin" replace />;
  }

  return children;
}
