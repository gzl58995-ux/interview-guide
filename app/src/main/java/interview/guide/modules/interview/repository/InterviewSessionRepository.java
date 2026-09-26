package interview.guide.modules.interview.repository;

import interview.guide.modules.interview.model.InterviewSessionEntity;
import interview.guide.modules.interview.model.InterviewSessionEntity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 面试会话Repository
 */
@Repository
public interface InterviewSessionRepository extends JpaRepository<InterviewSessionEntity, Long> {

    /**
     * 根据会话ID查找（不做归属校验，仅限异步任务等内部链路使用）
     */
    Optional<InterviewSessionEntity> findBySessionId(String sessionId);

    /**
     * 根据会话ID查找当前用户的会话
     */
    Optional<InterviewSessionEntity> findBySessionIdAndUserId(String sessionId, Long userId);

    /**
     * 根据幂等键查找当前用户的会话
     */
    Optional<InterviewSessionEntity> findByRequestIdAndUserId(String requestId, Long userId);

    /**
     * 根据会话ID查找（同时加载关联的简历）
     */
    @Query("SELECT s FROM InterviewSessionEntity s LEFT JOIN FETCH s.resume WHERE s.sessionId = :sessionId")
    Optional<InterviewSessionEntity> findBySessionIdWithResume(@Param("sessionId") String sessionId);
    
    /**
     * 根据简历ID查找所有面试记录（内部链路使用）
     */
    List<InterviewSessionEntity> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    /**
     * 根据用户和简历ID查找面试记录
     */
    List<InterviewSessionEntity> findByUserIdAndResumeIdOrderByCreatedAtDesc(Long userId, Long resumeId);

    /**
     * 根据用户和简历ID查找最近的面试记录（用于历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByUserIdAndResumeIdOrderByCreatedAtDesc(Long userId, Long resumeId);
    
    /**
     * 查找当前用户某简历的未完成面试（CREATED或IN_PROGRESS状态）
     */
    Optional<InterviewSessionEntity> findFirstByUserIdAndResumeIdAndStatusInOrderByCreatedAtDesc(
        Long userId,
        Long resumeId, 
        List<SessionStatus> statuses
    );

    /**
     * 查找当前用户的面试会话（按创建时间倒序）
     */
    List<InterviewSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 根据用户和 skillId 查找最近的面试记录（用于通用模式历史题去重）
     */
    List<InterviewSessionEntity> findTop10ByUserIdAndSkillIdOrderByCreatedAtDesc(Long userId, String skillId);

    /**
     * 根据用户 + resumeId + skillId 查找最近的面试记录（精确匹配）
     */
    List<InterviewSessionEntity> findTop10ByUserIdAndResumeIdAndSkillIdOrderByCreatedAtDesc(
        Long userId, Long resumeId, String skillId);
}
