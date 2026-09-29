package interview.guide.modules.resume.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 简历解析提示词路由注册表
 * 启动时按配置加载各求职方向的系统/用户提示词，运行期按方向解析
 */
@Component
public class ResumePromptRegistry {

    private final Map<String, ResumePrompts> promptsByDirection;
    private final String defaultDirection;

    public ResumePromptRegistry(ResumeAnalysisProperties properties, ResourceLoader resourceLoader) {
        this.defaultDirection = normalizeCode(properties.getDefaultDirection());
        Map<String, ResumePrompts> loaded = new LinkedHashMap<>();
        properties.getPromptRoutes().forEach((direction, route) -> {
            String code = normalizeCode(direction);
            loaded.put(code, new ResumePrompts(code,
                loadTemplate(resourceLoader, route.getSystemPromptPath(), code, "system"),
                loadTemplate(resourceLoader, route.getUserPromptPath(), code, "user")));
        });
        if (!loaded.containsKey(defaultDirection)) {
            throw new IllegalStateException("默认求职方向未配置提示词路由: " + defaultDirection);
        }
        this.promptsByDirection = Map.copyOf(loaded);
    }

    /**
     * 规范化方向代码：空值回退默认方向，未注册方向抛业务异常
     */
    public String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return defaultDirection;
        }
        String code = normalizeCode(direction);
        if (!promptsByDirection.containsKey(code)) {
            throw new BusinessException(ErrorCode.RESUME_JOB_DIRECTION_UNSUPPORTED,
                "不支持的求职方向: " + direction);
        }
        return code;
    }

    /**
     * 按方向解析系统与用户提示词模板
     */
    public ResumePrompts resolve(String direction) {
        return promptsByDirection.get(normalizeDirection(direction));
    }

    private static String normalizeCode(String direction) {
        return direction == null ? "" : direction.trim().toUpperCase(Locale.ROOT);
    }

    private static PromptTemplate loadTemplate(ResourceLoader resourceLoader, String path,
                                               String direction, String type) {
        try {
            String content = resourceLoader.getResource(path).getContentAsString(StandardCharsets.UTF_8);
            return new PromptTemplate(content);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                "加载简历提示词失败: direction=" + direction + ", type=" + type + ", path=" + path, e);
        }
    }

    public record ResumePrompts(String direction, PromptTemplate systemPrompt, PromptTemplate userPrompt) {
    }
}
