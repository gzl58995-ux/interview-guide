package interview.guide.modules.feedback.model;

import java.time.LocalDateTime;

/**
 * 反馈详情
 */
public record FeedbackDTO(
    Long id,
    Long userId,
    String username,
    FeedbackCategory category,
    String content,
    String pagePath,
    FeedbackStatus status,
    LocalDateTime handledAt,
    LocalDateTime createdAt
) {
}
