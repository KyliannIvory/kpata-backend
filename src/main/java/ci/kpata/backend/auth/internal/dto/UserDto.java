package ci.kpata.backend.auth.internal.dto;

import ci.kpata.backend.auth.internal.domain.Role;
import java.util.Set;

public record UserDto(

        String phoneNumber,
        String password,
        Set<Role> roles

) {
}
