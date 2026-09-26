package interview.guide.modules.auth;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.auth.model.AuthResponse;
import interview.guide.modules.auth.model.LoginRequest;
import interview.guide.modules.auth.model.RegisterRequest;
import interview.guide.modules.auth.model.UserDTO;
import interview.guide.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册并直接返回登录态
     */
    @PostMapping("/register")
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    /**
     * 账号密码登录
     */
    @PostMapping("/login")
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES)
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 退出登录（JWT 无状态，由前端清理本地会话）
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success(null);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/me")
    public Result<UserDTO> me() {
        return Result.success(authService.currentUser());
    }
}
