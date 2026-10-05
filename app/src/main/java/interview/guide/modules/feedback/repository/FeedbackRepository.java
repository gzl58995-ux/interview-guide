package interview.guide.modules.feedback.repository;

import interview.guide.modules.feedback.model.FeedbackCategory;
import interview.guide.modules.feedback.model.FeedbackEntity;
import interview.guide.modules.feedback.model.FeedbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 用户反馈仓库
 */
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {

    /**
     * 管理员分页查询反馈（仅限管理员链路使用）
     * status / category 为空时不参与过滤，固定按创建时间倒序
     */
    @Query("SELECT f FROM FeedbackEntity f WHERE "
        + "(:status IS NULL OR f.status = :status) "
        + "AND (:category IS NULL OR f.category = :category) "
        + "ORDER BY f.createdAt DESC")
    Page<FeedbackEntity> searchForAdmin(@Param("status") FeedbackStatus status,
                                        @Param("category") FeedbackCategory category,
                                        Pageable pageable);
}
