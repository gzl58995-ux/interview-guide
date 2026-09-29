package interview.guide.modules.resume.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("简历评分按方向注入提示词")
class ResumeGradingServiceTest {

  @Mock
  private LlmProviderRegistry llmProviderRegistry;
  @Mock
  private StructuredOutputInvoker structuredOutputInvoker;
  @Mock
  private ResumePromptRegistry promptRegistry;
  @Mock
  private ChatClient chatClient;

  private ResumeGradingService service() {
    return new ResumeGradingService(llmProviderRegistry, structuredOutputInvoker, promptRegistry);
  }

  private ResumePromptRegistry.ResumePrompts prompts(String direction, String system, String user) {
    return new ResumePromptRegistry.ResumePrompts(
        direction, new PromptTemplate(system), new PromptTemplate(user));
  }

  private void stubInvoker(ArgumentCaptor<String> systemCaptor, ArgumentCaptor<String> userCaptor) {
    when(llmProviderRegistry.getPlainChatClient()).thenReturn(chatClient);
    when(structuredOutputInvoker.invoke(any(), systemCaptor.capture(), userCaptor.capture(),
        any(), any(), anyString(), anyString(), any()))
        .thenThrow(new BusinessException(ErrorCode.RESUME_ANALYSIS_FAILED, "AI 分析失败：测试"));
  }

  @Test
  @DisplayName("使用指定方向的系统与用户提示词调用模型")
  void usesDirectionPrompts() {
    when(promptRegistry.resolve("SALES_BD"))
        .thenReturn(prompts("SALES_BD", "销售系统提示词", "销售用户提示词 {resumeText}"));
    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    stubInvoker(systemCaptor, userCaptor);

    assertThatThrownBy(() -> service().analyzeResume("候选人的简历正文", "SALES_BD"))
        .isInstanceOf(BusinessException.class);

    assertThat(systemCaptor.getValue()).startsWith("销售系统提示词");
    assertThat(userCaptor.getValue()).contains("候选人的简历正文");
  }

  @Test
  @DisplayName("方向为空时使用默认方向提示词")
  void fallsBackToDefaultDirection() {
    when(promptRegistry.resolve(null)).thenReturn(prompts("TECH", "技术系统提示词", "{resumeText}"));
    ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
    stubInvoker(systemCaptor, userCaptor);

    assertThatThrownBy(() -> service().analyzeResume("正文", null))
        .isInstanceOf(BusinessException.class);

    assertThat(systemCaptor.getValue()).startsWith("技术系统提示词");
  }

  @Test
  @DisplayName("未注册方向直接拒绝且不调用模型")
  void rejectsUnknownDirection() {
    when(promptRegistry.resolve("UNKNOWN"))
        .thenThrow(new BusinessException(ErrorCode.RESUME_JOB_DIRECTION_UNSUPPORTED, "不支持的求职方向"));

    assertThatThrownBy(() -> service().analyzeResume("正文", "UNKNOWN"))
        .isInstanceOf(BusinessException.class);

    verify(llmProviderRegistry, never()).getPlainChatClient();
    verify(structuredOutputInvoker, never())
        .invoke(any(), anyString(), anyString(), any(), any(), anyString(), anyString(), any());
  }
}
