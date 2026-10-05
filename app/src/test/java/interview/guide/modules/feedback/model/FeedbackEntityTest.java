package interview.guide.modules.feedback.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("反馈实体默认值")
class FeedbackEntityTest {

    @Test
    @DisplayName("新建反馈默认待处理且未标记处理时间")
    void defaultsToPending() {
        FeedbackEntity entity = new FeedbackEntity();

        assertThat(entity.getStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(entity.getHandledAt()).isNull();
    }

    @Test
    @DisplayName("分类可读写")
    void keepsCategory() {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setCategory(FeedbackCategory.SUGGESTION);

        assertThat(entity.getCategory()).isEqualTo(FeedbackCategory.SUGGESTION);
    }
}
