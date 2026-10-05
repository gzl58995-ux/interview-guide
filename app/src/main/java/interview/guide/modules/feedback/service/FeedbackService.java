package interview.guide.modules.feedback.service;

import interview.guide.common.auth.UserContext;
import interview.guide.modules.feedback.model.CreateFeedbackRequest;
import interview.guide.modules.feedback.model.FeedbackEntity;
import interview.guide.modules.feedback.model.FeedbackStatus;
import interview.guide.modules.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户反馈服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    /**
     * 提交反馈，归属当前登录用户
     */
    @Transactional
    public void submit(CreateFeedbackRequest request) {
        Long userId = UserContext.requireUserId();
        String username = UserContext.get().username();

        FeedbackEntity entity = new FeedbackEntity();
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setCategory(request.category());
        entity.setContent(request.content().trim());
        entity.setPagePath(request.pagePath());
        entity.setStatus(FeedbackStatus.PENDING);

        feedbackRepository.save(entity);
        log.info("用户提交反馈: userId={}, category={}", userId, request.category());
    }
}
