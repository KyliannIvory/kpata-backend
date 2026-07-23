package ci.kpata.backend.auth.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.dto.AuthResponseDto;
import ci.kpata.backend.auth.internal.dto.LoginRequestDto;
import ci.kpata.backend.auth.internal.dto.SignupRequestDto;
import ci.kpata.backend.auth.internal.dto.UserClaimsDto;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.auth.internal.repository.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PHONE_NUMBER = "+2250700000000";
    private static final String PASSWORD = "s3cret-password";

    @Mock
    private UserRepository repo;

    @Mock
    private JwtProvider provider;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(repo, provider, encoder, authenticationManager);
    }

    @Test
    void login_withValidCredentials_returnsTokenBuiltFromAuthenticatedRoles() {

        var authenticated = new UsernamePasswordAuthenticationToken(
                PHONE_NUMBER, PASSWORD, List.of(new SimpleGrantedAuthority("CUSTOMER")));

        when(authenticationManager.authenticate(any())).thenReturn(authenticated);
        when(provider.createToken(any())).thenReturn("signed-token");

        AuthResponseDto response = authService.login(new LoginRequestDto(PHONE_NUMBER, PASSWORD));

        assertThat(response.token()).isEqualTo("signed-token");

        ArgumentCaptor<UserClaimsDto> claimsCaptor = ArgumentCaptor.forClass(UserClaimsDto.class);
        verify(provider).createToken(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue().phoneNumber()).isEqualTo(PHONE_NUMBER);
        assertThat(claimsCaptor.getValue().roles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void login_withWrongCredentials_throwsInvalidCredentialsAndPreservesCause() {

        var cause = new BadCredentialsException("Bad credentials");
        when(authenticationManager.authenticate(any())).thenThrow(cause);

        assertThatThrownBy(() ->
                authService.login(new LoginRequestDto(PHONE_NUMBER, "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasCause(cause);
    }

    @Test
    void signup_withNewPhoneNumber_savesEncodedPasswordAndReturnsToken() {

        var dto = new SignupRequestDto("Kyliann", "Kouame", PHONE_NUMBER, PASSWORD, "k@x.com");

        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(encoder.encode(PASSWORD)).thenReturn("hashed-password");
        when(provider.createToken(any())).thenReturn("signed-token");

        AuthResponseDto response = authService.signup(dto);

        assertThat(response.token()).isEqualTo("signed-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertThat(saved.getFirstname()).isEqualTo("Kyliann");
        assertThat(saved.getLastname()).isEqualTo("Kouame");
        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE_NUMBER);
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.getRoles()).containsExactly(Role.CUSTOMER);
    }

    @Test
    void signup_withExistingPhoneNumber_throwsAndNeverSaves() {

        var dto = new SignupRequestDto("Kyliann", "Kouame", PHONE_NUMBER, PASSWORD, "k@x.com");

        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(dto))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void logout_withBearerToken_revokesIt() {

        authService.logout("Bearer signed-token");

        verify(provider).revokeToken("signed-token");
    }

    @Test
    void logout_withoutAuthorizationHeader_doesNothing() {

        authService.logout(null);

        verify(provider, never()).revokeToken(anyString());
    }

    @Test
    void logout_withNonBearerAuthorizationHeader_doesNothing() {

        authService.logout("Basic dXNlcjpwYXNz");

        verify(provider, never()).revokeToken(anyString());
    }
}
