package interview.guide.common.auth;

/**
 * 已认证用户信息，由 JWT + 用户表校验后得到
 */
public record AuthPrincipal(Long userId, String username, boolean admin) {
}
