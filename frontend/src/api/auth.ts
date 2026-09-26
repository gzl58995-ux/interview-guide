import { request } from './request';
import type { AuthResponse, LoginRequest, RegisterRequest, UserInfo } from '../types/auth';

export const authApi = {
  /**
   * 注册并直接返回登录态
   */
  register(data: RegisterRequest): Promise<AuthResponse> {
    return request.post<AuthResponse>('/api/auth/register', data);
  },

  /**
   * 账号密码登录
   */
  login(data: LoginRequest): Promise<AuthResponse> {
    return request.post<AuthResponse>('/api/auth/login', data);
  },

  /**
   * 退出登录
   */
  logout(): Promise<void> {
    return request.post<void>('/api/auth/logout');
  },

  /**
   * 获取当前登录用户
   */
  me(): Promise<UserInfo> {
    return request.get<UserInfo>('/api/auth/me');
  },
};
