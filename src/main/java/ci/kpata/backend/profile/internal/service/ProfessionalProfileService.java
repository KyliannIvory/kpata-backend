package ci.kpata.backend.profile.internal.service;

import ci.kpata.backend.profile.internal.domain.ProfessionalProfile;
import ci.kpata.backend.profile.internal.dto.ProfessionalProfileDto;
import ci.kpata.backend.profile.internal.exception.ProfessionalProfileNotFoundException;
import ci.kpata.backend.profile.internal.mapper.ProfessionalProfileMapper;
import ci.kpata.backend.profile.internal.repository.ProfessionalProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Looks up a user's professional profile. {@code userId} doubles as the profile's own id
 * (see {@link ProfessionalProfile}'s Javadoc), so there's no separate profile id to resolve.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalProfileService {

    private final ProfessionalProfileRepository repo;

    private final ProfessionalProfileMapper mapper;

    /**
     * @throws ProfessionalProfileNotFoundException if {@code userId} names an existing user
     *                                               who has no professional profile (e.g. a
     *                                               customer-only account)
     */
    public ProfessionalProfileDto getProInformationByUserId(UUID userId) {

        ProfessionalProfile profile = repo.getByUserId(userId).orElseThrow(() ->
            new ProfessionalProfileNotFoundException("Doesn't have a professional profile"));

        return mapper.toDto(profile);
    }
}
