package interview.guide.infrastructure.mapper;

import interview.guide.modules.auth.model.UserDTO;
import interview.guide.modules.auth.model.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 用户相关的对象映射器
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserDTO toDTO(UserEntity entity);
}
