package interview.guide.modules.interview.service;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.repository.InterviewAnswerRepository;
import interview.guide.modules.interview.repository.InterviewSessionRepository;
import interview.guide.modules.resume.model.ResumeEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("面试数据按用户隔离")
class InterviewOwnershipIsolationTest {

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private InterviewAnswerRepository answerRepository;
    @Mock
    private ResumeRepository resumeRepository;

    private InterviewPersistenceService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new AuthPrincipal(1L, "alice", false));
        service = new InterviewPersistenceService(sessionRepository, answerRepository,
            resumeRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("他人会话按不存在处理")
    void otherUsersSessionInvisible() {
        when(sessionRepository.findBySessionIdAndUserId("sid", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOwnedBySessionId("sid"))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INTERVIEW_SESSION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("面试列表与幂等键查询都带当前用户条件")
    void listAndIdempotencyQueriesScopedToCurrentUser() {
        when(sessionRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(sessionRepository.findByRequestIdAndUserId("req-1", 1L)).thenReturn(Optional.empty());

        service.findAll();
        service.findByRequestId("req-1");

        verify(sessionRepository).findByUserIdOrderByCreatedAtDesc(1L);
        verify(sessionRepository).findByRequestIdAndUserId("req-1", 1L);
        verify(sessionRepository, never()).findAll();
    }

    @Test
    @DisplayName("创建会话时拒绝关联他人简历")
    void rejectResumeOwnedByAnotherUser() {
        ResumeEntity other = new ResumeEntity();
        other.setId(5L);
        other.setUserId(2L);
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.saveSession("sid", 5L, 1, List.of(),
            "dashscope", "java-backend", "mid"))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESUME_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("会话创建时写入当前用户并关联本人简历")
    void sessionCreatedWithCurrentUser() {
        ResumeEntity own = new ResumeEntity();
        own.setId(5L);
        own.setUserId(1L);
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(own));
        when(sessionRepository.save(any(InterviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        InterviewSessionEntity saved = service.saveSession("sid", 5L, 1, List.of(),
            "dashscope", "java-backend", "mid");

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getResume()).isSameAs(own);
    }
}
