package ci.kpata.backend.auth.internal.dto;

/**
 * What a client is allowed to send to log in. Deliberately has no
 * {@code roles} field: a caller cannot claim its own permissions, only
 * {@code AuthService} — from the authenticated result — decides them.
 */
public record LoginRequestDto(

        String phoneNumber,
        String password
) {
}
