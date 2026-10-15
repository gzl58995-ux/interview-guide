package interview.guide.common.ai;

import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * LLM 调用错误分类器。
 * <p>
 * 与 {@code GlobalExceptionHandler} 同口径，沿 cause 链按序匹配：
 * 超时 → 限流 → 客户端错误 → 服务端错误 → 未知。
 * <p>
 * 隐私红线：{@link LlmErrorInfo#detail()} 只包含异常类名链，
 * 绝不包含任何异常 message（message 可能携带用户 prompt 等敏感内容）。
 */
public final class LlmErrorClassifier {

  private static final String[] RATE_LIMIT_MARKERS = {"429", "too many requests"};
  private static final String[] CLIENT_ERROR_MARKERS = {"401", "unauthorized", "403"};
  private static final String[] SERVER_ERROR_MARKERS = {"500", "502", "503", "504"};

  private LlmErrorClassifier() {
  }

  /**
   * 分类异常并生成安全的错误详情。
   *
   * @param error 待分类异常，可为 null
   * @param maxDetailLength detail 最大长度，超长截断
   * @return 错误类型 + 仅含异常类名链的详情；error 为 null 时返回 (UNKNOWN, "unknown")
   */
  public static LlmErrorInfo classify(Throwable error, int maxDetailLength) {
    if (error == null) {
      return new LlmErrorInfo(LlmCallErrorType.UNKNOWN, "unknown");
    }
    LlmCallErrorType errorType = doClassify(error);
    String detail = truncate(buildDetail(error), maxDetailLength);
    return new LlmErrorInfo(errorType, detail);
  }

  private static LlmCallErrorType doClassify(Throwable error) {
    if (hasTimeoutInChain(error)) {
      return LlmCallErrorType.TIMEOUT;
    }
    if (chainMessageContains(error, RATE_LIMIT_MARKERS)) {
      return LlmCallErrorType.RATE_LIMIT;
    }
    if (chainMessageContains(error, CLIENT_ERROR_MARKERS)) {
      return LlmCallErrorType.CLIENT_ERROR;
    }
    if (chainMessageContains(error, SERVER_ERROR_MARKERS)) {
      return LlmCallErrorType.SERVER_ERROR;
    }
    return LlmCallErrorType.UNKNOWN;
  }

  private static boolean hasTimeoutInChain(Throwable error) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (current instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }

  private static boolean chainMessageContains(Throwable error, String[] markers) {
    for (Throwable current = error; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message == null) {
        continue;
      }
      String lower = message.toLowerCase(Locale.ROOT);
      for (String marker : markers) {
        if (lower.contains(marker)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String buildDetail(Throwable error) {
    StringBuilder detail = new StringBuilder();
    for (Throwable current = error; current != null; current = current.getCause()) {
      if (detail.length() > 0) {
        detail.append(" <- ");
      }
      detail.append(current.getClass().getSimpleName());
    }
    return detail.toString();
  }

  private static String truncate(String detail, int maxDetailLength) {
    if (maxDetailLength < 0 || detail.length() <= maxDetailLength) {
      return detail;
    }
    return detail.substring(0, maxDetailLength);
  }

  /**
   * LLM 调用错误信息。
   *
   * @param errorType 错误类型
   * @param detail 仅含异常类名链的详情（不含任何异常 message）
   */
  public record LlmErrorInfo(LlmCallErrorType errorType, String detail) {
  }
}
