package interview.guide.modules.knowledgebase.service;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseQuestionEntity;
import interview.guide.modules.knowledgebase.model.QuestionGenStatus;
import interview.guide.modules.knowledgebase.model.QuestionGenerationConfig;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseQuestionRepository;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.model.VectorStatus;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("问题生成状态持久化")
class QuestionGenerationStateServiceTest {

  @Mock
  private KnowledgeBaseRepository knowledgeBaseRepository;
  @Mock
  private KnowledgeBaseQuestionRepository questionRepository;

  private QuestionGenerationStateService service;

  @BeforeEach
  void setUp() {
    UserContext.set(new AuthPrincipal(1L, "tester", false));
    service = new QuestionGenerationStateService(
        knowledgeBaseRepository,
        questionRepository,
        new ObjectMapper()
    );
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  @DisplayName("创建任务时持久化配置并进入 QUEUED")
  void shouldCreateQueuedTaskWithConfig() {
    KnowledgeBaseEntity kb = buildKb(QuestionGenStatus.NONE, null);
    when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
    when(knowledgeBaseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    QuestionGenerationConfig config = new QuestionGenerationConfig("mid", 5, 2, 3, "default");
    var response = service.createTask(1L, config);

    assertThat(response.questionGenStatus()).isEqualTo(QuestionGenStatus.QUEUED);
    assertThat(response.questionGenTaskId()).isNotBlank();
    assertThat(response.questionGenConfig()).isEqualTo(config);
    assertThat(kb.getQuestionGenConfig()).contains("\"difficulty\":\"mid\"");
    assertThat(kb.getQuestionGenUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("活动任务存在时拒绝重复创建")
  void shouldRejectCreatingSecondActiveTask() {
    KnowledgeBaseEntity kb = buildKb(QuestionGenStatus.PROCESSING, "task-1");
    when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

    assertThatThrownBy(() -> service.createTask(
        1L, new QuestionGenerationConfig("mid", 5, 2, 3, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("正在生成中");

    verify(knowledgeBaseRepository, never()).save(any());
  }

  @Test
  @DisplayName("只有当前 QUEUED 任务可以原子领取")
  void shouldOnlyClaimCurrentQueuedTask() {
    KnowledgeBaseEntity kb = buildKb(QuestionGenStatus.QUEUED, "task-1");
    when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

    assertThat(service.tryMarkProcessing(1L, "task-1")).isTrue();
    assertThat(kb.getQuestionGenStatus()).isEqualTo(QuestionGenStatus.PROCESSING);
    assertThat(service.tryMarkProcessing(1L, "task-1")).isFalse();
    assertThat(service.tryMarkProcessing(1L, "old-task")).isFalse();
  }

  @Test
  @DisplayName("旧任务不能替换题目或更新完成状态")
  void shouldNotPersistResultForStaleTask() {
    KnowledgeBaseEntity kb = buildKb(QuestionGenStatus.PROCESSING, "task-2");
    when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));

    boolean completed = service.replaceQuestionsAndComplete(
        1L, "task-1", List.of(new KnowledgeBaseQuestionEntity()), 0);

    assertThat(completed).isFalse();
    verify(questionRepository, never()).deleteByKnowledgeBaseId(any());
    verify(questionRepository, never()).saveAll(anyList());
  }

  @Test
  @DisplayName("替换题目与完成状态在同一状态操作中提交")
  void shouldReplaceQuestionsAndCompleteCurrentTask() {
    KnowledgeBaseEntity kb = buildKb(QuestionGenStatus.PROCESSING, "task-1");
    when(knowledgeBaseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(kb));
    when(questionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

    KnowledgeBaseQuestionEntity question = new KnowledgeBaseQuestionEntity();
    boolean completed = service.replaceQuestionsAndComplete(
        1L, "task-1", List.of(question), 2);

    assertThat(completed).isTrue();
    assertThat(question.getKnowledgeBase()).isSameAs(kb);
    assertThat(kb.getQuestionGenStatus()).isEqualTo(QuestionGenStatus.COMPLETED);
    assertThat(kb.getQuestionGenSavedCount()).isEqualTo(1);
    assertThat(kb.getQuestionGenSkippedCount()).isEqualTo(2);
    verify(questionRepository).deleteByKnowledgeBaseId(1L);
    verify(questionRepository).saveAll(List.of(question));
  }

  private KnowledgeBaseEntity buildKb(QuestionGenStatus status, String taskId) {
    KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
    kb.setId(1L);
    kb.setUserId(1L);
    kb.setName("测试知识库");
    kb.setFileHash("hash");
    kb.setVectorStatus(VectorStatus.COMPLETED);
    kb.setQuestionGenStatus(status);
    kb.setQuestionGenTaskId(taskId);
    return kb;
  }
}
