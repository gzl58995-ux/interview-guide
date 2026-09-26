package interview.guide.modules.voiceinterview.config;

import interview.guide.common.auth.JwtService;
import interview.guide.modules.auth.repository.UserRepository;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * 语音面试 WebSocket 握手鉴权
 * <p>
 * 浏览器 WebSocket 无法携带 Authorization 头，改为从查询参数 {@code token} 解析 JWT，
 * 并校验目标会话归属当前用户；任一环节失败直接拒绝握手。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceInterviewHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String SESSION_ID_ATTRIBUTE = "sessionId";

    private static final String TOKEN_QUERY_PARAM = "token";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final VoiceInterviewSessionRepository sessionRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long userId = parseUserId(request);
        Long sessionId = parseSessionId(request.getURI().getPath());

        if (userId == null || sessionId == null || !userRepository.existsById(userId)) {
            return reject(response, request, "未登录或登录已过期");
        }

        Optional<VoiceInterviewSessionEntity> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty() || !userId.equals(sessionOpt.get().getUserId())) {
            return reject(response, request, "会话不存在或无权访问");
        }

        attributes.put(USER_ID_ATTRIBUTE, userId);
        attributes.put(SESSION_ID_ATTRIBUTE, sessionId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private Long parseUserId(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
            .getQueryParams().getFirst(TOKEN_QUERY_PARAM);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtService.parse(token.trim()).userId();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("语音面试 WebSocket 握手 Token 无效: {}", e.getMessage());
            return null;
        }
    }

    private Long parseSessionId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] segments = path.split("/");
        String last = segments.length > 0 ? segments[segments.length - 1] : null;
        try {
            return last == null ? null : Long.parseLong(last);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean reject(ServerHttpResponse response, ServerHttpRequest request, String reason) {
        log.warn("语音面试 WebSocket 握手被拒绝: path={}, reason={}", request.getURI().getPath(), reason);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
