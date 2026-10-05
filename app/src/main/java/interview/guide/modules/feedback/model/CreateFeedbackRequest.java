package interview.guide.modules.feedback.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交反馈请求
 */
public record CreateFeedbackRequest(
    @NotNull(message = "请选择反馈类型") FeedbackCategory category,

    @NotBlank(message = "反馈内容不能为空")
    @Size(min = 5, max = 1000, message = "反馈内容需为 5-1000 字")
    String content,

    @Size(max = 512, message = "页面路径不能超过 512 个字符")
    String pagePath
) {
}
