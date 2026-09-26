package interview.guide.modules.auth.model;

import java.time.LocalDateTime;

/**
 * 用户信息
 */
public record UserDTO(Long id, String username, boolean admin, LocalDateTime createdAt) {
}
