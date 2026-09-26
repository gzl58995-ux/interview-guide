package interview.guide.common.auth;

import interview.guide.common.config.AuthProperties;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthInterceptor 测试")
class AuthInterceptorTest {

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;
    private AuthInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties();
        properties.setSecret("test-auth-jwt-secret-0123456789abcdef");
        properties.setIssuer("interview-guide");
        properties.setExpireMinutes(60);
        jwtService = new JwtService(properties);
        interceptor = new AuthInterceptor(jwtService, userRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("缺少 Token 时返回未授权错误")
    void missingTokenRejected() {
        BusinessException ex = catchThrowableOfType(
            () -> interceptor.preHandle(request, response, new Object()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("合法 Token 写入用户上下文与限流请求属性")
    void validTokenAccepted() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("tester");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(1L, "tester"));

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(UserContext.requireUserId()).isEqualTo(1L);
        assertThat(UserContext.isAdmin()).isFalse();
        assertThat(request.getAttribute(AuthInterceptor.USER_ID_REQUEST_ATTRIBUTE)).isEqualTo(1L);

        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(UserContext.get()).isNull();
    }

    @Test
    @DisplayName("Token 对应用户已被删除时返回登录失效")
    void deletedUserRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(1L, "tester"));

        BusinessException ex = catchThrowableOfType(
            () -> interceptor.preHandle(request, response, new Object()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("非法 Token 返回登录失效")
    void invalidTokenRejected() {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt");

        BusinessException ex = catchThrowableOfType(
            () -> interceptor.preHandle(request, response, new Object()), BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("CORS 预检请求直接放行")
    void optionsRequestPassed() throws Exception {
        request.setMethod("OPTIONS");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
