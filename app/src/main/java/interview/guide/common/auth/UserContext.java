package interview.guide.common.auth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;

/**
 * 当前请求的登录用户上下文
 * <p>
 * 由 {@link AuthInterceptor} 在请求进入时写入，请求结束时清理，
 * 业务代码通过 {@link #requireUserId()} 获取当前用户。
 */
public final class UserContext {

    private static final ThreadLocal<AuthPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AuthPrincipal get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        AuthPrincipal principal = HOLDER.get();
        return principal == null ? null : principal.userId();
    }

    public static Long requireUserId() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal.userId();
    }

    public static boolean isAdmin() {
        AuthPrincipal principal = HOLDER.get();
        return principal != null && principal.admin();
    }

    public static void requireAdmin() {
        AuthPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        if (!principal.admin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可访问");
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
