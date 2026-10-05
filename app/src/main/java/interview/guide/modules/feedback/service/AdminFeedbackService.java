package interview.guide.modules.feedback.service;

import interview.guide.common.result.PageResult;
import interview.guide.infrastructure.mapper.FeedbackMapper;
import interview.guide.modules.feedback.model.AdminFeedbackQueryRequest;
import interview.guide.modules.feedback.model.FeedbackDTO;
import interview.guide.modules.feedback.model.FeedbackEntity;
import interview.guide.modules.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
