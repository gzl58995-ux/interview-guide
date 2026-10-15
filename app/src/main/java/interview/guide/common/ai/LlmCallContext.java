package interview.guide.common.ai;

import java.util.function.Supplier;

/**
 * LLM 调用场景的线程上下文。
 * <p>
 * 基于 {@link ThreadLocal} 保存当前线程的调用场景，供 LLM 监控 Advisor 读取。
 * 通过 {@link #withScene(LlmCallScene, Runnable)} / {@link #withScene(LlmCallScene, Supplier)}
 * 进入场景，退出（含异常）时自动恢复旧值。
 */
public final class LlmCallContext {

  private static final ThreadLocal<LlmCallScene> HOLDER = new ThreadLocal<>();

  private LlmCallContext() {
  }

  /**
   * 当前线程的调用场景，未设置时返回 {@link LlmCallScene#UNKNOWN}。
   */
  public static LlmCallScene current() {
    LlmCallScene scene = HOLDER.get();
    return scene != null ? scene : LlmCallScene.UNKNOWN;
  }

  /**
   * 在指定场景下执行动作，退出（含异常）时恢复之前的场景。
   */
  public static void withScene(LlmCallScene scene, Runnable action) {
    LlmCallScene previous = HOLDER.get();
    HOLDER.set(scene);
    try {
      action.run();
    } finally {
      restore(previous);
    }
  }

  /**
   * 在指定场景下执行动作并返回结果，退出（含异常）时恢复之前的场景。
   */
  public static <T> T withScene(LlmCallScene scene, Supplier<T> action) {
    LlmCallScene previous = HOLDER.get();
    HOLDER.set(scene);
    try {
      return action.get();
    } finally {
      restore(previous);
    }
  }

  private static void restore(LlmCallScene previous) {
    if (previous == null) {
      HOLDER.remove();
    } else {
      HOLDER.set(previous);
    }
  }
}
