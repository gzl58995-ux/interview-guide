import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle, Eye, EyeOff, Loader2, Lock, LogIn, User } from 'lucide-react';
import AuthPageShell from '../components/AuthPageShell';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';

interface LoginLocationState {
  from?: string;
}

const inputClass = 'w-full py-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow';

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to={ROUTES.resumeHistory} replace />;
  }

  const redirectTo = (location.state as LoginLocationState | null)?.from || ROUTES.resumeHistory;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedUsername = username.trim();
    if (!trimmedUsername || !password) {
      setError('请输入账号和密码');
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      await login({ username: trimmedUsername, password });
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthPageShell title="欢迎回来" subtitle="登录后继续你的面试准备">
      <form onSubmit={handleSubmit} className="space-y-5">
        <div>
          <label htmlFor="login-username" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
            账号
          </label>
          <div className="relative">
            <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              id="login-username"
              type="text"
              value={username}
              onChange={event => setUsername(event.target.value)}
              placeholder="请输入用户账号"
              autoComplete="username"
              autoFocus
              className={`${inputClass} pl-10 pr-4`}
            />
          </div>
        </div>

        <div>
          <label htmlFor="login-password" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
            密码
          </label>
          <div className="relative">
            <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={event => setPassword(event.target.value)}
              placeholder="请输入密码"
              autoComplete="current-password"
              className={`${inputClass} pl-10 pr-11`}
            />
            <button
              type="button"
              onClick={() => setShowPassword(prev => !prev)}
              title={showPassword ? '隐藏密码' : '显示密码'}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {error && (
          <div className="flex items-start gap-2 px-3.5 py-3 rounded-xl bg-red-50 dark:bg-red-900/20 text-sm text-red-600 dark:text-red-400">
            <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="btn-primary w-full py-3 rounded-xl text-sm font-semibold flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <LogIn className="w-4 h-4" />}
          {submitting ? '登录中...' : '登录'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
        还没有账号？
        <Link
          to={ROUTES.register}
          className="ml-1 font-medium text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 transition-colors"
        >
          立即注册
        </Link>
      </p>
    </AuthPageShell>
  );
}
