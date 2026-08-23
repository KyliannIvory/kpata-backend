package ci.kpata.backend.profile;

import ci.kpata.backend.profile.internal.dto.ProfessionalProfileDto;
import ci.kpata.backend.profile.internal.service.ProfessionalProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/professionals")
@RequiredArgsConstructor
public class ProfessionalProfileController {

    private final ProfessionalProfileService service;

    /**
     * {@code userId} is a {@code User}'s id, not a separate professional-profile id — see
     * {@code ProfessionalProfile}'s Javadoc. Meant to be publicly browsable (a client
     * clicking a pro's profile card), but still requires authentication for now — see the
     * TODO in {@code JwtWebSecurityConfig}.
     */
    @GetMapping("/{userId}")
    public ProfessionalProfileDto getProfessional(@PathVariable UUID userId) {

        return service.getProInformationByUserId(userId);
    }
}
