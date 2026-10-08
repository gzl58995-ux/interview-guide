package interview.guide.modules.voiceinterview.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Voice interview configuration properties
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.voice-interview")
public class VoiceInterviewProperties {

    private PhaseConfig phase = new PhaseConfig();
    private AliyunConfig aliyun = new AliyunConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private AudioConfig audio = new AudioConfig();
    private QwenConfig qwen = new QwenConfig();
    private OpeningConfig opening = new OpeningConfig();
    @Valid
    private ContextCompressionConfig contextCompression = new ContextCompressionConfig();
    /**
     * 默认求职方向（未指定且无简历方向时使用）。
     */
    private String defaultJobDirection = "TECH";
    /**
     * 求职方向提问策略路由：方向码 -> 策略提示词文件。
     */
    private Map<String, JobDirectionRoute> jobDirectionRoutes = new LinkedHashMap<>();

    /**
     * 语音面试单轮面试官回复最大字符数（超出会截断到句子边界）。
     */
    private int aiQuestionMaxChars = 120;
    /**
     * 是否启用 LLM 流式文本下发（先文本后音频），用于降低首字等待时延。
     */
    private boolean llmStreamingEnabled = true;
    /**
     * 流式文本最小推送间隔（毫秒），避免 WebSocket 过于频繁刷屏。
     */
    private int aiStreamPushIntervalMs = 180;
    /**
     * 流式文本推送最小增量字符数。
     */
    private int aiStreamMinCharsDelta = 12;
    /**
     * 每会话允许的并发 TTS 合成调用上限（防止 DashScope 连接限制）。
     */
    private int maxConcurrentTtsPerSession = 3;
    /**
     * 是否启用分块音频推送（每句 TTS 完成后立即推送，不等全部完成）。
     */
    private boolean chunkedAudioEnabled = true;
    /**
     * 单句 TTS 合成超时（秒），超时后跳过该句，用已成功的句子拼合音频。
     */
    private int ttsTimeoutSeconds = 8;
    /**
     * TTS WebSocket 建连超时（秒），避免 SDK 握手失败后永久等待。
     */
    private int ttsConnectTimeoutSeconds = 5;
    /**
     * 是否在应用启动时预热开场白音频缓存。默认关闭，避免启动时产生云端 TTS 调用。
     */
    private boolean openingAudioWarmupEnabled = false;

    @Data
    public static class PhaseConfig {
        private DurationConfig intro = new DurationConfig(3, 5, 8, 2, 5);
        private DurationConfig tech = new DurationConfig(8, 10, 15, 3, 8);
        private DurationConfig project = new DurationConfig(8, 10, 15, 2, 5);
        private DurationConfig hr = new DurationConfig(3, 5, 8, 2, 5);
    }

    @Data
    public static class DurationConfig {
        private int minDuration;
        private int suggestedDuration;
        private int maxDuration;
        private int minQuestions;
        private int maxQuestions;

        public DurationConfig(int min, int suggested, int max, int minQ, int maxQ) {
            this.minDuration = min;
            this.suggestedDuration = suggested;
            this.maxDuration = max;
            this.minQuestions = minQ;
            this.maxQuestions = maxQ;
        }
    }

    @Data
    public static class AliyunConfig {
        private SttConfig stt = new SttConfig();
        private TtsConfig tts = new TtsConfig();
    }

    @Data
    public static class SttConfig {
        private String appKey;
        private String accessKey;
        private String format = "opus";
        private int sampleRate = 16000;
    }

    @Data
    public static class TtsConfig {
        private String appKey;
        private String accessKey;
        private String voice = "xiaoyun";
        private String format = "mp3";
        private int sampleRate = 16000;
    }

    @Data
    public static class RateLimitConfig {
        private int maxPerSession = 10;
        private int maxPerIp = 3;
        private int maxConcurrent = 50;
    }

    @Data
    public static class AudioConfig {
        private String codec = "opus";
        private int sampleRate = 16000;
        private int bitRate = 24000;
        private int channels = 1;
        private int chunkDuration = 2000; // 2 seconds
    }

    @Data
    public static class QwenConfig {
        private AsrConfig asr = new AsrConfig();
        private QwenTtsConfig tts = new QwenTtsConfig();
    }

    @Data
    public static class AsrConfig {
        private String url = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
        private String model = "qwen3-asr-flash-realtime";
        private String apiKey;
        private String language = "zh";
        private String format = "pcm";
        private int sampleRate = 16000;
        private boolean enableTurnDetection = true;
        private String turnDetectionType = "server_vad";
        private float turnDetectionThreshold = 0.0f;
        private int turnDetectionSilenceDurationMs = 1000;
    }

    @Data
    public static class QwenTtsConfig {
        private String model = "qwen3-tts-flash-realtime";
        private String apiKey;
        private String voice = "Cherry";
        private String format = "pcm";
        private int sampleRate = 24000;
        private String mode = "commit";
        private String languageType = "Chinese";
        private float speechRate = 1.0f;
        private int volume = 60;
    }

    @Data
    public static class OpeningConfig {
        private Map<String, String> skillQuestions = new LinkedHashMap<>();
        private List<String> algorithmSkills = List.of("bytedance-backend", "algorithm");
        private String algorithmQuestion =
            "你好，我是本场面试官。先做一道算法与数据结构热身题：请你从“哈希表/堆/栈/队列/树/图”里选两个，结合一道你熟悉的题，口述“为什么选这个结构、核心步骤、时间复杂度、空间复杂度、边界条件与反例”。本场不需要写代码，重点看你的思路和取舍。";
        private String backendQuestion =
            "你好，我是本场面试官。第一个问题：请用 1 分钟介绍一个你深度参与的项目，按三点回答：业务目标、你负责的核心模块、核心技术栈。说完我会立刻追问一个关键技术决策。";
    }

    @Data
    public static class ContextCompressionConfig {
        /**
         * 是否启用上下文压缩。enabled=false 是唯一关闭方式：返回全量历史，仅用于本地排查。
         * enabled=true 时无论何种模式都执行字符硬预算。
         */
        private boolean enabled = true;
        /**
         * 压缩模式：NONE=不摘要不裁窗口但仍有字符硬预算；WINDOW=窗口+字符硬预算；
         * SUMMARY=摘要+窗口+字符硬预算（生产默认）。
         */
        private Mode mode = Mode.SUMMARY;
        /**
         * 保留的最近轮次数量（滑动窗口大小）。
         * 注意：SUMMARY 模式在两次摘要批次之间最多保留 windowSize + summaryBatchSize - 1 轮，
         * 轮数有界；Prompt 的最终硬上限由 maxHistoryChars 字符预算保证。
         */
        @Min(value = 1, message = "windowSize 必须大于等于 1")
        private int windowSize = 20;
        /**
         * 早期轮次每越过该批次数，触发一次增量摘要合并（降低 LLM 摘要调用频率）。
         */
        @Min(value = 1, message = "summaryBatchSize 必须大于等于 1")
        private int summaryBatchSize = 10;
        /**
         * 历史总字符预算：摘要字符数 + 近期历史格式化后字符数 <= maxHistoryChars。
         */
        @Min(value = 1, message = "maxHistoryChars 必须大于 0")
        private int maxHistoryChars = 12000;
        /**
         * 摘要字符上限：摘要字符数 <= maxSummaryChars，超出时从尾部截断。
         */
        @Min(value = 0, message = "maxSummaryChars 不能为负数")
        private int maxSummaryChars = 4000;

        /**
         * 摘要占满上限后至少要给近期消息留出空间。
         */
        @AssertTrue(message = "maxHistoryChars 必须大于等于 maxSummaryChars")
        public boolean isBudgetConsistent() {
            return maxHistoryChars >= maxSummaryChars;
        }

        /**
         * SUMMARY 模式依赖摘要，摘要上限必须为正数。
         */
        @AssertTrue(message = "SUMMARY 模式下 maxSummaryChars 必须大于 0")
        public boolean isSummaryBudgetPositive() {
            return mode != Mode.SUMMARY || maxSummaryChars > 0;
        }
    }

    @Data
    public static class JobDirectionRoute {
        private String promptPath;
    }

    public enum Mode {
        NONE, WINDOW, SUMMARY
    }
}
