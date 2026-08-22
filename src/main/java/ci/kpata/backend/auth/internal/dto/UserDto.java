package ci.kpata.backend.auth.internal.dto;

import ci.kpata.backend.auth.internal.domain.Role;

import java.util.Set;
import java.util.UUID;

/**
 * What {@code GET /auth/me} returns for the currently authenticated user. Deliberately has
 * no {@code password} field, hashed or not — never expose it, even internally past this
 * point. Only mapped one way, {@code User} → {@code UserDto} (see {@link
 * ci.kpata.backend.auth.internal.mapper.UserMapper}); there's no reverse mapping back to
 * {@code User}, since that would let a client set any field it sends — {@code roles}
 * included — on save.
 * <p>
 * {@code id} exists here so a client can turn "the current user" into a routable resource
 * id — e.g. calling {@code GET /professionals/{id}} for their own professional profile —
 * without needing a separate lookup.
 */
public record UserDto(

    UUID id,
    String firstname,
    String lastname,
    String phoneNumber,
    String email,
    Set<Role> roles

) {
}
