package interview.guide.common.ai;

/**
 * LLM 调用场景枚举。
 * <p>
 * 用于 LLM 监控指标按业务场景维度聚合，通过 {@link LlmCallContext} 在调用点打标。
 */
public enum LlmCallScene {

  /** 面试题目生成 */
  INTERVIEW_QUESTION_GEN,

  /** 面试回答评估 */
  INTERVIEW_EVALUATION,

  /** 面试 JD 解析 */
  INTERVIEW_JD_PARSE,

  /** 简历评分 */
  RESUME_GRADING,

  /** 知识库题目生成 */
  KB_QUESTION_GEN,

  /** RAG 知识库问答 */
  RAG_CHAT,

  /** RAG 查询重写 */
  RAG_REWRITE,

  /** 语音面试对话 */
  VOICE_CHAT,

  /** 语音面试上下文压缩 */
  VOICE_CONTEXT_COMPRESS,

  /** 语音面试评估 */
  VOICE_EVALUATION,

  /** 面试日程解析 */
  SCHEDULE_PARSE,

  /** 未知场景（未设置上下文时的兜底值） */
  UNKNOWN
}
