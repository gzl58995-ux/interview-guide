package interview.guide.common.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmCallContextTest {

  @Test
  @DisplayName("未设置场景时 current 返回 UNKNOWN")
  void currentDefaultsToUnknown() {
    assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.UNKNOWN);
  }

  @Test
  @DisplayName("withScene 期间 current 为指定场景，退出后恢复旧值（含嵌套）")
  void withSceneSetsAndRestores() {
    LlmCallContext.withScene(LlmCallScene.RAG_CHAT, () -> {
      assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.RAG_CHAT);
      LlmCallContext.withScene(LlmCallScene.RAG_REWRITE, () -> {
        assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.RAG_REWRITE);
      });
      assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.RAG_CHAT);
    });
    assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.UNKNOWN);
  }

  @Test
  @DisplayName("withScene 内抛异常也会恢复旧值")
  void withSceneRestoresOnException() {
    assertThatThrownBy(() -> LlmCallContext.withScene(LlmCallScene.RAG_CHAT, () -> {
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.UNKNOWN);
  }

  @Test
  @DisplayName("withScene(Supplier) 返回业务值")
  void withSceneSupplierReturnsValue() {
    String result = LlmCallContext.withScene(LlmCallScene.KB_QUESTION_GEN, () -> {
      assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.KB_QUESTION_GEN);
      return "generated";
    });

    assertThat(result).isEqualTo("generated");
    assertThat(LlmCallContext.current()).isEqualTo(LlmCallScene.UNKNOWN);
  }
}
