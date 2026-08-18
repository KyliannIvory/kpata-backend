package ci.kpata.backend.auth.internal.dto;

import ci.kpata.backend.auth.internal.domain.Role;
import java.util.Set;

/**
 * The data embedded as JWT claims by {@code JwtProvider#createToken}.
 * <p>
 * Built by {@code AuthService} from an already-authenticated result (e.g. the
 * {@code Authentication} returned by {@code AuthenticationManager}), never
 * from raw client input — {@code roles} in particular must reflect what the
 * database says about the user, not what a caller claims about themselves.
 */
public record UserClaimsDto(

        String phoneNumber,
        Set<Role> roles
) {
}
