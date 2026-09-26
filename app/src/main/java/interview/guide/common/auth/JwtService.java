package interview.guide.common.auth;

import interview.guide.common.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与解析
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_USERNAME = "username";

    private final AuthProperties authProperties;
    private final SecretKey signingKey;

    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.signingKey = buildSigningKey(authProperties.getSecret());
    }

    /**
     * 签发 Token，subject 为用户 ID
     */
    public String issue(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(authProperties.getExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_USERNAME, username)
            .issuer(authProperties.getIssuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
    }

    /**
     * 解析并校验 Token，签名错误、过期、签发者不匹配都会抛出 {@link io.jsonwebtoken.JwtException}
     */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(authProperties.getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return new AuthPrincipal(Long.valueOf(claims.getSubject()), claims.get(CLAIM_USERNAME, String.class), false);
    }

    private static SecretKey buildSigningKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "app.auth.jwt.secret 未配置或长度不足 " + MIN_SECRET_BYTES + " 字节，请在 .env 中设置 APP_AUTH_JWT_SECRET");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
