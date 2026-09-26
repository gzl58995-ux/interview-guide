import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { AlertCircle, Eye, EyeOff, KeyRound, Loader2, Lock, User, UserPlus } from 'lucide-react';
import AuthPageShell from '../components/AuthPageShell';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../api/request';
import { ROUTES } from '../constants/routes';

const USERNAME_PATTERN = /^[a-zA-Z0-9_]{3,32}$/;
const PASSWORD_MIN_LENGTH = 6;
const PASSWORD_MAX_LENGTH = 64;

const inputClass = 'w-full py-3 rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { isAuthenticated, register } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to={ROUTES.resumeHistory} replace />;
  }

  const validate = (): string => {
    const trimmedUsername = username.trim();
    if (!trimmedUsername) {
      return '请输入用户账号';
    }
    if (!USERNAME_PATTERN.test(trimmedUsername)) {
      return '账号需为 3-32 位字母、数字或下划线';
    }
    if (!password) {
      return '请输入密码';
    }
    if (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH) {
      return `密码长度需为 ${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH} 位`;
    }
    if (password !== confirmPassword) {
      return '两次输入的密码不一致';
    }
    return '';
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setError('');
    setSubmitting(true);
    try {
      await register({ username: username.trim(), password });
      navigate(ROUTES.resumeHistory, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const passwordToggle = (visible: boolean, onToggle: () => void) => (
    <button
      type="button"
      onClick={onToggle}
      title={visible ? '隐藏密码' : '显示密码'}
      className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
    >
      {visible ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
    </button>
  );

  return (
    <AuthPageShell title="创建账号" subtitle="注册后即可保存简历和面试记录">
      <form onSubmit={handleSubmit} className="space-y-5">
        <div>
          <label htmlFor="register-username" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
            用户账号
          </label>
          <div className="relative">
            <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              id="register-username"
              type="text"
              value={username}
              onChange={event => setUsername(event.target.value)}
              placeholder="3-32 位字母、数字或下划线"
              autoComplete="username"
              autoFocus
              className={`${inputClass} pl-10 pr-4`}
            />
          </div>
        </div>

        <div>
          <label htmlFor="register-password" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
            密码
          </label>
          <div className="relative">
            <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              id="register-password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={event => setPassword(event.target.value)}
              placeholder={`${PASSWORD_MIN_LENGTH}-${PASSWORD_MAX_LENGTH} 位密码`}
              autoComplete="new-password"
              className={`${inputClass} pl-10 pr-11`}
            />
            {passwordToggle(showPassword, () => setShowPassword(prev => !prev))}
          </div>
        </div>

        <div>
          <label htmlFor="register-confirm-password" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
            确认密码
          </label>
          <div className="relative">
            <KeyRound className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
            <input
              id="register-confirm-password"
              type={showConfirmPassword ? 'text' : 'password'}
              value={confirmPassword}
              onChange={event => setConfirmPassword(event.target.value)}
              placeholder="请再次输入密码"
              autoComplete="new-password"
              className={`${inputClass} pl-10 pr-11`}
            />
            {passwordToggle(showConfirmPassword, () => setShowConfirmPassword(prev => !prev))}
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
          {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserPlus className="w-4 h-4" />}
          {submitting ? '注册中...' : '注册并登录'}
        </button>
      </form>

      <p className="mt-6 text-center text-sm text-slate-500 dark:text-slate-400">
        已有账号？
        <Link
          to={ROUTES.login}
          className="ml-1 font-medium text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 transition-colors"
        >
          直接登录
        </Link>
      </p>
    </AuthPageShell>
  );
}
