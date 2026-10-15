package interview.guide.common.ai;

import interview.guide.common.ai.LlmErrorClassifier.LlmErrorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class LlmErrorClassifierTest {

  @Nested
  @DisplayName("错误类型分类")
  class Classification {

    @Test
    @DisplayName("SocketTimeoutException 在 cause 链中分类为 TIMEOUT")
    void timeoutClassification() {
      var e = new ResourceAccessException("IO error", new SocketTimeoutException("Read timed out"));

      assertThat(LlmErrorClassifier.classify(e, 200).errorType()).isEqualTo(LlmCallErrorType.TIMEOUT);
    }

    @Test
    @DisplayName("message 含 429 或 too many requests（忽略大小写）分类为 RATE_LIMIT")
    void rateLimitClassification() {
      assertThat(LlmErrorClassifier.classify(new RuntimeException("HTTP 429"), 200).errorType())
          .isEqualTo(LlmCallErrorType.RATE_LIMIT);
      assertThat(LlmErrorClassifier.classify(new RuntimeException("Too Many Requests"), 200).errorType())
          .isEqualTo(LlmCallErrorType.RATE_LIMIT);
    }

    @Test
    @DisplayName("message 含 401/unauthorized/403（忽略大小写）分类为 CLIENT_ERROR")
    void clientErrorClassification() {
      assertThat(LlmErrorClassifier.classify(new RuntimeException("401"), 200).errorType())
          .isEqualTo(LlmCallErrorType.CLIENT_ERROR);
      assertThat(LlmErrorClassifier.classify(new RuntimeException("Unauthorized"), 200).errorType())
          .isEqualTo(LlmCallErrorType.CLIENT_ERROR);
      assertThat(LlmErrorClassifier.classify(new RuntimeException("403 Forbidden"), 200).errorType())
          .isEqualTo(LlmCallErrorType.CLIENT_ERROR);
    }

    @Test
    @DisplayName("message 含 500/502/503/504 分类为 SERVER_ERROR，cause 链内层 message 同样生效")
    void serverErrorClassification() {
      assertThat(LlmErrorClassifier.classify(new RuntimeException("503 Service Unavailable"), 200).errorType())
          .isEqualTo(LlmCallErrorType.SERVER_ERROR);
      var wrapped = new RuntimeException("call failed", new RuntimeException("502 Bad Gateway"));
      assertThat(LlmErrorClassifier.classify(wrapped, 200).errorType())
          .isEqualTo(LlmCallErrorType.SERVER_ERROR);
    }

    @Test
    @DisplayName("无匹配分类为 UNKNOWN")
    void unknownClassification() {
      assertThat(LlmErrorClassifier.classify(new RuntimeException("boom"), 200).errorType())
          .isEqualTo(LlmCallErrorType.UNKNOWN);
    }
  }

  @Nested
  @DisplayName("detail 详情")
  class Detail {

    @Test
    @DisplayName("detail 为异常类名链，以 <- 连接")
    void detailIsClassNameChain() {
      var e = new ResourceAccessException("IO error", new SocketTimeoutException("Read timed out"));

      assertThat(LlmErrorClassifier.classify(e, 200).detail())
          .isEqualTo("ResourceAccessException <- SocketTimeoutException");
    }

    @Test
    @DisplayName("detail 只含异常类名链，不含原始 message（隐私）")
    void detailContainsOnlyClassNames() {
      var e = new IllegalStateException("用户 prompt 敏感内容 429");

      LlmErrorInfo info = LlmErrorClassifier.classify(e, 200);

      assertThat(info.detail()).doesNotContain("敏感内容").contains("IllegalStateException");
    }

    @Test
    @DisplayName("detail 超长截断到 maxDetailLength")
    void detailTruncatedToMaxLength() {
      Throwable e = new RuntimeException(new IllegalStateException(
          new IllegalArgumentException(new SecurityException())));

      LlmErrorInfo info = LlmErrorClassifier.classify(e, 20);

      assertThat(info.detail().length()).isLessThanOrEqualTo(20);
    }
  }

  @Test
  @DisplayName("error 为 null 返回 UNKNOWN/unknown")
  void nullError() {
    LlmErrorInfo info = LlmErrorClassifier.classify(null, 200);

    assertThat(info.errorType()).isEqualTo(LlmCallErrorType.UNKNOWN);
    assertThat(info.detail()).isEqualTo("unknown");
  }
}
