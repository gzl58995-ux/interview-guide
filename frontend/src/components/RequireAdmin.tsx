import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ROUTES } from '../constants/routes';

/**
 * 仅管理员可访问的路由守卫，非管理员重定向回简历管理页
 */
export default function RequireAdmin() {
  const { user } = useAuth();

  if (user?.admin) {
    return <Outlet />;
  }

  return <Navigate to={ROUTES.resumeHistory} replace />;
}
