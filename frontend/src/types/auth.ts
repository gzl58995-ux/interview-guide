export interface UserInfo {
  id: number;
  username: string;
  admin: boolean;
  createdAt?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  user: UserInfo;
}
