package interview.guide.common.auth;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("UserContext 管理员校验")
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未登录时 isAdmin 为 false，requireAdmin 抛出未授权")
    void requireAdminWithoutLogin() {
        assertThat(UserContext.isAdmin()).isFalse();

        BusinessException ex = catchThrowableOfType(UserContext::requireAdmin, BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("普通用户 requireAdmin 抛出禁止访问")
    void requireAdminForNormalUser() {
        UserContext.set(new AuthPrincipal(1L, "alice", false));

        BusinessException ex = catchThrowableOfType(UserContext::requireAdmin, BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("管理员 requireAdmin 通过")
    void requireAdminForAdmin() {
        UserContext.set(new AuthPrincipal(1L, "admin", true));

        assertThat(UserContext.isAdmin()).isTrue();
        assertThatCode(UserContext::requireAdmin).doesNotThrowAnyException();
    }
}
