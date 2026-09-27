package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeAnalysisEntity;
import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 简历评测Repository
 */
@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {
    
    /**
     * 根据简历查找所有评测记录
     */
    List<ResumeAnalysisEntity> findByResumeOrderByAnalyzedAtDesc(ResumeEntity resume);
    
    /**
     * 根据简历ID查找最新评测记录
     */
    ResumeAnalysisEntity findFirstByResumeIdOrderByAnalyzedAtDesc(Long resumeId);
    
    /**
     * 根据简历ID查找所有评测记录
     */
    List<ResumeAnalysisEntity> findByResumeIdOrderByAnalyzedAtDesc(Long resumeId);

    /**
     * 批量查询多份简历的最新评测分数（仅限管理员链路使用）
     * 返回 [简历ID, 总分, 分析时间]，按分析时间倒序
     */
    @Query("SELECT a.resume.id, a.overallScore, a.analyzedAt FROM ResumeAnalysisEntity a "
        + "WHERE a.resume.id IN :resumeIds "
        + "AND a.analyzedAt = (SELECT MAX(a2.analyzedAt) FROM ResumeAnalysisEntity a2 "
        + "WHERE a2.resume.id = a.resume.id) "
        + "ORDER BY a.analyzedAt DESC")
    List<Object[]> findLatestScoresByResumeIds(@Param("resumeIds") List<Long> resumeIds);
}
