package interview.guide.modules.feedback.repository;

import interview.guide.modules.feedback.model.FeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户反馈仓库
 */
public interface FeedbackRepository extends JpaRepository<FeedbackEntity, Long> {
}
