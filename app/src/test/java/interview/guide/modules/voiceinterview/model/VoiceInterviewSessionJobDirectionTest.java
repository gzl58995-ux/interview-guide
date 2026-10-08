package interview.guide.modules.voiceinterview.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("语音面试会话求职方向持久化兜底")
class VoiceInterviewSessionJobDirectionTest {

  @Test
  @DisplayName("持久化前方向为 null 时回填默认 TECH，避免 NOT NULL 违规")
  void nullJobDirectionDefaultsOnPersist() {
    VoiceInterviewSessionEntity entity = VoiceInterviewSessionEntity.builder()
        .userId(1L)
        .roleType("java-backend")
        .jobDirection(null)
        .build();

    entity.onCreate();

    assertThat(entity.getJobDirection()).isEqualTo("TECH");
  }

  @Test
  @DisplayName("持久化前空白方向同样回填默认 TECH")
  void blankJobDirectionDefaultsOnUpdate() {
    VoiceInterviewSessionEntity entity = VoiceInterviewSessionEntity.builder()
        .userId(1L)
        .roleType("java-backend")
        .jobDirection("  ")
        .build();

    entity.onUpdate();

    assertThat(entity.getJobDirection()).isEqualTo("TECH");
  }
}
