package interview.guide.modules.admin.model;

import org.springframework.data.domain.Sort;

/**
 * 管理员简历列表排序方式
 * 只暴露白名单枚举，避免外部传入任意排序字段
 */
public enum AdminResumeSort {

    UPLOADED_AT_DESC(Sort.by(Sort.Direction.DESC, "uploadedAt")),
    UPLOADED_AT_ASC(Sort.by(Sort.Direction.ASC, "uploadedAt")),
    ACCESS_COUNT_DESC(Sort.by(Sort.Direction.DESC, "accessCount")),
    FILE_SIZE_DESC(Sort.by(Sort.Direction.DESC, "fileSize"));

    private final Sort sort;

    AdminResumeSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return sort;
    }
}
