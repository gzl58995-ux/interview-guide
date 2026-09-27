package interview.guide.common.result;

import java.util.List;

/**
 * 分页查询结果
 * 与 {@link Result} 配合作为对外响应体，page 从 1 开始
 */
public record PageResult<T>(
    List<T> items,
    long total,
    int page,
    int size,
    int totalPages
) {

    /**
     * 由当前页内容与总数构建分页结果
     */
    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResult<>(items, total, page, size, totalPages);
    }
}
