package interview.guide.modules.auth.service;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.JwtService;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.mapper.UserMapper;
import interview.guide.modules.auth.model.AuthResponse;
import interview.guide.modules.auth.model.LoginRequest;
import interview.guide.modules.auth.model.RegisterRequest;
import interview.guide.modules.auth.model.UserDTO;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 测试")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private UserEntity userEntity() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setUsername("tester");
        entity.setPasswordHash("hashed-password");
        return entity;
    }

    @Nested
    @DisplayName("注册")
    class Register {

        @Test
        @DisplayName("注册成功时保存哈希密码并返回登录态")
        void registerSuccess() {
            when(userRepository.existsByUsername("tester")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed-password");
            when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity entity = invocation.getArgument(0);
                entity.setId(1L);
                return entity;
            });
            when(jwtService.issue(1L, "tester")).thenReturn("token");
            when(userMapper.toDTO(any(UserEntity.class)))
                .thenReturn(new UserDTO(1L, "tester", false, null));

            AuthResponse response = authService.register(new RegisterRequest("tester", "secret123"));

            assertThat(response.token()).isEqualTo("token");
            assertThat(response.user().username()).isEqualTo("tester");

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getPasswordHash())
                .isEqualTo("hashed-password")
                .isNotEqualTo("secret123");
        }

        @Test
        @DisplayName("用户名已存在时拒绝注册")
        void registerDuplicateRejected() {
            when(userRepository.existsByUsername("tester")).thenReturn(true);

            BusinessException ex = catchThrowableOfType(
                () -> authService.register(new RegisterRequest("tester", "secret123")), BusinessException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getCode()).isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS.getCode());
            verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        }
    }

    @Nested
    @DisplayName("登录")
    class Login {

        @Test
        @DisplayName("密码正确时签发 Token")
        void loginSuccess() {
            when(userRepository.findByUsername("tester")).thenReturn(Optional.of(userEntity()));
            when(passwordEncoder.matches("secret123", "hashed-password")).thenReturn(true);
            when(jwtService.issue(1L, "tester")).thenReturn("token");
            when(userMapper.toDTO(any(UserEntity.class)))
                .thenReturn(new UserDTO(1L, "tester", false, null));

            AuthResponse response = authService.login(new LoginRequest("tester", "secret123"));

            assertThat(response.token()).isEqualTo("token");
            assertThat(response.user().id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("用户不存在时统一返回用户名或密码错误")
        void loginUserNotFound() {
            when(userRepository.findByUsername("tester")).thenReturn(Optional.empty());

            BusinessException ex = catchThrowableOfType(
                () -> authService.login(new LoginRequest("tester", "secret123")), BusinessException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getCode()).isEqualTo(ErrorCode.USERNAME_OR_PASSWORD_INCORRECT.getCode());
        }

        @Test
        @DisplayName("密码错误时统一返回用户名或密码错误")
        void loginWrongPassword() {
            when(userRepository.findByUsername("tester")).thenReturn(Optional.of(userEntity()));
            when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

            BusinessException ex = catchThrowableOfType(
                () -> authService.login(new LoginRequest("tester", "wrong")), BusinessException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getCode()).isEqualTo(ErrorCode.USERNAME_OR_PASSWORD_INCORRECT.getCode());
        }
    }

    @Nested
    @DisplayName("当前用户")
    class CurrentUser {

        @Test
        @DisplayName("未登录时返回未授权错误")
        void currentUserWithoutLogin() {
            BusinessException ex = catchThrowableOfType(
                () -> authService.currentUser(), BusinessException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        }

        @Test
        @DisplayName("已登录时返回用户信息")
        void currentUserSuccess() {
            UserContext.set(new AuthPrincipal(1L, "tester", false));
            when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity()));
            when(userMapper.toDTO(any(UserEntity.class)))
                .thenReturn(new UserDTO(1L, "tester", false, null));

            UserDTO user = authService.currentUser();

            assertThat(user.id()).isEqualTo(1L);
            assertThat(user.username()).isEqualTo("tester");
        }
    }
}
