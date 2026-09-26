import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import type { LoginRequest, RegisterRequest, UserInfo } from '../types/auth';
import { clearAuthSession, getStoredUser, saveAuthSession } from '../utils/authStorage';

interface AuthContextValue {
  user: UserInfo | null;
  isAuthenticated: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(() => getStoredUser());

  const login = useCallback(async (data: LoginRequest) => {
    const session = await authApi.login(data);
    saveAuthSession(session);
    setUser(session.user);
  }, []);

  const register = useCallback(async (data: RegisterRequest) => {
    const session = await authApi.register(data);
    saveAuthSession(session);
    setUser(session.user);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (error) {
      console.warn('退出登录接口调用失败，已清理本地会话', error);
    }
    clearAuthSession();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, login, register, logout }),
    [user, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}
