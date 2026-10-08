package interview.guide.modules.voiceinterview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("语音面试求职方向策略注册表")
class VoiceJobDirectionPromptRegistryTest {

  private static final String TECH = "classpath:prompts/voice-interview/job-direction/tech.st";
  private static final String SALES = "classpath:prompts/voice-interview/job-direction/sales-bd.st";

  private VoiceInterviewProperties properties(Map<String, String> routes) {
    VoiceInterviewProperties properties = new VoiceInterviewProperties();
    properties.setDefaultJobDirection("TECH");
    Map<String, VoiceInterviewProperties.JobDirectionRoute> routeMap = new LinkedHashMap<>();
    routes.forEach((direction, path) -> {
      VoiceInterviewProperties.JobDirectionRoute route = new VoiceInterviewProperties.JobDirectionRoute();
      route.setPromptPath(path);
      routeMap.put(direction, route);
    });
    properties.setJobDirectionRoutes(routeMap);
    return properties;
  }

  @Nested
  @DisplayName("方向规范化")
  class NormalizeTests {

    @Test
    @DisplayName("空值与空白回退默认方向，大小写被规范化")
    void normalizesDirection() {
      VoiceJobDirectionPromptRegistry registry = new VoiceJobDirectionPromptRegistry(
          properties(Map.of("TECH", TECH)), new DefaultResourceLoader());

      assertThat(registry.normalizeDirection(null)).isEqualTo("TECH");
      assertThat(registry.normalizeDirection(" ")).isEqualTo("TECH");
      assertThat(registry.normalizeDirection(" tech ")).isEqualTo("TECH");
    }

    @Test
    @DisplayName("未注册方向抛出业务异常")
    void rejectsUnknownDirection() {
      VoiceJobDirectionPromptRegistry registry = new VoiceJobDirectionPromptRegistry(
          properties(Map.of("TECH", TECH)), new DefaultResourceLoader());

      assertThatThrownBy(() -> registry.normalizeDirection("UNKNOWN"))
          .isInstanceOfSatisfying(BusinessException.class,
              e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VOICE_JOB_DIRECTION_UNSUPPORTED.getCode()));
    }
  }

  @Nested
  @DisplayName("按方向解析策略")
  class StrategyTests {

    @Test
    @DisplayName("小写方向码返回对应策略文件内容")
    void resolvesStrategyContent() throws Exception {
      VoiceJobDirectionPromptRegistry registry = new VoiceJobDirectionPromptRegistry(
          properties(Map.of("TECH", TECH, "SALES_BD", SALES)), new DefaultResourceLoader());
      String expected = new DefaultResourceLoader().getResource(SALES)
          .getContentAsString(StandardCharsets.UTF_8);

      assertThat(registry.strategy(" sales_bd ")).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("启动期配置校验")
  class StartupTests {

    @Test
    @DisplayName("策略文件缺失时启动失败并指出方向与路径")
    void failsFastWhenPromptFileMissing() {
      VoiceInterviewProperties properties = properties(Map.of(
          "TECH", TECH,
          "BROKEN", "classpath:prompts/voice-interview/job-direction/broken.st"));

      assertThatThrownBy(() -> new VoiceJobDirectionPromptRegistry(properties, new DefaultResourceLoader()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("BROKEN")
          .hasMessageContaining("broken.st");
    }

    @Test
    @DisplayName("默认方向没有策略时启动失败")
    void failsFastWhenDefaultDirectionMissing() {
      VoiceInterviewProperties properties = properties(Map.of("SALES_BD", SALES));

      assertThatThrownBy(() -> new VoiceJobDirectionPromptRegistry(properties, new DefaultResourceLoader()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("TECH");
    }
  }
}
