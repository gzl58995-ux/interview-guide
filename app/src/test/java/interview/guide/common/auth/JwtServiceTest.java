package interview.guide.common.auth;

import interview.guide.common.config.AuthProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService 测试")
class JwtServiceTest {

    private AuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setSecret("test-auth-jwt-secret-0123456789abcdef");
        properties.setIssuer("interview-guide");
        properties.setExpireMinutes(60);
    }

    @Nested
    @DisplayName("签发与解析")
    class IssueAndParse {

        @Test
        @DisplayName("合法 Token 可解析出用户信息")
        void roundTrip() {
            JwtService jwtService = new JwtService(properties);

            String token = jwtService.issue(42L, "tester");
            AuthPrincipal principal = jwtService.parse(token);

            assertThat(principal.userId()).isEqualTo(42L);
            assertThat(principal.username()).isEqualTo("tester");
        }

        @Test
        @DisplayName("过期 Token 解析抛出 ExpiredJwtException")
        void expiredTokenRejected() {
            properties.setExpireMinutes(-1);
            JwtService jwtService = new JwtService(properties);

            String token = jwtService.issue(1L, "tester");

            assertThatThrownBy(() -> jwtService.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("签名被篡改的 Token 解析失败")
        void tamperedTokenRejected() {
            JwtService jwtService = new JwtService(properties);
            String token = jwtService.issue(1L, "tester");
            String tampered = token.substring(0, token.length() - 2) + "xx";

            assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("其他签发者签发的 Token 解析失败")
        void foreignIssuerRejected() {
            JwtService jwtService = new JwtService(properties);
            String token = jwtService.issue(1L, "tester");

            properties.setIssuer("another-issuer");
            JwtService anotherService = new JwtService(properties);

            assertThatThrownBy(() -> anotherService.parse(token))
                .isInstanceOf(JwtException.class);
        }
    }

    @Test
    @DisplayName("密钥缺失或不足 32 字节时拒绝启动")
    void shortSecretRejected() {
        properties.setSecret("too-short");

        assertThatThrownBy(() -> new JwtService(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_AUTH_JWT_SECRET");
    }
}
