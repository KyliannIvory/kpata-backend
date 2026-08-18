package ci.kpata.backend.auth.internal.dto;

import ci.kpata.backend.shared.validation.IvoryCoastPhone;
import ci.kpata.backend.shared.validation.StrictEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequestDto(

        @NotBlank
        @Size(max = 50)
        String firstname,

        @NotBlank
        @Size(max = 50)
        String lastname,

        @IvoryCoastPhone
        @NotBlank
        String phoneNumber,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @StrictEmail
        String email
) {
}
