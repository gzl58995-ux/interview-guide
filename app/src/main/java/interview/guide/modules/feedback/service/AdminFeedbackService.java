package interview.guide.modules.feedback.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.PageResult;
import interview.guide.infrastructure.mapper.FeedbackMapper;
import interview.guide.modules.feedback.model.AdminFeedbackQueryRequest;
import interview.guide.modules.feedback.model.FeedbackDTO;
import interview.guide.modules.feedback.model.FeedbackEntity;
import interview.guide.modules.feedback.model.FeedbackStatus;
import interview.guide.modules.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员反馈服务
 * 平台级查询，不做归属过滤，仅供管理员链路使用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackMapper feedbackMapper;

    /**
     * 按状态/分类筛选分页查询反馈
     */
    @Transactional(readOnly = true)
    public PageResult<FeedbackDTO> query(AdminFeedbackQueryRequest query) {
        Pageable pageable = PageRequest.of(query.page() - 1, query.size());
        Page<FeedbackEntity> page = feedbackRepository.searchForAdmin(
            query.status(), query.category(), pageable);

        return PageResult.of(
            feedbackMapper.toDTOs(page.getContent()),
            page.getTotalElements(),
            query.page(),
            query.size());
    }

    /**
     * 更新反馈处理状态
     * 切到已处理时写入处理时间，回退待处理时清空；重复标记同一状态不改动时间
     */
    @Transactional
    public FeedbackDTO updateStatus(Long id, FeedbackStatus status) {
        FeedbackEntity entity = feedbackRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND, "留言不存在"));

        if (entity.getStatus() != status) {
            entity.setStatus(status);
            entity.setHandledAt(status == FeedbackStatus.PROCESSED ? LocalDateTime.now() : null);
        }

        log.info("管理员更新反馈状态: id={}, status={}", id, status);
        return feedbackMapper.toDTO(entity);
    }

    /**
     * 删除反馈
     */
    @Transactional
    public void delete(Long id) {
        FeedbackEntity entity = feedbackRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.FEEDBACK_NOT_FOUND, "留言不存在"));

        feedbackRepository.delete(entity);
        log.info("管理员删除反馈: id={}", id);
    }
}
