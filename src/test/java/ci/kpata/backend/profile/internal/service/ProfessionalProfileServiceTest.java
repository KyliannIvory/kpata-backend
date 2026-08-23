package ci.kpata.backend.profile.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ci.kpata.backend.profile.internal.domain.ProfessionalProfile;
import ci.kpata.backend.profile.internal.domain.SalonRole;
import ci.kpata.backend.profile.internal.dto.ProfessionalProfileDto;
import ci.kpata.backend.profile.internal.exception.ProfessionalProfileNotFoundException;
import ci.kpata.backend.profile.internal.mapper.ProfessionalProfileMapper;
import ci.kpata.backend.profile.internal.repository.ProfessionalProfileRepository;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfessionalProfileServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SALON_ID = UUID.randomUUID();

    @Mock
    private ProfessionalProfileRepository repo;

    @Mock
    private ProfessionalProfileMapper mapper;

    @InjectMocks
    private ProfessionalProfileService service;

    @Test
    void getProInformationByUserId_withExistingProfile_returnsMappedDto() {

        ProfessionalProfile profile = ProfessionalProfile
            .builder()
            .userId(USER_ID)
            .salonId(SALON_ID)
            .description("Hair stylist, five years of experience")
            .salonRole(SalonRole.EMPLOYEE)
            .build();
        ProfessionalProfileDto expected = new ProfessionalProfileDto(
            SALON_ID, "Hair stylist, five years of experience", SalonRole.EMPLOYEE);

        when(repo.getByUserId(USER_ID)).thenReturn(Optional.of(profile));
        when(mapper.toDto(profile)).thenReturn(expected);

        ProfessionalProfileDto result = service.getProInformationByUserId(USER_ID);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getProInformationByUserId_withUnknownUserId_throwsProfessionalProfileNotFound() {

        when(repo.getByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProInformationByUserId(USER_ID))
            .isInstanceOf(ProfessionalProfileNotFoundException.class);
    }
}
