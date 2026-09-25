package interview.guide.modules.resume.listener;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.transaction.TransactionalExecutor;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.service.ResumeGradingService;
import interview.guide.modules.resume.service.ResumeParseService;
import interview.guide.modules.resume.service.ResumePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.stream.StreamMessageId;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历分析 Stream ID-only 消息测试")
class AnalyzeStreamTest {

  @Mock
  private RedisService redisService;
  @Mock
  private ResumeRepository resumeRepository;
  @Mock
  private TransactionalExecutor transactionalExecutor;
  @Mock
  private ResumeGradingService gradingService;
  @Mock
  private ResumePersistenceService persistenceService;
  @Mock
  private ResumeParseService parseService;

  private AnalyzeStreamProducer producer;
  private AnalyzeStreamConsumer consumer;

  @BeforeEach
  void setUp() {
    producer = new AnalyzeStreamProducer(redisService, resumeRepository, transactionalExecutor);
    consumer = new AnalyzeStreamConsumer(redisService, gradingService,
        persistenceService, resumeRepository, parseService);
  }

  private ResumeAnalysisResponse analysis() {
    return new ResumeAnalysisResponse(0, null, null, null, null, null);
  }

  private ResumeEntity resume(String resumeText, String storageKey, String filename) {
    ResumeEntity resume = new ResumeEntity();
    resume.setId(5L);
    resume.setResumeText(resumeText);
    resume.setStorageKey(storageKey);
    resume.setOriginalFilename(filename);
    return resume;
  }

  private AnalyzeStreamConsumer.AnalyzePayload payload() {
    AnalyzeStreamConsumer.AnalyzePayload payload = new AnalyzeStreamConsumer.AnalyzePayload(5L);
    payload.setAttemptId("attempt-1");
    return payload;
  }

  private void invokeProcessMessage(AnalyzeStreamConsumer consumer, Map<String, String> data)
      throws Exception {
    Method method = interview.guide.common.async.AbstractStreamConsumer.class
        .getDeclaredMethod("processMessage", StreamMessageId.class, Map.class);
    method.setAccessible(true);
    method.invoke(consumer, new StreamMessageId(1, 0), new HashMap<>(data));
  }

  @Nested
  @DisplayName("生产者")
  class ProducerTests {

    @Test
    @DisplayName("Stream 消息只包含 resumeId 与 retryCount")
    void shouldSendIdOnlyMessage() {
      producer.sendAnalyzeTask(5L);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
      verify(redisService).streamAdd(anyString(), captor.capture(), anyInt());
      assertThat(captor.getValue())
          .containsEntry("resumeId", "5")
          .containsEntry("retryCount", "0")
          .hasSize(2);
    }
  }

  @Nested
  @DisplayName("消费者")
  class ConsumerTests {

    @Test
    @DisplayName("解析旧格式消息时忽略 content 字段")
    void shouldIgnoreLegacyContentField() {
      AnalyzeStreamConsumer.AnalyzePayload payload = consumer.parsePayload(
          new StreamMessageId(0, 0),
          Map.of("resumeId", "5", "content", "旧格式正文", "retryCount", "0"));

      assertThat(payload.resumeId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("从数据库正文直接分析")
    void shouldAnalyzeFromDatabaseText() {
      when(resumeRepository.findById(5L))
          .thenReturn(Optional.of(resume("数据库中的简历正文", "resume/5", "a.pdf")));
      when(gradingService.analyzeResume("数据库中的简历正文")).thenReturn(analysis());
      when(resumeRepository.heartbeatAnalyzeProcessing(
          org.mockito.ArgumentMatchers.eq(5L),
          org.mockito.ArgumentMatchers.eq("attempt-1"), any())).thenReturn(1);

      consumer.processBusiness(payload());

      verify(parseService, never()).downloadAndParseContent(anyString(), anyString());
      verify(persistenceService).saveAnalysisIfOwned(
          org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq("attempt-1"),
          any(ResumeAnalysisResponse.class));
    }

    @Test
    @DisplayName("历史正文为空时从 RustFS 恢复并回填后分析")
    void shouldRestoreTextFromStorageWhenMissing() {
      when(resumeRepository.findById(5L))
          .thenReturn(Optional.of(resume(null, "resume/5", "a.pdf")));
      when(parseService.downloadAndParseContent("resume/5", "a.pdf")).thenReturn("恢复的简历正文");
      when(gradingService.analyzeResume("恢复的简历正文")).thenReturn(analysis());
      when(resumeRepository.heartbeatAnalyzeProcessing(
          org.mockito.ArgumentMatchers.eq(5L),
          org.mockito.ArgumentMatchers.eq("attempt-1"), any())).thenReturn(1);

      consumer.processBusiness(payload());

      verify(persistenceService).updateResumeText(5L, "恢复的简历正文");
      verify(persistenceService).saveAnalysisIfOwned(
          org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.eq("attempt-1"),
          any(ResumeAnalysisResponse.class));
    }

    @Test
    @DisplayName("恢复后正文仍为空时抛出业务异常")
    void shouldThrowWhenRestoredTextStillEmpty() {
      when(resumeRepository.findById(5L))
          .thenReturn(Optional.of(resume("", "resume/5", "a.pdf")));
      when(parseService.downloadAndParseContent("resume/5", "a.pdf")).thenReturn(" ");

      when(resumeRepository.heartbeatAnalyzeProcessing(
          org.mockito.ArgumentMatchers.eq(5L),
          org.mockito.ArgumentMatchers.eq("attempt-1"), any())).thenReturn(1);

      assertThatThrownBy(() -> consumer.processBusiness(payload()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("无法获取简历文本内容");
      verify(gradingService, never()).analyzeResume(anyString());
    }

    @Test
    @DisplayName("实体已删除时跳过且不分析")
    void shouldSkipWhenEntityDeleted() {
      when(resumeRepository.findById(5L)).thenReturn(Optional.empty());

      consumer.processBusiness(payload());

      verify(gradingService, never()).analyzeResume(anyString());
    }

    @Test
    @DisplayName("AI 分析失败时标记 FAILED 并透出业务异常信息，不写入伪成功结果")
    void shouldMarkFailedWithBusinessMessage() throws Exception {
      when(resumeRepository.findById(5L))
          .thenReturn(Optional.of(resume("数据库中的简历正文", "resume/5", "a.pdf")));
      when(resumeRepository.tryMarkAnalyzeProcessing(
          org.mockito.ArgumentMatchers.eq(5L), anyString(), any())).thenReturn(1);
      when(gradingService.analyzeResume("数据库中的简历正文"))
          .thenThrow(new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "AI 分析失败：请稍后重试"));

      invokeProcessMessage(consumer, Map.of("resumeId", "5", "retryCount", "3"));

      ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
      verify(resumeRepository).failAnalyzeIfProcessing(
          org.mockito.ArgumentMatchers.eq(5L), anyString(),
          errorCaptor.capture(), any());
      assertThat(errorCaptor.getValue())
          .contains("简历分析失败")
          .contains("AI 分析失败：请稍后重试");
      verify(persistenceService, never()).saveAnalysisIfOwned(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("重试消息只携带 resumeId 与 retryCount")
    void shouldRetryWithIdOnlyMessage() {
      when(resumeRepository.resetAnalyzeToPending(org.mockito.ArgumentMatchers.eq(5L),
          org.mockito.ArgumentMatchers.eq("attempt-1"), org.mockito.ArgumentMatchers.any()))
          .thenReturn(1);
      consumer.retryMessage(payload(), 3);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
      verify(redisService).streamAdd(anyString(), captor.capture(), anyInt());
      assertThat(captor.getValue())
          .containsEntry("resumeId", "5")
          .containsEntry("retryCount", "3")
          .hasSize(2);
      assertThat(captor.getValue().keySet()).doesNotContain("content");
    }
  }
}
