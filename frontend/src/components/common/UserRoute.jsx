import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function UserRoute({ children }) {
  const { isAuthenticated, isAdmin, loading } = useAuth();

  if (loading) {
    return <div className="loading-screen">Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (isAdmin && !window.location.search.includes('preview=true')) {
    return <Navigate to="/admin" replace />;
  }

  return children;
}
