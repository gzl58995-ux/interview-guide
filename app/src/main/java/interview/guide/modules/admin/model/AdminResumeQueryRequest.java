package interview.guide.modules.admin.model;

import interview.guide.common.constant.CommonConstants;
import interview.guide.common.model.AsyncTaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 管理员简历分页查询表单
 * keyword 模糊匹配简历文件名或用户名，其余条件精确匹配
 */
public record AdminResumeQueryRequest(
    @Size(max = 100, message = "搜索关键词不能超过 100 个字符")
    String keyword,

    AsyncTaskStatus analyzeStatus,

    @Min(value = 1, message = "用户 ID 必须大于 0")
    Long userId,

    AdminResumeSort sort,

    @Min(value = 1, message = "页码必须大于 0")
    Integer page,

    @Min(value = 1, message = "每页条数必须大于 0")
    @Max(value = CommonConstants.Pagination.MAX_SIZE, message = "每页条数不能超过 "
        + CommonConstants.Pagination.MAX_SIZE)
    Integer size
) {

    public AdminResumeQueryRequest {
        keyword = normalizeKeyword(keyword);
        page = page == null ? CommonConstants.Pagination.DEFAULT_PAGE : page;
        size = size == null ? CommonConstants.Pagination.DEFAULT_SIZE : size;
        sort = sort == null ? AdminResumeSort.UPLOADED_AT_DESC : sort;
    }

    private static String normalizeKeyword(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
