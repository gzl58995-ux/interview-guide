package interview.guide.modules.auth.model;

/**
 * 登录/注册成功后的会话信息
 */
public record AuthResponse(String token, UserDTO user) {
}
