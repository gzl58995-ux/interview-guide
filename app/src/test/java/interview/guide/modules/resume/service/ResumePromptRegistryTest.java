package interview.guide.modules.resume.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("简历提示词路由注册表")
class ResumePromptRegistryTest {

  private static final String TECH_SYSTEM = "classpath:prompts/resume-analysis/tech/system.st";
  private static final String TECH_USER = "classpath:prompts/resume-analysis/tech/user.st";
  private static final String SALES_SYSTEM = "classpath:prompts/resume-analysis/sales-bd/system.st";
  private static final String SALES_USER = "classpath:prompts/resume-analysis/sales-bd/user.st";

  private ResumeAnalysisProperties properties(Map<String, ResumeAnalysisProperties.PromptRoute> routes) {
    ResumeAnalysisProperties properties = new ResumeAnalysisProperties();
    properties.setDefaultDirection("TECH");
    properties.setPromptRoutes(new LinkedHashMap<>(routes));
    return properties;
  }

  private ResumeAnalysisProperties.PromptRoute route(String system, String user) {
    ResumeAnalysisProperties.PromptRoute route = new ResumeAnalysisProperties.PromptRoute();
    route.setSystemPromptPath(system);
    route.setUserPromptPath(user);
    return route;
  }

  private String content(String path) throws Exception {
    return new DefaultResourceLoader().getResource(path)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  @Nested
  @DisplayName("方向规范化")
  class NormalizeTests {

    @Test
    @DisplayName("空值与空白回退默认方向，大小写被规范化")
    void normalizesDirection() {
      ResumePromptRegistry registry = new ResumePromptRegistry(
          properties(Map.of("TECH", route(TECH_SYSTEM, TECH_USER))),
          new DefaultResourceLoader());

      assertThat(registry.normalizeDirection(null)).isEqualTo("TECH");
      assertThat(registry.normalizeDirection(" ")).isEqualTo("TECH");
      assertThat(registry.normalizeDirection(" tech ")).isEqualTo("TECH");
    }

    @Test
    @DisplayName("未注册方向抛出业务异常")
    void rejectsUnknownDirection() {
      ResumePromptRegistry registry = new ResumePromptRegistry(
          properties(Map.of("TECH", route(TECH_SYSTEM, TECH_USER))),
          new DefaultResourceLoader());

      assertThatThrownBy(() -> registry.normalizeDirection("UNKNOWN"))
          .isInstanceOfSatisfying(BusinessException.class,
              e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESUME_JOB_DIRECTION_UNSUPPORTED.getCode()));
    }
  }

  @Nested
  @DisplayName("按方向解析模板")
  class ResolveTests {

    @Test
    @DisplayName("返回该方向自己的系统与用户提示词")
    void resolvesDirectionTemplates() throws Exception {
      ResumePromptRegistry registry = new ResumePromptRegistry(
          properties(Map.of(
              "TECH", route(TECH_SYSTEM, TECH_USER),
              "SALES_BD", route(SALES_SYSTEM, SALES_USER))),
          new DefaultResourceLoader());

      ResumePromptRegistry.ResumePrompts prompts = registry.resolve(" sales_bd ");

      assertThat(prompts.direction()).isEqualTo("SALES_BD");
      assertThat(prompts.systemPrompt().render().trim())
          .isEqualTo(new PromptTemplate(content(SALES_SYSTEM)).render().trim());
      assertThat(prompts.userPrompt().render(Map.<String, Object>of("resumeText", "正文")).trim())
          .isEqualTo(new PromptTemplate(content(SALES_USER))
              .render(Map.<String, Object>of("resumeText", "正文")).trim());
    }
  }

  @Nested
  @DisplayName("启动期配置校验")
  class StartupTests {

    @Test
    @DisplayName("路由文件缺失时启动失败并指出方向与路径")
    void failsFastWhenPromptFileMissing() {
      ResumeAnalysisProperties properties = properties(Map.of(
          "TECH", route(TECH_SYSTEM, TECH_USER),
          "BROKEN", route("classpath:prompts/resume-analysis/broken/system.st",
              "classpath:prompts/resume-analysis/broken/user.st")));

      assertThatThrownBy(() -> new ResumePromptRegistry(properties, new DefaultResourceLoader()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("BROKEN")
          .hasMessageContaining("broken/system.st");
    }

    @Test
    @DisplayName("默认方向没有路由时启动失败")
    void failsFastWhenDefaultDirectionMissing() {
      ResumeAnalysisProperties properties = properties(Map.of(
          "SALES_BD", route(SALES_SYSTEM, SALES_USER)));

      assertThatThrownBy(() -> new ResumePromptRegistry(properties, new DefaultResourceLoader()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("TECH");
    }
  }
}
