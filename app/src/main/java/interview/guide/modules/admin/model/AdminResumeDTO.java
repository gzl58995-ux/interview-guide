package interview.guide.modules.admin.model;

import interview.guide.common.model.AsyncTaskStatus;

import java.time.LocalDateTime;

/**
 * 管理员视角的简历列表项DTO
 * 包含简历归属用户信息，用于平台级简历管理
 */
public record AdminResumeDTO(
    Long id,
    Long userId,
    String username,
    String filename,
    Long fileSize,
    LocalDateTime uploadedAt,
    Integer accessCount,
    Integer latestScore,
    LocalDateTime lastAnalyzedAt,
    Integer interviewCount,
    AsyncTaskStatus analyzeStatus,
    String analyzeError
) {}
