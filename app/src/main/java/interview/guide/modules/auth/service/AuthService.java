package interview.guide.modules.auth.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户注册、登录与当前用户查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "用户名已存在");
        }

        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(request.password()));
        UserEntity saved = saveOrThrowDuplicate(entity);

        log.info("用户注册成功: id={}", saved.getId());
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username().trim())
            .orElseThrow(AuthService::credentialsError);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw credentialsError();
        }

        log.info("用户登录成功: id={}", user.getId());
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserDTO currentUser() {
        Long userId = UserContext.requireUserId();
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
        return userMapper.toDTO(user);
    }

    private UserEntity saveOrThrowDuplicate(UserEntity entity) {
        try {
            return userRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "用户名已存在");
        }
    }

    private AuthResponse toAuthResponse(UserEntity user) {
        String token = jwtService.issue(user.getId(), user.getUsername());
        return new AuthResponse(token, userMapper.toDTO(user));
    }

    private static BusinessException credentialsError() {
        return new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_INCORRECT, "用户名或密码错误");
    }
}
