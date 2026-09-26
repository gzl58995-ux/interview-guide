import type { AuthResponse, UserInfo } from '../types/auth';

const TOKEN_KEY = 'auth.token';
const USER_KEY = 'auth.user';

export function getAuthToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as UserInfo;
  } catch (error) {
    console.warn('本地用户信息解析失败，已忽略', error);
    return null;
  }
}

export function saveAuthSession(session: AuthResponse): void {
  localStorage.setItem(TOKEN_KEY, session.token);
  localStorage.setItem(USER_KEY, JSON.stringify(session.user));
}

export function clearAuthSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}
