package interview.guide.modules.admin.service;

import interview.guide.common.constant.CommonConstants;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.common.result.PageResult;
import interview.guide.modules.admin.model.AdminResumeDTO;
import interview.guide.modules.admin.model.AdminResumeOwnerDTO;
import interview.guide.modules.admin.model.AdminResumeQueryRequest;
import interview.guide.modules.admin.model.AdminResumeSort;
import interview.guide.modules.admin.model.AdminResumeStatsDTO;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeAnalysisRepository;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.service.ResumeHistoryService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("管理员简历查询")
class AdminResumeServiceTest {

    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private ResumeAnalysisRepository resumeAnalysisRepository;
    @Mock
    private InterviewSessionRepository interviewSessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResumeHistoryService resumeHistoryService;

    private AdminResumeService service;

    @BeforeEach
    void setUp() {
        service = new AdminResumeService(resumeRepository, resumeAnalysisRepository,
            interviewSessionRepository, userRepository, resumeHistoryService);
    }

    @Test
    @DisplayName("返回全平台简历并补充归属用户、评分与面试次数")
    void aggregatesResumesFromAllUsers() {
        LocalDateTime analyzedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
        ResumeEntity aliceResume = resume(1L, 1L, "alice.pdf", AsyncTaskStatus.COMPLETED);
        ResumeEntity bobResume = resume(2L, 2L, "bob.pdf", AsyncTaskStatus.PENDING);
        when(resumeRepository.findAllByOrderByUploadedAtDesc())
            .thenReturn(List.of(aliceResume, bobResume));
        when(resumeAnalysisRepository.findLatestScoresByResumeIds(List.of(1L, 2L)))
            .thenReturn(List.<Object[]>of(new Object[]{1L, 88, analyzedAt}));
        when(interviewSessionRepository.countByResumeIds(List.of(1L, 2L)))
            .thenReturn(List.<Object[]>of(new Object[]{1L, 3L}));
        when(userRepository.findAllById(List.of(1L, 2L)))
            .thenReturn(List.of(user(1L, "alice"), user(2L, "bob")));

        List<AdminResumeDTO> result = service.getAllResumes();

        assertThat(result).hasSize(2);

        AdminResumeDTO aliceItem = result.get(0);
        assertThat(aliceItem.username()).isEqualTo("alice");
        assertThat(aliceItem.latestScore()).isEqualTo(88);
        assertThat(aliceItem.lastAnalyzedAt()).isEqualTo(analyzedAt);
        assertThat(aliceItem.interviewCount()).isEqualTo(3);
        assertThat(aliceItem.analyzeStatus()).isEqualTo(AsyncTaskStatus.COMPLETED);

        AdminResumeDTO bobItem = result.get(1);
        assertThat(bobItem.username()).isEqualTo("bob");
        assertThat(bobItem.latestScore()).isNull();
        assertThat(bobItem.interviewCount()).isZero();
    }

    @Test
    @DisplayName("管理员下载简历文件委托历史服务")
    void downloadResumeFileDelegatesToHistoryService() {
        ResumeHistoryService.ResumeFile file =
            new ResumeHistoryService.ResumeFile(new byte[]{1, 2}, "alice.pdf", "application/pdf");
        when(resumeHistoryService.downloadResumeFileForAdmin(1L)).thenReturn(file);

        assertThat(service.downloadResumeFile(1L)).isSameAs(file);
    }

    @Test
    @DisplayName("没有简历时返回空列表且不查询关联数据")
    void emptyWhenNoResumes() {
        when(resumeRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());

        assertThat(service.getAllResumes()).isEmpty();

        verifyNoInteractions(resumeAnalysisRepository, interviewSessionRepository, userRepository);
    }

    @Test
    @DisplayName("归属用户已删除时用户名为空")
    void usernameNullWhenUserMissing() {
        ResumeEntity orphanResume = resume(1L, 99L, "orphan.pdf", AsyncTaskStatus.FAILED);
        when(resumeRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(orphanResume));
        when(resumeAnalysisRepository.findLatestScoresByResumeIds(List.of(1L))).thenReturn(List.of());
        when(interviewSessionRepository.countByResumeIds(List.of(1L))).thenReturn(List.of());
        when(userRepository.findAllById(List.of(99L))).thenReturn(List.of());

        List<AdminResumeDTO> result = service.getAllResumes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isNull();
        assertThat(result.get(0).userId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("查询表单空值归一化为默认分页参数")
    void queryDefaultsNormalized() {
        AdminResumeQueryRequest query =
            new AdminResumeQueryRequest(null, null, null, null, null, null);

        assertThat(query.keyword()).isNull();
        assertThat(query.page()).isEqualTo(CommonConstants.Pagination.DEFAULT_PAGE);
        assertThat(query.size()).isEqualTo(CommonConstants.Pagination.DEFAULT_SIZE);
        assertThat(query.sort()).isEqualTo(AdminResumeSort.UPLOADED_AT_DESC);
    }

    @Test
    @DisplayName("查询表单空白关键词归一化为空")
    void blankKeywordNormalizedToNull() {
        AdminResumeQueryRequest query =
            new AdminResumeQueryRequest("   ", null, null, null, 1, 10);

        assertThat(query.keyword()).isNull();
    }

    @Test
    @DisplayName("每页条数超过上限时校验失败")
    void sizeOverMaxFailsValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            AdminResumeQueryRequest query =
                new AdminResumeQueryRequest(null, null, null, null, 1, CommonConstants.Pagination.MAX_SIZE + 1);

            assertThat(validator.validate(query)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("分页查询将表单转换为 Pageable 并封装分页结果")
    void queryResumesMapsPageableAndResult() {
        AdminResumeQueryRequest query = new AdminResumeQueryRequest(
            "  alice  ", AsyncTaskStatus.COMPLETED, 3L, AdminResumeSort.ACCESS_COUNT_DESC, 2, 10);
        Pageable expectedPageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "accessCount"));
        when(resumeRepository.searchForAdmin("alice", AsyncTaskStatus.COMPLETED, 3L, expectedPageable))
            .thenReturn(new PageImpl<>(List.of(), expectedPageable, 25));

        PageResult<AdminResumeDTO> result = service.queryResumes(query);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("统计信息汇总全平台数据")
    void statisticsAggregatePlatformData() {
        when(resumeRepository.count()).thenReturn(137L);
        when(resumeRepository.countDistinctUsers()).thenReturn(12L);
        when(resumeRepository.countByAnalyzeStatusIn(
            List.of(AsyncTaskStatus.PENDING, AsyncTaskStatus.PROCESSING))).thenReturn(3L);
        when(interviewSessionRepository.countByResumeIsNotNull()).thenReturn(45L);

        AdminResumeStatsDTO stats = service.getStatistics();

        assertThat(stats.totalResumes()).isEqualTo(137);
        assertThat(stats.userCount()).isEqualTo(12);
        assertThat(stats.analyzingCount()).isEqualTo(3);
        assertThat(stats.interviewCount()).isEqualTo(45);
    }

    @Test
    @DisplayName("用户筛选项补全用户名并保留简历数")
    void ownersIncludeUsernameAndCount() {
        when(resumeRepository.countGroupByUserId())
            .thenReturn(List.<Object[]>of(new Object[]{1L, 5L}, new Object[]{2L, 2L}));
        when(userRepository.findAllById(List.of(1L, 2L)))
            .thenReturn(List.of(user(1L, "alice")));

        List<AdminResumeOwnerDTO> owners = service.getOwners();

        assertThat(owners).hasSize(2);
        assertThat(owners.get(0).username()).isEqualTo("alice");
        assertThat(owners.get(0).resumeCount()).isEqualTo(5);
        assertThat(owners.get(1).userId()).isEqualTo(2L);
        assertThat(owners.get(1).username()).isNull();
    }

    private ResumeEntity resume(Long id, Long userId, String filename, AsyncTaskStatus status) {
        ResumeEntity entity = new ResumeEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setOriginalFilename(filename);
        entity.setFileSize(1024L);
        entity.setAccessCount(1);
        entity.setUploadedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        entity.setAnalyzeStatus(status);
        return entity;
    }

    private UserEntity user(Long id, String username) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername(username);
        return entity;
    }
}
