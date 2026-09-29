package interview.guide.modules.resume.listener;

import interview.guide.common.async.AbstractStreamConsumer;
import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.log.ErrorLogSanitizer;
import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.interview.model.ResumeAnalysisResponse;
import interview.guide.modules.resume.model.ResumeEntity;
import interview.guide.modules.resume.repository.ResumeRepository;
import interview.guide.modules.resume.service.ResumeGradingService;
import interview.guide.modules.resume.service.ResumeParseService;
import interview.guide.modules.resume.service.ResumePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 简历分析 Stream 消费者
 * 负责从 Redis Stream 消费消息并执行 AI 分析
 */
@Slf4j
@Component
public class AnalyzeStreamConsumer extends AbstractStreamConsumer<AnalyzeStreamConsumer.AnalyzePayload> {

    private final ResumeGradingService gradingService;
    private final ResumePersistenceService persistenceService;
    private final ResumeRepository resumeRepository;
    private final ResumeParseService parseService;

    public AnalyzeStreamConsumer(
        RedisService redisService,
        ResumeGradingService gradingService,
        ResumePersistenceService persistenceService,
        ResumeRepository resumeRepository,
        ResumeParseService parseService
    ) {
        super(redisService);
        this.gradingService = gradingService;
        this.persistenceService = persistenceService;
        this.resumeRepository = resumeRepository;
        this.parseService = parseService;
    }

    static final class AnalyzePayload {

        private final Long resumeId;
        private String attemptId;

        AnalyzePayload(Long resumeId) {
            this.resumeId = resumeId;
        }

        Long resumeId() {
            return resumeId;
        }

        String attemptId() {
            return attemptId;
        }

        void setAttemptId(String attemptId) {
            this.attemptId = attemptId;
        }
    }

    @Override
    protected String taskDisplayName() {
        return "简历分析";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.RESUME_ANALYZE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "analyze-consumer";
    }

    @Override
    protected AnalyzePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        String resumeIdStr = data.get(AsyncTaskStreamConstants.FIELD_RESUME_ID);
        if (resumeIdStr == null) {
            log.warn("消息格式错误，跳过: messageId={}", messageId);
            return null;
        }
        // 兼容旧格式消息：其中的 content 字段直接忽略，正文以数据库实体为事实来源
        return new AnalyzePayload(Long.parseLong(resumeIdStr));
    }

    @Override
    protected String payloadIdentifier(AnalyzePayload payload) {
        return "resumeId=" + payload.resumeId();
    }

    @Override
    protected boolean shouldSkip(AnalyzePayload payload) {
        return resumeRepository.findById(payload.resumeId())
            .map(resume -> resume.getAnalyzeStatus() == AsyncTaskStatus.COMPLETED)
            .orElse(true);
    }

    /**
     * 条件领取模式下 markProcessing 不再使用。
     */
    @Override
    protected void markProcessing(AnalyzePayload payload) {
        // tryMarkProcessing 原子领取
    }

    /**
     * 条件领取：只有 PENDING → PROCESSING 成功的消费者才执行任务（多实例防重复）。
     */
    @Override
    protected boolean tryMarkProcessing(AnalyzePayload payload) {
        String attemptId = UUID.randomUUID().toString();
        boolean claimed = resumeRepository.tryMarkAnalyzeProcessing(
            payload.resumeId(), attemptId, java.time.LocalDateTime.now()) == 1;
        if (claimed) {
            payload.setAttemptId(attemptId);
        }
        return claimed;
    }

    @Override
    protected void processBusiness(AnalyzePayload payload) {
        Long resumeId = payload.resumeId();
        ResumeEntity resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
            return;
        }

        String resumeText = resume.getResumeText();
        if (isBlank(resumeText)) {
            // 历史数据正文为空时，从 RustFS 恢复文本并以独立短事务回填后再分析
            resumeText = parseService.downloadAndParseContent(
                resume.getStorageKey(), resume.getOriginalFilename());
            // 下载 + 解析完成心跳
            heartbeat(payload);
            if (isBlank(resumeText)) {
                throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法获取简历文本内容");
            }
            persistenceService.updateResumeText(resumeId, resumeText);
        }

        ResumeAnalysisResponse analysis = gradingService.analyzeResume(resumeText, resume.getJobDirection());
        // LLM 分析完成心跳（单次外部调用，无循环；分析超时必须小于 PROCESSING 阈值）
        heartbeat(payload);
        ResumeEntity latestResume = resumeRepository.findById(resumeId).orElse(null);
        if (latestResume == null) {
            log.warn("简历在分析期间被删除，跳过保存结果: resumeId={}", resumeId);
            return;
        }
        persistenceService.saveAnalysisIfOwned(resumeId, payload.attemptId(), analysis);
    }

    private void heartbeat(AnalyzePayload payload) {
        int updated = resumeRepository.heartbeatAnalyzeProcessing(
            payload.resumeId(), payload.attemptId(), java.time.LocalDateTime.now());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "简历分析任务执行权已失效");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    protected void markCompleted(AnalyzePayload payload) {
        // 条件写入：状态已不是 PROCESSING 时 no-op，防止覆盖他人结果
        resumeRepository.completeAnalyzeIfProcessing(
            payload.resumeId(), payload.attemptId(), java.time.LocalDateTime.now());
    }

    @Override
    protected void markFailed(AnalyzePayload payload, String error) {
        resumeRepository.failAnalyzeIfProcessing(
            payload.resumeId(), payload.attemptId(), truncateError(error),
            java.time.LocalDateTime.now());
    }

    @Override
    protected void retryMessage(AnalyzePayload payload, int retryCount) {
        Long resumeId = payload.resumeId();
        // 条件领取的配套重置：PROCESSING → PENDING 成功才重新投递，否则只记录并 ACK 丢弃
        int reset = resumeRepository.resetAnalyzeToPending(
            resumeId, payload.attemptId(), java.time.LocalDateTime.now());
        if (reset != 1) {
            log.warn("重试重置失败（任务已完成或状态不再匹配），ACK 丢弃: resumeId={}, retryCount={}", resumeId, retryCount);
            return;
        }
        try {
            Map<String, String> message = Map.of(
                AsyncTaskStreamConstants.FIELD_RESUME_ID, resumeId.toString(),
                AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
            );

            redisService().streamAdd(
                AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                message,
                AsyncTaskStreamConstants.STREAM_MAX_LEN
            );
            log.info("简历分析任务已重新入队: resumeId={}, retryCount={}", resumeId, retryCount);

        } catch (Exception e) {
            log.error("重试入队失败: resumeId={}, error={}", resumeId,
                ErrorLogSanitizer.summarize(e), ErrorLogSanitizer.forLogging(e));
            // 重投失败标 FAILED，避免停在 PENDING 被恢复调度器反复补投
            resumeRepository.failAnalyzeIfPending(
                resumeId, "重试入队失败", java.time.LocalDateTime.now());
        }
    }


}
