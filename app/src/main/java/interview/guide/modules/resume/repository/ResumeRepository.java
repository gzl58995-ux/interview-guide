package interview.guide.modules.resume.repository;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;

/**
 * 简历Repository
 */
@Repository
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResumeEntity r WHERE r.id = :id")
    Optional<ResumeEntity> findByIdForUpdate(@Param("id") Long id);
    
    /**
     * 根据文件哈希查找当前用户的简历（用于去重）
     */
    Optional<ResumeEntity> findByUserIdAndFileHash(Long userId, String fileHash);

    /**
     * 查询当前用户的简历列表
     */
    List<ResumeEntity> findByUserIdOrderByUploadedAtDesc(Long userId);

    /**
     * 查询全平台简历列表（仅限管理员链路使用）
     */
    List<ResumeEntity> findAllByOrderByUploadedAtDesc();

    /**
     * 管理员分页搜索简历（仅限管理员链路使用）
     * keyword 模糊匹配简历文件名或归属用户名，其余条件为空时不参与过滤。
     * keyword 必须显式 CAST：Hibernate 7 在参数为 null 时会将参数按 bytea 绑定，
     * PostgreSQL 报 "function lower(bytea) does not exist"。
     */
    @Query(value = "SELECT r FROM ResumeEntity r WHERE "
        + "(:keyword IS NULL OR LOWER(r.originalFilename) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
        + "OR EXISTS (SELECT u.id FROM UserEntity u WHERE u.id = r.userId "
        + "AND LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))) "
        + "AND (:analyzeStatus IS NULL OR r.analyzeStatus = :analyzeStatus) "
        + "AND (:userId IS NULL OR r.userId = :userId)",
        countQuery = "SELECT COUNT(r) FROM ResumeEntity r WHERE "
            + "(:keyword IS NULL OR LOWER(r.originalFilename) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) "
            + "OR EXISTS (SELECT u.id FROM UserEntity u WHERE u.id = r.userId "
            + "AND LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))) "
            + "AND (:analyzeStatus IS NULL OR r.analyzeStatus = :analyzeStatus) "
            + "AND (:userId IS NULL OR r.userId = :userId)")
    Page<ResumeEntity> searchForAdmin(@Param("keyword") String keyword,
                                      @Param("analyzeStatus") AsyncTaskStatus analyzeStatus,
                                      @Param("userId") Long userId,
                                      Pageable pageable);

    /**
     * 统计全平台拥有简历的用户数（仅限管理员链路使用）
     */
    @Query("SELECT COUNT(DISTINCT r.userId) FROM ResumeEntity r")
    long countDistinctUsers();

    /**
     * 按归属用户分组统计简历数（仅限管理员链路使用）
     * 返回 [用户ID, 简历数]，按简历数倒序
     */
    @Query("SELECT r.userId, COUNT(r) FROM ResumeEntity r "
        + "GROUP BY r.userId ORDER BY COUNT(r) DESC")
    List<Object[]> countGroupByUserId();

    /**
     * 按分析状态统计简历数（仅限管理员链路使用）
     */
    long countByAnalyzeStatusIn(List<AsyncTaskStatus> statuses);

    // ========== P1-07 条件状态更新（终态不被覆盖，多实例安全） ==========

    /** PENDING → PROCESSING 条件领取，返回是否领取成功 */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = 'PROCESSING', "
        + "r.analyzeAttemptId = :attemptId, r.analyzeUpdatedAt = :now "
        + "WHERE r.id = :id AND r.analyzeStatus = 'PENDING'")
    int tryMarkAnalyzeProcessing(@Param("id") Long id, @Param("attemptId") String attemptId,
                                 @Param("now") LocalDateTime now);

    /** 心跳：仍为 PROCESSING 才推进进展时间 */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeUpdatedAt = :now "
        + "WHERE r.id = :id AND r.analyzeStatus = 'PROCESSING' "
        + "AND r.analyzeAttemptId = :attemptId")
    int heartbeatAnalyzeProcessing(@Param("id") Long id, @Param("attemptId") String attemptId,
                                   @Param("now") LocalDateTime now);

    /** PROCESSING → COMPLETED（非 PROCESSING 时 no-op） */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = 'COMPLETED', r.analyzeError = null, "
        + "r.analyzeAttemptId = null, r.analyzeUpdatedAt = :now "
        + "WHERE r.id = :id AND r.analyzeStatus = 'PROCESSING' "
        + "AND r.analyzeAttemptId = :attemptId")
    int completeAnalyzeIfProcessing(@Param("id") Long id, @Param("attemptId") String attemptId,
                                    @Param("now") LocalDateTime now);

    /** 当前执行代次 PROCESSING → FAILED。 */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = 'FAILED', r.analyzeError = :error, "
        + "r.analyzeAttemptId = null, r.analyzeUpdatedAt = :now "
        + "WHERE r.id = :id AND r.analyzeStatus = 'PROCESSING' "
        + "AND r.analyzeAttemptId = :attemptId")
    int failAnalyzeIfProcessing(@Param("id") Long id, @Param("attemptId") String attemptId,
                                @Param("error") String error, @Param("now") LocalDateTime now);

    /** 尚未领取的 PENDING → FAILED，用于生产者或恢复补投失败。 */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = 'FAILED', r.analyzeError = :error, "
        + "r.analyzeAttemptId = null, r.analyzeUpdatedAt = :now "
        + "WHERE r.id = :id AND r.analyzeStatus = 'PENDING'")
    int failAnalyzeIfPending(@Param("id") Long id, @Param("error") String error,
                             @Param("now") LocalDateTime now);

    /** PROCESSING → PENDING（重试重置） */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = 'PENDING', r.analyzeAttemptId = null, "
        + "r.analyzeUpdatedAt = :now WHERE r.id = :id AND r.analyzeStatus = 'PROCESSING' "
        + "AND r.analyzeAttemptId = :attemptId")
    int resetAnalyzeToPending(@Param("id") Long id, @Param("attemptId") String attemptId,
                              @Param("now") LocalDateTime now);

    /** 恢复补投前原子推进时间（PENDING 且早于阈值） */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeUpdatedAt = :now WHERE r.id = :id AND r.analyzeStatus = 'PENDING' AND r.analyzeUpdatedAt < :threshold")
    int touchQueuedAnalyzeForRecovery(@Param("id") Long id, @Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now);

    /** PROCESSING 无进展超阈值 → PENDING */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeStatus = 'PENDING', r.analyzeAttemptId = null, "
        + "r.analyzeUpdatedAt = :now WHERE r.id = :id AND r.analyzeStatus = 'PROCESSING' "
        + "AND r.analyzeUpdatedAt < :threshold")
    int resetStaleAnalyzeProcessing(@Param("id") Long id, @Param("threshold") LocalDateTime threshold, @Param("now") LocalDateTime now);

    /** 恢复计数 +1 */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeRecoveryCount = r.analyzeRecoveryCount + 1 WHERE r.id = :id")
    int incrementAnalyzeRecoveryCount(@Param("id") Long id);

    /** 手动重试清零恢复计数 */
    @Transactional
    @Modifying
    @Query("UPDATE ResumeEntity r SET r.analyzeRecoveryCount = 0 WHERE r.id = :id")
    int resetAnalyzeRecoveryCount(@Param("id") Long id);

    /** 卡住任务扫描（带每轮上限） */
    @Query("SELECT r FROM ResumeEntity r WHERE r.analyzeStatus = :status AND r.analyzeUpdatedAt < :threshold ORDER BY r.analyzeUpdatedAt ASC")
    List<ResumeEntity> findStaleByAnalyzeStatus(@Param("status") interview.guide.common.model.AsyncTaskStatus status,
                                                @Param("threshold") LocalDateTime threshold,
                                                Pageable pageable);
}
