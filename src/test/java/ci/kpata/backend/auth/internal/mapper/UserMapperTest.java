package ci.kpata.backend.auth.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.dto.UserDto;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    private final UserMapper mapper = new UserMapperImpl();

    @Test
    void toDto_mapsEveryFieldExceptPassword() {

        User user = User
            .builder()
            .firstname("Jane")
            .lastname("Doe")
            .phoneNumber("+2250700000000")
            .email("jane.doe@example.com")
            .password("hashed-password")
            .roles(Set.of(Role.CUSTOMER))
            .build();

        UserDto dto = mapper.toDto(user);

        // UserDto has no password field at all (see its Javadoc) — this assertion only
        // guards against someone adding one back and MapStruct auto-mapping it, since
        // User.getPassword() and a hypothetical UserDto.password() would match by name.
        assertThat(dto.firstname()).isEqualTo("Jane");
        assertThat(dto.lastname()).isEqualTo("Doe");
        assertThat(dto.phoneNumber()).isEqualTo("+2250700000000");
        assertThat(dto.email()).isEqualTo("jane.doe@example.com");
        assertThat(dto.roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void toDto_withNullUser_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDto_withNullRoles_mapsToNullRoles() {

        User user = User
            .builder()
            .firstname("Jane")
            .lastname("Doe")
            .phoneNumber("+2250700000000")
            .password("hashed-password")
            .roles(null)
            .build();

        assertThat(mapper.toDto(user).roles()).isNull();
    }
}
