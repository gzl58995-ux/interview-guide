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

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("语音面试求职方向解析")
class VoiceInterviewJobDirectionTest {

  @Mock private VoiceInterviewSessionRepository sessionRepository;
  @Mock private VoiceInterviewMessageRepository messageRepository;
  @Mock private VoiceInterviewEvaluationRepository evaluationRepository;
  @Mock private ResumeRepository resumeRepository;
  @Mock private RedissonClient redissonClient;
  @Mock private VoiceInterviewProperties properties;
  @Mock private VoiceEvaluateStreamProducer voiceEvaluateStreamProducer;
  @Mock private LlmProviderRegistry llmProviderRegistry;
  @Mock private VoiceJobDirectionPromptRegistry jobDirectionPromptRegistry;
  @Mock private RBucket<VoiceInterviewSessionEntity> sessionBucket;

  private VoiceInterviewService service;

  @BeforeEach
  void setUp() {
    UserContext.set(new AuthPrincipal(1L, "alice", false));
    service = new VoiceInterviewService(sessionRepository, messageRepository, evaluationRepository,
        resumeRepository, redissonClient, properties, voiceEvaluateStreamProducer, llmProviderRegistry,
        jobDirectionPromptRegistry);
    lenient().when(redissonClient.<VoiceInterviewSessionEntity>getBucket(anyString()))
        .thenReturn(sessionBucket);
    lenient().when(jobDirectionPromptRegistry.normalizeDirection(isNull())).thenReturn("TECH");
    lenient().when(jobDirectionPromptRegistry.normalizeDirection(anyString()))
        .thenAnswer(inv -> inv.getArgument(0, String.class).trim().toUpperCase(Locale.ROOT));
    lenient().when(sessionRepository.save(any(VoiceInterviewSessionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  private VoiceInterviewSessionEntity createdEntity(CreateSessionRequest request) {
    service.createSession(request);
    ArgumentCaptor<VoiceInterviewSessionEntity> captor =
        ArgumentCaptor.forClass(VoiceInterviewSessionEntity.class);
    verify(sessionRepository).save(captor.capture());
    return captor.getValue();
  }

  private ResumeEntity resume(Long userId, String jobDirection) {
    ResumeEntity resume = new ResumeEntity();
    resume.setUserId(userId);
    resume.setJobDirection(jobDirection);
    return resume;
  }

  @Test
  @DisplayName("请求显式方向优先于简历方向")
  void requestDirectionWinsOverResume() {
    when(resumeRepository.findById(5L)).thenReturn(Optional.of(resume(1L, "SALES_BD")));

    CreateSessionRequest request = CreateSessionRequest.builder()
        .skillId("java-backend")
        .resumeId(5L)
        .jobDirection("PROCUREMENT_SUPPLY_CHAIN")
        .build();

    assertThat(createdEntity(request).getJobDirection()).isEqualTo("PROCUREMENT_SUPPLY_CHAIN");
  }

  @Test
  @DisplayName("请求为空时继承简历上的求职方向")
  void fallsBackToResumeDirection() {
    when(resumeRepository.findById(5L)).thenReturn(Optional.of(resume(1L, "SALES_BD")));

    CreateSessionRequest request = CreateSessionRequest.builder()
        .skillId("java-backend")
        .resumeId(5L)
        .build();

    assertThat(createdEntity(request).getJobDirection()).isEqualTo("SALES_BD");
  }

  @Test
  @DisplayName("请求与简历都为空时使用默认 TECH")
  void defaultsToTech() {
    CreateSessionRequest request = CreateSessionRequest.builder()
        .skillId("java-backend")
        .build();

    assertThat(createdEntity(request).getJobDirection()).isEqualTo("TECH");
  }

  @Test
  @DisplayName("未注册方向抛出 VOICE_JOB_DIRECTION_UNSUPPORTED 且不落库")
  void rejectsUnsupportedDirection() {
    when(jobDirectionPromptRegistry.normalizeDirection("UNKNOWN"))
        .thenThrow(new BusinessException(ErrorCode.VOICE_JOB_DIRECTION_UNSUPPORTED, "不支持的求职方向: UNKNOWN"));

    CreateSessionRequest request = CreateSessionRequest.builder()
        .skillId("java-backend")
        .jobDirection("UNKNOWN")
        .build();

    assertThatThrownBy(() -> service.createSession(request))
        .isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VOICE_JOB_DIRECTION_UNSUPPORTED.getCode()));
    verify(sessionRepository, never()).save(any(VoiceInterviewSessionEntity.class));
  }
}
