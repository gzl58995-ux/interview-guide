package interview.guide.infrastructure.mapper;

import interview.guide.modules.feedback.model.FeedbackDTO;
import interview.guide.modules.feedback.model.FeedbackEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 反馈相关的对象映射器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FeedbackMapper {

    FeedbackDTO toDTO(FeedbackEntity entity);

    List<FeedbackDTO> toDTOs(List<FeedbackEntity> entities);
}
