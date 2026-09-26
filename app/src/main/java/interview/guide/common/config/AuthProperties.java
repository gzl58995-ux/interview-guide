package interview.guide.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth.jwt")
public class AuthProperties {

    /**
     * JWT 签名密钥（HS256，至少 32 字节），只允许通过 .env 注入
     */
    private String secret;

    /**
     * Token 有效期（分钟），默认 7 天
     */
    private long expireMinutes = 10080;

    /**
     * Token 签发者
     */
    private String issuer = "interview-guide";
}
