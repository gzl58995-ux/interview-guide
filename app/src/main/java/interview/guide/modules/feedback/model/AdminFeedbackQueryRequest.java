package interview.guide.modules.feedback.model;

import interview.guide.common.constant.CommonConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 管理员反馈分页查询表单
 * status / category 为空时不参与过滤
 */
public record AdminFeedbackQueryRequest(
    FeedbackStatus status,

    FeedbackCategory category,

    @Min(value = 1, message = "页码必须大于 0")
    Integer page,

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = CommonConstants.Pagination.MAX_SIZE, message = "每页条数不能超过 "
        + CommonConstants.Pagination.MAX_SIZE)
    Integer size
) {

    public AdminFeedbackQueryRequest {
        page = page == null ? CommonConstants.Pagination.DEFAULT_PAGE : page;
        size = size == null ? CommonConstants.Pagination.DEFAULT_SIZE : size;
    }
}
