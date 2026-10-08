package interview.guide.modules.voiceinterview.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.voiceinterview.config.VoiceInterviewProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 语音面试求职方向策略路由注册表
 * 启动时按配置加载各方向的提问策略，运行期按方向解析
 */
@Component
public class VoiceJobDirectionPromptRegistry {

    private final Map<String, String> strategyByDirection;
    private final String defaultDirection;

    public VoiceJobDirectionPromptRegistry(VoiceInterviewProperties properties, ResourceLoader resourceLoader) {
        this.defaultDirection = normalizeCode(properties.getDefaultJobDirection());
        Map<String, String> loaded = new LinkedHashMap<>();
        properties.getJobDirectionRoutes().forEach((direction, route) -> {
            String code = normalizeCode(direction);
            loaded.put(code, loadStrategy(resourceLoader, route.getPromptPath(), code));
        });
        if (!loaded.containsKey(defaultDirection)) {
            throw new IllegalStateException("默认求职方向未配置提问策略: " + defaultDirection);
        }
        this.strategyByDirection = Map.copyOf(loaded);
    }

    /**
     * 规范化方向代码：空值回退默认方向，未注册方向抛业务异常
     */
    public String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return defaultDirection;
        }
        String code = normalizeCode(direction);
        if (!strategyByDirection.containsKey(code)) {
            throw new BusinessException(ErrorCode.VOICE_JOB_DIRECTION_UNSUPPORTED,
                "不支持的求职方向: " + direction);
        }
        return code;
    }

    /**
     * 返回该方向的提问策略全文
     */
    public String strategy(String direction) {
        return strategyByDirection.get(normalizeDirection(direction));
    }

    private static String normalizeCode(String direction) {
        return direction == null ? "" : direction.trim().toUpperCase(Locale.ROOT);
    }

    private static String loadStrategy(ResourceLoader resourceLoader, String path, String direction) {
        String content;
        try {
            content = resourceLoader.getResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                "加载语音面试求职方向策略失败: direction=" + direction + ", path=" + path, e);
        }
        if (content.isBlank()) {
            throw new IllegalStateException(
                "语音面试求职方向策略内容为空: direction=" + direction + ", path=" + path);
        }
        return content;
    }
}
