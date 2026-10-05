package interview.guide.modules.feedback;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.modules.feedback.model.CreateFeedbackRequest;
import interview.guide.modules.feedback.service.FeedbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Tag(name = "用户反馈", description = "登录用户提交反馈")
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * 提交反馈
     */
    @PostMapping
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 5, interval = 1,
        timeUnit = RateLimit.TimeUnit.MINUTES)
    public Result<Void> submit(@Valid @RequestBody CreateFeedbackRequest request) {
        feedbackService.submit(request);
        return Result.success(null);
    }
}
