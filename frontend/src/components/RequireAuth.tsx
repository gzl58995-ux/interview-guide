import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ROUTES } from '../constants/routes';

/**
 * 后端接入登录校验后，将 VITE_AUTH_REQUIRED 设为 true 即可开启强制登录
 */
const AUTH_REQUIRED = import.meta.env.VITE_AUTH_REQUIRED === 'true';

export default function RequireAuth() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!AUTH_REQUIRED || isAuthenticated) {
    return <Outlet />;
  }

  return <Navigate to={ROUTES.login} replace state={{ from: location.pathname }} />;
}
