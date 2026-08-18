package ci.kpata.backend.auth.internal.dto;

import ci.kpata.backend.shared.validation.IvoryCoastPhone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a client is allowed to send to log in. Deliberately has no
 * {@code roles} field: a caller cannot claim its own permissions, only
 * {@code AuthService} — from the authenticated result — decides them.
 * <p>
 * Carries the same {@code @NotBlank}/{@code @Size}/{@link IvoryCoastPhone} constraints as
 * {@link SignupRequestDto}: without them, an empty {@code phoneNumber} or {@code password}
 * would still pass {@code @Valid} and reach {@code AuthenticationManager}, which rejects it
 * anyway via {@code InvalidCredentialsException} — but with a generic "Invalid credentials"
 * message instead of a precise 400 like "phoneNumber must not be blank".
 */
public record LoginRequestDto(

        @NotBlank
        @IvoryCoastPhone
        String phoneNumber,

        @NotBlank
        @Size(min = 8, max = 100 )
        String password
) {
}
