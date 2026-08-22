package ci.kpata.backend.profile.internal.dto;

import ci.kpata.backend.profile.internal.domain.SalonRole;

import java.util.UUID;

public record ProfessionalProfileDto(

    UUID salonId,

    String description,

    SalonRole salonRole
) {
}
