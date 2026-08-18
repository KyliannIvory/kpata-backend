package ci.kpata.backend.auth.internal.dto;

/** What {@code AuthService#login} returns on success: a ready-to-use access token. */
public record AuthResponseDto(

        String token
) {
}
