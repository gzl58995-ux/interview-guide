package interview.guide.modules.admin.model;

/**
 * 管理员简历归属用户筛选项
 */
public record AdminResumeOwnerDTO(
    Long userId,
    String username,
    long resumeCount
) {}
