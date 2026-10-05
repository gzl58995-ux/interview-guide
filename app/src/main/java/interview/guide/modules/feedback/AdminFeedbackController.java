package interview.guide.modules.feedback;

import interview.guide.common.result.PageResult;
import interview.guide.common.result.Result;
import interview.guide.modules.feedback.model.AdminFeedbackQueryRequest;
import interview.guide.modules.feedback.model.FeedbackDTO;
import interview.guide.modules.feedback.model.FeedbackStatus;
import interview.guide.modules.feedback.service.AdminFeedbackService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员反馈控制器
 * 平台级反馈管理，访问路径由 AdminInterceptor 统一校验管理员身份
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
@Tag(name = "管理员", description = "平台级数据管理（仅管理员）")
public class AdminFeedbackController {

    private final AdminFeedbackService adminFeedbackService;

    /**
     * 分页查询用户反馈
     */
    @GetMapping
    public Result<PageResult<FeedbackDTO>> query(@Valid @ModelAttribute AdminFeedbackQueryRequest query) {
        return Result.success(adminFeedbackService.query(query));
    }

    /**
     * 更新反馈处理状态
     */
    @PatchMapping("/{id}/status")
    public Result<FeedbackDTO> updateStatus(@PathVariable Long id, @RequestParam FeedbackStatus status) {
        return Result.success(adminFeedbackService.updateStatus(id, status));
    }

    /**
     * 删除反馈
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminFeedbackService.delete(id);
        return Result.success(null);
    }
}
