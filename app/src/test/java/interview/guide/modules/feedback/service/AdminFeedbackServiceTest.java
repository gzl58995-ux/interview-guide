package interview.guide.modules.feedback.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.common.result.PageResult;
import interview.guide.infrastructure.mapper.FeedbackMapper;
import interview.guide.modules.feedback.model.AdminFeedbackQueryRequest;
import interview.guide.modules.feedback.model.FeedbackCategory;
import interview.guide.modules.feedback.model.FeedbackDTO;
import interview.guide.modules.feedback.model.FeedbackEntity;
import interview.guide.modules.feedback.model.FeedbackStatus;
import interview.guide.modules.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("管理员反馈查询")
class AdminFeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    private AdminFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AdminFeedbackService(feedbackRepository, Mappers.getMapper(FeedbackMapper.class));
    }

    @Test
    @DisplayName("按筛选条件分页查询并映射为 DTO")
    void queryMapsPageToDTOs() {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setId(1L);
        entity.setUserId(7L);
        entity.setUsername("alice");
        entity.setCategory(FeedbackCategory.SUGGESTION);
        entity.setContent("页面加载太慢");
        entity.setPagePath("/history");
        entity.setStatus(FeedbackStatus.PENDING);
        entity.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(feedbackRepository.searchForAdmin(
            FeedbackStatus.PENDING, FeedbackCategory.SUGGESTION, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        PageResult<FeedbackDTO> result = service.query(
            new AdminFeedbackQueryRequest(FeedbackStatus.PENDING, FeedbackCategory.SUGGESTION, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        FeedbackDTO dto = result.items().get(0);
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.category()).isEqualTo(FeedbackCategory.SUGGESTION);
        assertThat(dto.status()).isEqualTo(FeedbackStatus.PENDING);
    }

    @Test
    @DisplayName("查询表单为空时默认第 1 页每页 20 条且不筛选")
    void queryUsesDefaultsWhenUnset() {
        when(feedbackRepository.searchForAdmin(null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PageResult<FeedbackDTO> result = service.query(new AdminFeedbackQueryRequest(null, null, null, null));

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("标记已处理时写入处理时间")
    void markProcessedSetsHandledAt() {
        FeedbackEntity entity = pendingEntity();
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(entity));

        FeedbackDTO dto = service.updateStatus(1L, FeedbackStatus.PROCESSED);

        assertThat(entity.getStatus()).isEqualTo(FeedbackStatus.PROCESSED);
        assertThat(entity.getHandledAt()).isNotNull();
        assertThat(dto.status()).isEqualTo(FeedbackStatus.PROCESSED);
    }

    @Test
    @DisplayName("回退待处理时清空处理时间")
    void markPendingClearsHandledAt() {
        FeedbackEntity entity = pendingEntity();
        entity.setStatus(FeedbackStatus.PROCESSED);
        entity.setHandledAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.updateStatus(1L, FeedbackStatus.PENDING);

        assertThat(entity.getStatus()).isEqualTo(FeedbackStatus.PENDING);
        assertThat(entity.getHandledAt()).isNull();
    }

    @Test
    @DisplayName("重复标记同一状态不改动处理时间")
    void markingSameStatusKeepsHandledAt() {
        LocalDateTime handledAt = LocalDateTime.of(2026, 10, 2, 10, 0);
        FeedbackEntity entity = pendingEntity();
        entity.setStatus(FeedbackStatus.PROCESSED);
        entity.setHandledAt(handledAt);
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.updateStatus(1L, FeedbackStatus.PROCESSED);

        assertThat(entity.getHandledAt()).isEqualTo(handledAt);
    }

    @Test
    @DisplayName("更新不存在的留言抛出留言不存在业务异常")
    void updateMissingThrows() {
        when(feedbackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, FeedbackStatus.PROCESSED))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getCode())
            .isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("删除存在的留言调用仓库删除")
    void deleteExistingDelegates() {
        FeedbackEntity entity = pendingEntity();
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(feedbackRepository).delete(entity);
    }

    @Test
    @DisplayName("删除不存在的留言抛出留言不存在业务异常")
    void deleteMissingThrows() {
        when(feedbackRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getCode())
            .isEqualTo(ErrorCode.FEEDBACK_NOT_FOUND.getCode());
    }

    private FeedbackEntity pendingEntity() {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setId(1L);
        entity.setUserId(7L);
        entity.setUsername("alice");
        entity.setCategory(FeedbackCategory.BUG);
        entity.setContent("页面打不开了");
        entity.setStatus(FeedbackStatus.PENDING);
        return entity;
    }
}
