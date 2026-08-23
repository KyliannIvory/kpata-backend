package ci.kpata.backend.profile.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ci.kpata.backend.profile.internal.domain.ProfessionalProfile;
import ci.kpata.backend.profile.internal.domain.SalonRole;
import ci.kpata.backend.profile.internal.dto.ProfessionalProfileDto;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProfessionalProfileMapperTest {

    private final ProfessionalProfileMapper mapper = new ProfessionalProfileMapperImpl();

    @Test
    void toDto_mapsEveryField() {

        UUID salonId = UUID.randomUUID();
        ProfessionalProfile profile = ProfessionalProfile
            .builder()
            .userId(UUID.randomUUID())
            .salonId(salonId)
            .description("Hair stylist, five years of experience")
            .salonRole(SalonRole.OWNER)
            .build();

        ProfessionalProfileDto dto = mapper.toDto(profile);

        assertThat(dto.salonId()).isEqualTo(salonId);
        assertThat(dto.description()).isEqualTo("Hair stylist, five years of experience");
        assertThat(dto.salonRole()).isEqualTo(SalonRole.OWNER);
    }

    @Test
    void toDto_withNullProfile_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
