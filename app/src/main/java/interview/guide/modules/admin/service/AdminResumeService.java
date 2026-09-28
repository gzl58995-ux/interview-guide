package interview.guide.modules.admin.service;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.common.result.PageResult;
import interview.guide.modules.admin.model.AdminResumeDTO;
import interview.guide.modules.admin.model.AdminResumeOwnerDTO;
import interview.guide.modules.admin.model.AdminResumeQueryRequest;
import interview.guide.modules.admin.model.AdminResumeStatsDTO;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeDetailDTO;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeAnalysisRepository;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.service.ResumeHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员简历服务
 * 平台级简历查询，不做归属过滤，仅供管理员链路使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final UserRepository userRepository;
    private final ResumeHistoryService resumeHistoryService;

    /**
     * 获取全平台所有用户的简历列表
     */
    @Transactional(readOnly = true)
    public List<AdminResumeDTO> getAllResumes() {
        return toDTOs(resumeRepository.findAllByOrderByUploadedAtDesc());
    }

    /**
     * 按查询表单分页搜索全平台简历
     */
    @Transactional(readOnly = true)
    public PageResult<AdminResumeDTO> queryResumes(AdminResumeQueryRequest query) {
        Pageable pageable = PageRequest.of(
            query.page() - 1,
            query.size(),
            query.sort().toSort());
        Page<ResumeEntity> resumePage = resumeRepository.searchForAdmin(
            query.keyword(), query.analyzeStatus(), query.userId(), pageable);

        return PageResult.of(
            toDTOs(resumePage.getContent()),
            resumePage.getTotalElements(),
            query.page(),
            query.size());
    }

    /**
     * 获取全平台简历统计信息
     */
    @Transactional(readOnly = true)
    public AdminResumeStatsDTO getStatistics() {
        long totalResumes = resumeRepository.count();
        long userCount = resumeRepository.countDistinctUsers();
        long analyzingCount = resumeRepository.countByAnalyzeStatusIn(
            List.of(AsyncTaskStatus.PENDING, AsyncTaskStatus.PROCESSING));
        long interviewCount = interviewSessionRepository.countByResumeIsNotNull();

        return new AdminResumeStatsDTO(totalResumes, userCount, analyzingCount, interviewCount);
    }

    /**
     * 获取拥有简历的归属用户列表（用于用户筛选下拉）
     */
    @Transactional(readOnly = true)
    public List<AdminResumeOwnerDTO> getOwners() {
        List<Object[]> rows = resumeRepository.countGroupByUserId();
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = rows.stream()
            .map(row -> (Long) row[0])
            .toList();
        Map<Long, String> usernames = loadUsernamesByIds(userIds);

        return rows.stream()
            .map(row -> {
                Long userId = (Long) row[0];
                return new AdminResumeOwnerDTO(
                    userId,
                    usernames.get(userId),
                    ((Number) row[1]).longValue());
            })
            .toList();
    }

    /**
     * 获取任意用户的简历详情（平台级，不做归属校验）
     */
    public ResumeDetailDTO getResumeDetail(Long id) {
        return resumeHistoryService.getResumeDetailForAdmin(id);
    }

    /**
     * 导出任意用户的简历分析报告（平台级，不做归属校验）
     */
    public ResumeHistoryService.ExportResult exportAnalysisPdf(Long id) {
        return resumeHistoryService.exportAnalysisPdfForAdmin(id);
    }

    /**
     * 下载任意用户的原始简历文件（平台级，不做归属校验）
     */
    public ResumeHistoryService.ResumeFile downloadResumeFile(Long id) {
        return resumeHistoryService.downloadResumeFileForAdmin(id);
    }

    /**
     * 批量转换为DTO，用户名、最新评分、面试次数均为批量查询，避免逐条访问数据库
     */
    private List<AdminResumeDTO> toDTOs(List<ResumeEntity> resumes) {
        if (resumes.isEmpty()) {
            return List.of();
        }

        List<Long> resumeIds = resumes.stream().map(ResumeEntity::getId).toList();
        Map<Long, ScoreSnapshot> latestScores = loadLatestScores(resumeIds);
        Map<Long, Integer> interviewCounts = loadInterviewCounts(resumeIds);
        Map<Long, String> usernames = loadUsernamesByIds(
            resumes.stream().map(ResumeEntity::getUserId).distinct().toList());

        return resumes.stream()
            .map(resume -> {
                ScoreSnapshot score = latestScores.get(resume.getId());
                return new AdminResumeDTO(
                    resume.getId(),
                    resume.getUserId(),
                    usernames.get(resume.getUserId()),
                    resume.getOriginalFilename(),
                    resume.getFileSize(),
                    resume.getUploadedAt(),
                    resume.getAccessCount(),
                    score == null ? null : score.overallScore(),
                    score == null ? null : score.analyzedAt(),
                    interviewCounts.getOrDefault(resume.getId(), 0),
                    resume.getAnalyzeStatus(),
                    resume.getAnalyzeError()
                );
            })
            .toList();
    }

    /**
     * 批量查询每份简历的最新评测分数，避免逐条查询数据库
     */
    private Map<Long, ScoreSnapshot> loadLatestScores(List<Long> resumeIds) {
        Map<Long, ScoreSnapshot> latestScores = new HashMap<>();
        for (Object[] row : resumeAnalysisRepository.findLatestScoresByResumeIds(resumeIds)) {
            Long resumeId = (Long) row[0];
            Integer overallScore = (Integer) row[1];
            LocalDateTime analyzedAt = (LocalDateTime) row[2];
            latestScores.putIfAbsent(resumeId, new ScoreSnapshot(overallScore, analyzedAt));
        }
        return latestScores;
    }

    /**
     * 批量统计每份简历的面试次数
     */
    private Map<Long, Integer> loadInterviewCounts(List<Long> resumeIds) {
        return interviewSessionRepository.countByResumeIds(resumeIds).stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Number) row[1]).intValue()
            ));
    }

    /**
     * 批量加载用户名
     */
    private Map<Long, String> loadUsernamesByIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUsername));
    }

    private record ScoreSnapshot(Integer overallScore, LocalDateTime analyzedAt) {
    }
}
