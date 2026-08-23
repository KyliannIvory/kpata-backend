package ci.kpata.backend.profile.internal.mapper;

import ci.kpata.backend.profile.internal.domain.ProfessionalProfile;
import ci.kpata.backend.profile.internal.dto.ProfessionalProfileDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfessionalProfileMapper {

    ProfessionalProfileDto toDto(ProfessionalProfile profile);
}
