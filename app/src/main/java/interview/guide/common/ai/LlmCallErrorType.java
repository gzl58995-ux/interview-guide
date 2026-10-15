package interview.guide.common.ai;

/**
 * LLM 调用错误类型枚举。
 * <p>
 * 用于 LLM 监控指标按错误类别聚合，由 {@link LlmErrorClassifier} 从异常分类得出。
 */
public enum LlmCallErrorType {

  /** 超时（cause 链中存在 SocketTimeoutException） */
  TIMEOUT,

  /** 限流（HTTP 429 / too many requests） */
  RATE_LIMIT,

  /** 客户端错误（HTTP 401 / 403 / unauthorized 等鉴权或请求错误） */
  CLIENT_ERROR,

  /** 服务端错误（HTTP 500 / 502 / 503 / 504） */
  SERVER_ERROR,

  /** 无法归类的其它错误 */
  UNKNOWN
}
