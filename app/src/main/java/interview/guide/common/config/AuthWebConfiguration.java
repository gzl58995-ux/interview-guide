package interview.guide.common.config;

import interview.guide.common.auth.AdminInterceptor;
import interview.guide.common.auth.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 认证拦截器注册
 * <p>
 * 默认保护所有 {@code /api/**} 接口，只放行登录与注册；
 * 系统设置相关接口额外要求管理员身份。
 */
@Configuration
@RequiredArgsConstructor
public class AuthWebConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/auth/login",
                "/api/auth/register"
            );

        registry.addInterceptor(adminInterceptor)
            .addPathPatterns("/api/llm-provider/**");
    }
}
