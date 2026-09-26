package interview.guide.modules.resume.service;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.file.FileHashService;
import interview.guide.infrastructure.mapper.ResumeMapper;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeAnalysisRepository;
import interview.guide.modules.resume.repository.ResumeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历数据按用户隔离")
class ResumeOwnershipIsolationTest {

    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private ResumeAnalysisRepository analysisRepository;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private FileHashService fileHashService;

    private ResumePersistenceService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new AuthPrincipal(1L, "alice", false));
        service = new ResumePersistenceService(resumeRepository, analysisRepository,
            new ObjectMapper(), resumeMapper, fileHashService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("他人简历按不存在处理")
    void otherUsersResumeInvisible() {
        ResumeEntity other = resume(9L, 2L);
        when(resumeRepository.findById(9L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.requireOwnedById(9L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESUME_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("本人简历可以正常访问")
    void ownResumeAccessible() {
        ResumeEntity own = resume(9L, 1L);
        when(resumeRepository.findById(9L)).thenReturn(Optional.of(own));

        assertThat(service.requireOwnedById(9L)).isSameAs(own);
    }

    @Test
    @DisplayName("列表只查询当前用户且不使用全局查询")
    void listScopedToCurrentUser() {
        when(resumeRepository.findByUserIdOrderByUploadedAtDesc(1L)).thenReturn(List.of());

        service.findAllResumes();

        verify(resumeRepository).findByUserIdOrderByUploadedAtDesc(1L);
        verify(resumeRepository, never()).findAll();
    }

    private ResumeEntity resume(Long id, Long userId) {
        ResumeEntity entity = new ResumeEntity();
        entity.setId(id);
        entity.setUserId(userId);
        return entity;
    }
}
