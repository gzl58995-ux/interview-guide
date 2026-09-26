package interview.guide.modules.interview.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.infrastructure.redis.InterviewSessionCache;
import interview.guide.infrastructure.redis.InterviewSessionCache.CachedSession;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.listener.EvaluateStreamProducer;
import interview.guide.modules.interview.model.CreateInterviewRequest;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import interview.guide.modules.interview.model.InterviewSessionDTO.SessionStatus;
import interview.guide.modules.interview.model.InterviewSessionEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("文本面试创建幂等性")
class InterviewSessionIdempotencyTest {

  @Mock
  private InterviewQuestionService questionService;
  @Mock
  private AnswerEvaluationService evaluationService;
  @Mock
  private InterviewPersistenceService persistenceService;
  @Mock
  private InterviewSessionCache sessionCache;
  @Mock
  private EvaluateStreamProducer evaluateStreamProducer;
  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private RedisService redisService;

  private ObjectMapper objectMapper;
  private InterviewSessionService service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    UserContext.set(new AuthPrincipal(1L, "tester", false));
    service = new InterviewSessionService(
        questionService,
        evaluationService,
        persistenceService,
        sessionCache,
        objectMapper,
        evaluateStreamProducer,
        llmProviderRegistry,
        redisService
    );
    when(redisService.executeWithLock(anyString(), anyLong(), anyLong(), any(), any()))
        .thenAnswer(invocation -> {
          RedisService.LockedOperation<?> operation = invocation.getArgument(4);
          return operation.execute();
        });
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  @DisplayName("Redis 结果映射丢失后应从数据库恢复同一会话，不再次调用 LLM")
  void restoresExistingSessionFromDatabaseWhenRedisMappingIsMissing() {
    String requestId = "request-20260803";
    String existingSessionId = "existing-session";
    InterviewQuestionDTO question = InterviewQuestionDTO.create(0, "什么是 JVM？", "JVM", "JVM");
    InterviewSessionEntity entity = new InterviewSessionEntity();
    entity.setSessionId(existingSessionId);
    entity.setRequestId(requestId);
    entity.setUserId(1L);
    when(redisService.get("interview:create:result:" + requestId)).thenReturn(null);
    when(persistenceService.findByRequestId(requestId)).thenReturn(Optional.of(entity));
    CachedSession cached = new CachedSession(
        existingSessionId,
        1L,
        "",
        null,
        null,
        null,
        List.of(question),
        0,
        SessionStatus.CREATED,
        objectMapper
    );
    when(sessionCache.getSession(existingSessionId)).thenReturn(Optional.of(cached));
    CreateInterviewRequest request = new CreateInterviewRequest(
        "",
        8,
        null,
        true,
        "glm",
        "java-backend",
        "mid",
        null,
        null,
        requestId
    );

    InterviewSessionDTO result = service.createSession(request);

    assertThat(result.sessionId()).isEqualTo(existingSessionId);
    verify(questionService, never()).generateQuestionsBySkill(
        any(), anyString(), anyString(), any(), anyInt(), any(), any(), any());
  }
}
