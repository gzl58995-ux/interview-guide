package interview.guide.modules.voiceinterview.service;

import interview.guide.common.ai.PromptSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("语音面试系统提示词组装")
class VoiceInterviewPromptServiceTest {

  private final PromptSanitizer promptSanitizer = mock(PromptSanitizer.class);

  @Test
  @DisplayName("系统提示词包含技能加载指令、输出约束与求职方向策略")
  void includesSkillConstraintsAndJobDirectionStrategy() {
    VoiceJobDirectionPromptRegistry registry = mock(VoiceJobDirectionPromptRegistry.class);
    when(registry.strategy("SALES_BD")).thenReturn("【求职方向：销售 / BD】\n- 以行为面试为主");
    VoiceInterviewPromptService service = new VoiceInterviewPromptService(promptSanitizer, registry);

    String prompt = service.generateSystemPromptWithContext("java-backend", "SALES_BD", null);

    assertThat(prompt).contains("java-backend");
    assertThat(prompt).contains("【语音面试输出约束】");
    assertThat(prompt).contains("【求职方向：销售 / BD】");
    assertThat(prompt).contains("以行为面试为主");
  }

  @Test
  @DisplayName("空方向同样交由注册表解析（默认方向策略仍会注入）")
  void blankDirectionDefersToRegistry() {
    VoiceJobDirectionPromptRegistry registry = mock(VoiceJobDirectionPromptRegistry.class);
    when(registry.strategy(null)).thenReturn("【求职方向：技术类】");
    VoiceInterviewPromptService service = new VoiceInterviewPromptService(promptSanitizer, registry);

    String prompt = service.generateSystemPromptWithContext(null, null, null);

    assertThat(prompt).contains("【求职方向：技术类】");
    assertThat(prompt).doesNotContain("java-backend");
  }
}
