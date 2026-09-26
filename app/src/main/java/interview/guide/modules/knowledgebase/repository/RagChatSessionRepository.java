package interview.guide.modules.knowledgebase.repository;

import interview.guide.modules.knowledgebase.model.RagChatSessionEntity;
import interview.guide.modules.knowledgebase.model.RagChatSessionEntity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 
 * RAG聊天会话Repository
 */
@Repository
public interface RagChatSessionRepository extends JpaRepository<RagChatSessionEntity, Long> {

    /**
     * 根据ID查找当前用户的会话
     */
    Optional<RagChatSessionEntity> findByIdAndUserId(Long id, Long userId);

    /**
     * 按更新时间倒序获取当前用户的活跃会话
     */
    List<RagChatSessionEntity> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, SessionStatus status);

    /**
     * 获取当前用户的所有会话（按更新时间倒序）
     */
    List<RagChatSessionEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 获取当前用户的所有会话（按置顶状态和更新时间排序：置顶的在前，然后按更新时间倒序）
     */
    @Query("SELECT s FROM RagChatSessionEntity s WHERE s.userId = :userId ORDER BY s.isPinned DESC, s.updatedAt DESC")
    List<RagChatSessionEntity> findByUserIdOrderByPinnedAndUpdatedAtDesc(@Param("userId") Long userId);

    /**
     * 根据知识库ID查找相关会话（内部链路使用）
     */
    @Query("SELECT DISTINCT s FROM RagChatSessionEntity s JOIN s.knowledgeBases kb WHERE kb.id IN :kbIds ORDER BY s.updatedAt DESC")
    List<RagChatSessionEntity> findByKnowledgeBaseIds(@Param("kbIds") List<Long> knowledgeBaseIds);

    /**
     * 根据知识库ID查找当前用户的相关会话
     */
    @Query("SELECT DISTINCT s FROM RagChatSessionEntity s JOIN s.knowledgeBases kb WHERE s.userId = :userId AND kb.id IN :kbIds ORDER BY s.updatedAt DESC")
    List<RagChatSessionEntity> findByUserIdAndKnowledgeBaseIds(@Param("userId") Long userId,
                                                               @Param("kbIds") List<Long> knowledgeBaseIds);

    /**
     * 获取会话详情（带消息列表和知识库）
     * 注意：使用 DISTINCT 避免笛卡尔积导致的重复数据
     */
    @Query("SELECT DISTINCT s FROM RagChatSessionEntity s LEFT JOIN FETCH s.knowledgeBases WHERE s.id = :id")
    Optional<RagChatSessionEntity> findByIdWithMessagesAndKnowledgeBases(@Param("id") Long id);

    /**
     * 获取当前用户的会话详情（带知识库，不带消息）
     */
    @Query("SELECT s FROM RagChatSessionEntity s LEFT JOIN FETCH s.knowledgeBases WHERE s.id = :id AND s.userId = :userId")
    Optional<RagChatSessionEntity> findByIdWithKnowledgeBasesAndUserId(@Param("id") Long id,
                                                                       @Param("userId") Long userId);

    /**
     * 获取会话（带知识库，不带消息）
     */
    @Query("SELECT s FROM RagChatSessionEntity s LEFT JOIN FETCH s.knowledgeBases WHERE s.id = :id")
    Optional<RagChatSessionEntity> findByIdWithKnowledgeBases(@Param("id") Long id);
}
