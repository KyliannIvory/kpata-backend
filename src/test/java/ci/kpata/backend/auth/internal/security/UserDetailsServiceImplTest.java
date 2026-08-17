package ci.kpata.backend.auth.internal.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    private static final String PHONE_NUMBER = "+2250700000000";

    @Mock
    private UserRepository repo;

    @Test
    void loadUserByUsername_withKnownPhoneNumber_mapsPasswordAndRolesToAuthorities() {

        User user = User.builder()
                .firstname("Jane")
                .lastname("Doe")
                .phoneNumber(PHONE_NUMBER)
                .password("hashed-password")
                .roles(Set.of(Role.CUSTOMER, Role.PRO))
                .build();

        when(repo.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(user));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(repo);
        UserDetails details = service.loadUserByUsername(PHONE_NUMBER);

        assertThat(details.getUsername()).isEqualTo(PHONE_NUMBER);
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("CUSTOMER", "PRO");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void loadUserByUsername_withUnknownPhoneNumber_throwsUsernameNotFoundException() {

        when(repo.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.empty());

        UserDetailsServiceImpl service = new UserDetailsServiceImpl(repo);

        assertThatThrownBy(() -> service.loadUserByUsername(PHONE_NUMBER))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
