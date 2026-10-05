package interview.guide.modules.feedback.service;

import interview.guide.common.auth.AuthPrincipal;
import interview.guide.common.auth.UserContext;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.feedback.model.CreateFeedbackRequest;
import interview.guide.modules.feedback.model.FeedbackCategory;
import interview.guide.modules.feedback.model.FeedbackEntity;
import interview.guide.modules.feedback.model.FeedbackStatus;
import interview.guide.modules.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户反馈提交")
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    private FeedbackService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(feedbackRepository);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("提交时写入当前用户与待处理状态，正文去除首尾空白")
    void submitSavesCurrentUserAndPendingStatus() {
        UserContext.set(new AuthPrincipal(7L, "alice", false));
        ArgumentCaptor<FeedbackEntity> captor = ArgumentCaptor.forClass(FeedbackEntity.class);

        service.submit(new CreateFeedbackRequest(
            FeedbackCategory.SUGGESTION, "  页面加载太慢  ", "/history"));

        verify(feedbackRepository).save(captor.capture());
        FeedbackEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getCategory()).isEqualTo(FeedbackCategory.SUGGESTION);
        assertThat(saved.getContent()).isEqualTo("页面加载太慢");
        assertThat(saved.getPagePath()).isEqualTo("/history");
        assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(saved.getHandledAt()).isNull();
    }

    @Test
    @DisplayName("页面路径可以为空")
    void submitAllowsNullPagePath() {
        UserContext.set(new AuthPrincipal(1L, "bob", false));

        service.submit(new CreateFeedbackRequest(FeedbackCategory.OTHER, "随便写点什么", null));

        ArgumentCaptor<FeedbackEntity> captor = ArgumentCaptor.forClass(FeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getPagePath()).isNull();
    }

    @Test
    @DisplayName("未登录提交抛出未授权业务异常且不写库")
    void submitWithoutLoginThrows() {
        assertThatThrownBy(() -> service.submit(
            new CreateFeedbackRequest(FeedbackCategory.BUG, "页面打不开了", null)))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getCode())
            .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());

        verifyNoInteractions(feedbackRepository);
    }
}
