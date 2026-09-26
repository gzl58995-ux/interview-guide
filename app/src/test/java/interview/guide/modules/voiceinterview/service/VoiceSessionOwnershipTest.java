package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import interview.guide.modules.voiceinterview.dto.CreateSessionRequest;
import interview.guide.modules.voiceinterview.listener.VoiceEvaluateStreamProducer;
import interview.guide.modules.voiceinterview.model.VoiceInterviewSessionEntity;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewEvaluationRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewMessageRepository;
import interview.guide.modules.voiceinterview.repository.VoiceInterviewSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("语音面试数据按用户隔离")
class VoiceSessionOwnershipTest {

    @Mock
    private VoiceInterviewSessionRepository sessionRepository;
    @Mock
    private VoiceInterviewMessageRepository messageRepository;
    @Mock
    private VoiceInterviewEvaluationRepository evaluationRepository;
    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private VoiceInterviewProperties properties;
    @Mock
    private VoiceEvaluateStreamProducer voiceEvaluateStreamProducer;
    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private RBucket<VoiceInterviewSessionEntity> sessionBucket;

    private VoiceInterviewService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new AuthPrincipal(1L, "alice", false));
        service = new VoiceInterviewService(sessionRepository, messageRepository, evaluationRepository,
            resumeRepository, redissonClient, properties, voiceEvaluateStreamProducer, llmProviderRegistry);
        lenient().when(redissonClient.<VoiceInterviewSessionEntity>getBucket(anyString()))
            .thenReturn(sessionBucket);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("他人会话按不存在处理")
    void otherUsersSessionInvisible() {
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(session(2L, 2L)));

        assertThatThrownBy(() -> service.requireOwnedSession(2L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VOICE_SESSION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("本人会话可从缓存返回")
    void ownSessionAccessibleFromCache() {
        VoiceInterviewSessionEntity cached = session(2L, 1L);
        when(sessionBucket.get()).thenReturn(cached);

        assertThat(service.requireOwnedSession(2L)).isSameAs(cached);
    }

    @Test
    @DisplayName("创建会话时写入当前用户并拒绝关联他人简历")
    void createSessionUsesCurrentUserAndRejectsOtherUsersResume() {
        ResumeEntity other = new ResumeEntity();
        other.setId(5L);
        other.setUserId(2L);
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(other));

        CreateSessionRequest request = CreateSessionRequest.builder()
            .skillId("java-backend")
            .resumeId(5L)
            .build();

        assertThatThrownBy(() -> service.createSession(request))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("创建会话时归属当前用户")
    void createSessionOwnedByCurrentUser() {
        ResumeEntity own = new ResumeEntity();
        own.setId(5L);
        own.setUserId(1L);
        when(resumeRepository.findById(5L)).thenReturn(Optional.of(own));
        when(sessionRepository.save(any(VoiceInterviewSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateSessionRequest request = CreateSessionRequest.builder()
            .skillId("java-backend")
            .resumeId(5L)
            .build();
        service.createSession(request);

        ArgumentCaptor<VoiceInterviewSessionEntity> captor =
            ArgumentCaptor.forClass(VoiceInterviewSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getResumeId()).isEqualTo(5L);
    }

    private VoiceInterviewSessionEntity session(Long id, Long userId) {
        return VoiceInterviewSessionEntity.builder()
            .id(id)
            .userId(userId)
            .roleType("java-backend")
            .build();
    }
}
