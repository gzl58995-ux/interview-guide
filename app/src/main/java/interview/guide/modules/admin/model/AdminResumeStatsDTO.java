package interview.guide.modules.admin.model;

/**
 * 管理员简历统计信息
 */
public record AdminResumeStatsDTO(
    long totalResumes,
    long userCount,
    long analyzingCount,
    long interviewCount
) {}
