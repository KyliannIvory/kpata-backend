package ci.kpata.backend.auth.internal.mapper;

import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.dto.UserDto;
import org.mapstruct.Mapper;

/**
 * {@code User} → {@link UserDto} only — deliberately one-way. A {@code toEntity(UserDto)}
 * would let any field the client sends (e.g. {@code roles}) flow straight into a persisted
 * {@code User} the next time someone calls {@code repository.save(...)} on it; see {@link
 * UserDto}'s Javadoc.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}
