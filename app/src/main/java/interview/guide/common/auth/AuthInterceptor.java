package interview.guide.common.auth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 登录认证拦截器
 * <p>
 * 解析 {@code Authorization: Bearer <token>}，校验通过后把用户写入 {@link UserContext}，
 * 并同步写入 request attribute {@code userId} 供限流切面使用。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements AsyncHandlerInterceptor {

    public static final String USER_ID_REQUEST_ATTRIBUTE = "userId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }

        AuthPrincipal principal = parseToken(token);
        UserEntity user = userRepository.findById(principal.userId())
            .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "登录状态无效，请重新登录"));

        AuthPrincipal authenticated = new AuthPrincipal(user.getId(), user.getUsername(), user.isAdmin());
        UserContext.set(authenticated);
        request.setAttribute(USER_ID_REQUEST_ATTRIBUTE, authenticated.userId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private AuthPrincipal parseToken(String token) {
        try {
            return jwtService.parse(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "登录状态无效，请重新登录");
        }
    }
}
