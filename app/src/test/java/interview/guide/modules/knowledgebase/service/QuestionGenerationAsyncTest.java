package interview.guide.modules.knowledgebase.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.PromptSanitizer;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.knowledgebase.listener.QuestionGenStreamConsumer;
import interview.guide.modules.knowledgebase.listener.QuestionGenStreamProducer;
import interview.guide.modules.knowledgebase.model.GenerateKnowledgeBaseQuestionsRequest;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionFollowUpDTO;
import interview.guide.modules.knowledgebase.model.QuestionGenStatus;
import interview.guide.modules.knowledgebase.model.QuestionGenStatusResponse;
import interview.guide.modules.knowledgebase.model.QuestionGenerationConfig;
import interview.guide.modules.knowledgebase.model.VectorStatus;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.infrastructure.redis.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.redisson.api.stream.StreamMessageId;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("知识库问题异步生成")
class QuestionGenerationAsyncTest {

  @Mock
  private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock
  private KnowledgeBaseQuestionRepository questionRepository;
  @Mock
  private KnowledgeBaseVectorService vectorService;
  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private StructuredOutputInvoker structuredOutputInvoker;
  @Mock
  private PromptSanitizer promptSanitizer;
  @Mock
  private QuestionGenStreamProducer questionGenStreamProducer;
  @Mock
  private RedisService redisService;
  @Mock
  private ChatClient chatClient;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private KnowledgeBaseQuestionService questionService;
  private KnowledgeBaseQuestionGenerationService generationService;
  private QuestionGenerationStateService stateService;

  @BeforeEach
  void setUp() throws Exception {
    UserContext.set(new AuthPrincipal(1L, "tester", false));
    stateService = new QuestionGenerationStateService(
        knowledgeBaseRepository,
        questionRepository,
        objectMapper
    );

    questionService = new KnowledgeBaseQuestionService(
        knowledgeBaseRepository,
        questionRepository,
        objectMapper,
        questionGenStreamProducer,
        stateService
    );

    generationService = new KnowledgeBaseQuestionGenerationService(
        knowledgeBaseRepository,
        questionRepository,
        vectorService,
        llmProviderRegistry,
        structuredOutputInvoker,
        promptSanitizer,
        stateService,
        objectMapper
    );

    // 注入 @Value 字段
    Resource systemResource = new ClassPathResource("prompts/knowledgebase-question-generation-system.st");
    Resource userResource = new ClassPathResource("prompts/knowledgebase-question-generation-user.st");
    setField(KnowledgeBaseQuestionGenerationService.class, "systemPromptResource", systemResource);
    setField(KnowledgeBaseQuestionGenerationService.class, "userPromptResource", userResource);

    when(promptSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    when(promptSanitizer.wrapWithDelimiters(anyString(), anyString())).thenReturn("wrapped");
    when(vectorService.similaritySearch(anyString(), anyList(), anyInt(), anyDouble()))
        .thenReturn(List.of(new Document("知识库片段内容")));
    lenient().when(llmProviderRegistry.getPlainChatClient(nullable(String.class))).thenReturn(chatClient);

  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  private void setField(Class<?> clazz, String fieldName, Object value) throws Exception {
    var field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(generationService, value);
  }

  private KnowledgeBaseEntity buildKb(Long id, QuestionGenStatus status, String taskId) {
    KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
    kb.setId(id);
    kb.setUserId(1L);
    kb.setName("测试知识库");
    kb.setFileHash("hash-test");
    kb.setVectorStatus(VectorStatus.COMPLETED);
    kb.setQuestionGenStatus(status);
    kb.setQuestionGenTaskId(taskId);
    return kb;
  }

  @Nested
  @DisplayName("提交生成任务")
  class SubmitTask {

    @Test
    @DisplayName("首次提交后状态变为 QUEUED，并成功投递 Redis Stream 消息")
    void shouldSetQueuedAndSendToStream() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.NONE, null);
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(questionGenStreamProducer.sendGenerateTask(eq(1L), anyString())).thenReturn(true);

      QuestionGenStatusResponse response = questionService.submitGenerationTask(
          1L, new GenerateKnowledgeBaseQuestionsRequest("mid", 5, 2, 3, null));

      assertThat(response.questionGenStatus()).isEqualTo(QuestionGenStatus.QUEUED);
      assertThat(response.questionGenTaskId()).isNotNull();
      assertThat(kb.getQuestionGenStatus()).isEqualTo(QuestionGenStatus.QUEUED);
      assertThat(kb.getQuestionGenTaskId()).isEqualTo(response.questionGenTaskId());
      verify(questionGenStreamProducer).sendGenerateTask(eq(1L), anyString());
    }

    @Test
    @DisplayName("等待处理时重复提交会被拒绝")
    void shouldRejectWhenAlreadyQueued() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.QUEUED, "existing-task");
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

      assertThatThrownBy(() -> questionService.submitGenerationTask(
          1L, new GenerateKnowledgeBaseQuestionsRequest("mid", 5, 2, 3, null)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("正在生成中");

      verify(questionGenStreamProducer, never()).sendGenerateTask(any(), anyString());
    }

    @Test
    @DisplayName("生成中时重复提交会被拒绝")
    void shouldRejectWhenAlreadyProcessing() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "existing-task");
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

      assertThatThrownBy(() -> questionService.submitGenerationTask(
          1L, new GenerateKnowledgeBaseQuestionsRequest("mid", 5, 2, 3, null)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("正在生成中");

      verify(questionGenStreamProducer, never()).sendGenerateTask(any(), anyString());
    }

    @Test
    @DisplayName("生成失败后可以重新提交")
    void shouldAllowResubmitAfterFailed() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.FAILED, "old-task");
      kb.setQuestionGenError("之前的错误");
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(questionGenStreamProducer.sendGenerateTask(eq(1L), anyString())).thenReturn(true);

      QuestionGenStatusResponse response = questionService.submitGenerationTask(
          1L, new GenerateKnowledgeBaseQuestionsRequest("mid", 5, 2, 3, null));

      assertThat(response.questionGenStatus()).isEqualTo(QuestionGenStatus.QUEUED);
      assertThat(kb.getQuestionGenError()).isNull();
      assertThat(kb.getQuestionGenTaskId()).isNotEqualTo("old-task");
    }
  }

  @Nested
  @DisplayName("Consumer 执行生成")
  class ConsumerExecution {

    @Test
    @DisplayName("Consumer 开始执行后状态变为 PROCESSING")
    void shouldMarkProcessing() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.QUEUED, "task-1");
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // 模拟 Consumer 的 shouldSkip 检查
      QuestionGenStreamConsumer consumer = new QuestionGenStreamConsumer(
          redisService, generationService, stateService, questionGenStreamProducer);

      boolean claimed = invokeTryMarkProcessing(consumer,
          new QuestionGenStreamConsumer.QuestionGenPayload(
              1L, "task-1"));
      assertThat(claimed).isTrue();
      assertThat(kb.getQuestionGenStatus()).isEqualTo(QuestionGenStatus.PROCESSING);
    }

    @Test
    @DisplayName("知识库已删除时 Consumer 跳过任务")
    void shouldSkipWhenKbDeleted() {
      when(knowledgeBaseRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

      QuestionGenStreamConsumer consumer = new QuestionGenStreamConsumer(
          redisService, generationService, stateService, questionGenStreamProducer);

      boolean claimed = invokeTryMarkProcessing(consumer,
          new QuestionGenStreamConsumer.QuestionGenPayload(
              99L, "task-1"));
      assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("旧任务ID不匹配时 Consumer 跳过任务（幂等性）")
    void shouldSkipWhenTaskIdMismatch() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.QUEUED, "newer-task");
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

      QuestionGenStreamConsumer consumer = new QuestionGenStreamConsumer(
          redisService, generationService, stateService, questionGenStreamProducer);

      boolean claimed = invokeTryMarkProcessing(consumer,
          new QuestionGenStreamConsumer.QuestionGenPayload(
              1L, "old-task"));
      assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("状态非 QUEUED 时 Consumer 跳过任务（重复消费幂等）")
    void shouldSkipWhenStatusNotQueued() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.COMPLETED, "task-1");
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

      QuestionGenStreamConsumer consumer = new QuestionGenStreamConsumer(
          redisService, generationService, stateService, questionGenStreamProducer);

      boolean claimed = invokeTryMarkProcessing(consumer,
          new QuestionGenStreamConsumer.QuestionGenPayload(
              1L, "task-1"));
      assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("执行失败重新入队前应将状态恢复为 QUEUED")
    void shouldResetToQueuedBeforeRetry() throws Exception {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.QUEUED, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      kb.setQuestionGenConfig(objectMapper.writeValueAsString(
          new QuestionGenerationConfig("mid", 5, 2, 3, null)));
      when(questionGenStreamProducer.sendGenerateTask(1L, "task-1", 1)).thenReturn(true);

      KnowledgeBaseQuestionGenerationService failingService =
          mock(KnowledgeBaseQuestionGenerationService.class);
      doThrow(new BusinessException(
          interview.guide.common.exception.ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
          "模拟失败"))
          .when(failingService)
          .executeGeneration(eq(1L), eq("task-1"), any(QuestionGenerationConfig.class));

      QuestionGenStreamConsumer consumer = new QuestionGenStreamConsumer(
          redisService, failingService, stateService, questionGenStreamProducer);
      invokeProcessMessage(consumer, Map.of(
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_KB_ID, "1",
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_TASK_ID, "task-1",
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_DIFFICULTY, "mid",
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_QUESTION_COUNT, "5",
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_FOLLOW_UP_COUNT, "2",
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_CATEGORY_LIMIT, "3",
          interview.guide.common.constant.AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
      ));

      assertThat(kb.getQuestionGenStatus()).isEqualTo(QuestionGenStatus.QUEUED);
      verify(questionGenStreamProducer).sendGenerateTask(1L, "task-1", 1);
    }
  }

  @Nested
  @DisplayName("生成结果持久化")
  class GenerationPersistence {

    @Test
    @DisplayName("生成成功后批量保存问题并替换旧问题")
    @SuppressWarnings("unchecked")
    void shouldReplaceOldQuestionsOnSuccess() throws Exception {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(questionRepository.findCategoryCounts(1L)).thenReturn(List.of());
      when(questionRepository.findTop20ByKnowledgeBase_IdAndDifficultyOrderByUpdatedAtDesc(1L, "mid"))
          .thenReturn(List.of());
      when(questionRepository.deleteByKnowledgeBaseId(1L)).thenReturn(3);
      when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
      stubInvokerForGeneration(new KnowledgeBaseQuestionGenerationService.QuestionListDTO(List.of(
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              "Redis", null, "什么是Redis", "摘要", "参考答案",
              List.of("要点"), "规则", List.of()),
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              "JVM", null, "什么是JVM", "摘要", "参考答案",
              List.of("要点"), "规则", List.of())
      )));

      generationService.executeGeneration(
          1L, "task-1", new QuestionGenerationConfig("mid", 5, 2, 3, null));

      // 验证删除旧问题
      verify(questionRepository).deleteByKnowledgeBaseId(1L);
      // 验证批量保存
      ArgumentCaptor<List<KnowledgeBaseQuestionEntity>> captor =
          ArgumentCaptor.forClass(List.class);
      verify(questionRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("生成追问超过目标时截断，少于目标时保留有效草稿")
    @SuppressWarnings("unchecked")
    void shouldNormalizeGeneratedFollowUpsToTargetWithoutDroppingShortDrafts() throws Exception {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(questionRepository.findCategoryCounts(1L)).thenReturn(List.of());
      when(questionRepository.findTop20ByKnowledgeBase_IdAndDifficultyOrderByUpdatedAtDesc(1L, "mid"))
          .thenReturn(List.of());
      when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
      stubInvokerForGeneration(new KnowledgeBaseQuestionGenerationService.QuestionListDTO(List.of(
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              "Redis", null, "追问过多的题目", "摘要", "参考答案",
              List.of("要点"), "规则", List.of(
                  new KnowledgeBaseQuestionFollowUpDTO("追问1"),
                  new KnowledgeBaseQuestionFollowUpDTO("追问2"),
                  new KnowledgeBaseQuestionFollowUpDTO("追问3")
              )),
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              "JVM", null, "追问不足的题目", "摘要", "参考答案",
              List.of("要点"), "规则", List.of(
                  new KnowledgeBaseQuestionFollowUpDTO("唯一追问")
              ))
      )));

      generationService.executeGeneration(
          1L, "task-1", new QuestionGenerationConfig("mid", 2, 2, 3, null));

      ArgumentCaptor<List<KnowledgeBaseQuestionEntity>> captor =
          ArgumentCaptor.forClass(List.class);
      verify(questionRepository).saveAll(captor.capture());
      List<KnowledgeBaseQuestionEntity> saved = captor.getValue();
      TypeReference<List<KnowledgeBaseQuestionFollowUpDTO>> type = new TypeReference<>() {
      };
      assertThat(objectMapper.readValue(saved.get(0).getFollowUpsJson(), type)).hasSize(2);
      assertThat(objectMapper.readValue(saved.get(1).getFollowUpsJson(), type)).hasSize(1);
      assertThat(saved)
          .allSatisfy(question ->
              assertThat(question.getStatus()).isEqualTo(
                  interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionStatus.DRAFT));
    }

    @Test
    @DisplayName("LLM 调用失败后抛出异常（Consumer 会标记 FAILED）")
    void shouldThrowWhenLlmFails() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(questionRepository.findCategoryCounts(1L)).thenReturn(List.of());
      when(questionRepository.findTop20ByKnowledgeBase_IdAndDifficultyOrderByUpdatedAtDesc(1L, "mid"))
          .thenReturn(List.of());
      when(structuredOutputInvoker.invoke(
          eq(chatClient), anyString(), anyString(), any(),
          any(), anyString(), anyString(), any()))
          .thenThrow(new BusinessException(
              interview.guide.common.exception.ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
              "LLM调用超时"));

      assertThatThrownBy(() ->
          generationService.executeGeneration(
              1L, "task-1", new QuestionGenerationConfig("mid", 5, 2, 3, null)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("LLM调用超时");

      // 不应保存任何问题
      verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("任务ID不匹配时放弃生成，不覆盖新任务结果")
    void shouldAbortWhenTaskIdMismatchDuringGeneration() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "newer-task");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));

      // 不应抛异常，只是静默放弃
      generationService.executeGeneration(
          1L, "old-task", new QuestionGenerationConfig("mid", 5, 2, 3, null));

      verify(questionRepository, never()).deleteByKnowledgeBaseId(any());
      verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("LLM执行期间任务被替换时旧任务不能覆盖新题目")
    void shouldAbortWhenTaskIdChangesDuringLlmCall() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(questionRepository.findCategoryCounts(1L)).thenReturn(List.of());
      when(questionRepository.findTop20ByKnowledgeBase_IdAndDifficultyOrderByUpdatedAtDesc(1L, "mid"))
          .thenReturn(List.of());
      when(structuredOutputInvoker.invoke(
          eq(chatClient), anyString(), anyString(), any(),
          any(), anyString(), anyString(), any()))
          .thenAnswer(invocation -> {
            kb.setQuestionGenTaskId("task-2");
            kb.setQuestionGenStatus(QuestionGenStatus.QUEUED);
            return new KnowledgeBaseQuestionGenerationService.QuestionListDTO(List.of(
                new KnowledgeBaseQuestionGenerationService.QuestionDTO(
                    "Redis", null, "什么是Redis", "摘要", "参考答案",
                    List.of("要点"), "规则", List.of())
            ));
          });

      generationService.executeGeneration(
          1L, "task-1", new QuestionGenerationConfig("mid", 5, 2, 3, null));

      verify(questionRepository, never()).deleteByKnowledgeBaseId(any());
      verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("同批重复题只保存一次并记录跳过数量")
    @SuppressWarnings("unchecked")
    void shouldDeduplicateGeneratedBatch() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(questionRepository.findCategoryCounts(1L)).thenReturn(List.of());
      when(questionRepository.findTop20ByKnowledgeBase_IdAndDifficultyOrderByUpdatedAtDesc(1L, "mid"))
          .thenReturn(List.of());
      when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
      stubInvokerForGeneration(new KnowledgeBaseQuestionGenerationService.QuestionListDTO(List.of(
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              " ", null, "什么是依赖注入", "摘要", "参考答案",
              List.of("要点"), "规则", List.of()),
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              "Spring", null, "什么是 依赖注入", "摘要", "参考答案",
              List.of("要点"), "规则", List.of())
      )));

      generationService.executeGeneration(
          1L, "task-1", new QuestionGenerationConfig("mid", 2, 0, 3, null));

      ArgumentCaptor<List<KnowledgeBaseQuestionEntity>> captor =
          ArgumentCaptor.forClass(List.class);
      verify(questionRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      assertThat(captor.getValue().get(0).getCategory()).isEqualTo("测试知识库");
      assertThat(kb.getQuestionGenSkippedCount()).isEqualTo(1);
      assertThat(kb.getQuestionGenStatus()).isEqualTo(QuestionGenStatus.COMPLETED);
    }

    @Test
    @DisplayName("已有方向和题目会传入异步生成 Prompt")
    void shouldPassExistingQuestionsToPrompt() {
      KnowledgeBaseEntity kb = buildKb(1L, QuestionGenStatus.PROCESSING, "task-1");
      when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));
      when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
      when(questionRepository.findCategoryCounts(1L)).thenReturn(List.of(
          categoryCount("JVM", 5L),
          categoryCount("Spring", 3L)
      ));
      KnowledgeBaseQuestionEntity existing = new KnowledgeBaseQuestionEntity();
      existing.setQuestion("已有的 JVM 问题");
      when(questionRepository.findTop20ByKnowledgeBase_IdAndDifficultyOrderByUpdatedAtDesc(1L, "mid"))
          .thenReturn(List.of(existing));
      when(questionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
      stubInvokerForGeneration(new KnowledgeBaseQuestionGenerationService.QuestionListDTO(List.of(
          new KnowledgeBaseQuestionGenerationService.QuestionDTO(
              "JVM", null, "什么是内存模型", "摘要", "参考答案",
              List.of("要点"), "规则", List.of())
      )));

      generationService.executeGeneration(
          1L, "task-1", new QuestionGenerationConfig("mid", 1, 0, 2, null));

      ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
      verify(structuredOutputInvoker).invoke(
          eq(chatClient), anyString(), userPrompt.capture(), any(),
          any(), anyString(), anyString(), any());
      assertThat(userPrompt.getValue()).contains("JVM（5 题）");
      assertThat(userPrompt.getValue()).contains("Spring（3 题）");
      assertThat(userPrompt.getValue()).contains("已有的 JVM 问题");
    }
  }

  // ========== 辅助方法 ==========

  private boolean invokeTryMarkProcessing(
      QuestionGenStreamConsumer consumer,
      QuestionGenStreamConsumer.QuestionGenPayload payload
  ) {
    try {
      var consumerMethod = QuestionGenStreamConsumer.class.getDeclaredMethod("tryMarkProcessing",
          QuestionGenStreamConsumer.QuestionGenPayload.class);
      consumerMethod.setAccessible(true);
      return (boolean) consumerMethod.invoke(consumer, payload);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void stubInvokerForGeneration(KnowledgeBaseQuestionGenerationService.QuestionListDTO toReturn) {
    when(structuredOutputInvoker.invoke(
        eq(chatClient), anyString(), anyString(), any(),
        any(), anyString(), anyString(), any()))
        .thenReturn(toReturn);
  }

  private void invokeProcessMessage(
      QuestionGenStreamConsumer consumer,
      Map<String, String> data
  ) throws Exception {
    Method method = interview.guide.common.async.AbstractStreamConsumer.class
        .getDeclaredMethod("processMessage", StreamMessageId.class, Map.class);
    method.setAccessible(true);
    method.invoke(consumer, new StreamMessageId(1, 0), new HashMap<>(data));
  }

  private KnowledgeBaseQuestionRepository.CategoryCount categoryCount(
      String category,
      Long count
  ) {
    return new KnowledgeBaseQuestionRepository.CategoryCount() {
      @Override
      public String getCategory() {
        return category;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }
}
