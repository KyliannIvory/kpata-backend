package ci.kpata.backend.profile.internal.repository;

import ci.kpata.backend.profile.internal.domain.ProfessionalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, UUID> {


    Optional<ProfessionalProfile> getByUserId(UUID userId);
}
