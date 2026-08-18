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
import ci.kpata.backend.auth.internal.dto.UserDto;
import ci.kpata.backend.auth.internal.exception.InvalidCredentialsException;
import ci.kpata.backend.auth.internal.exception.UserAlreadyExistsException;
import ci.kpata.backend.auth.internal.exception.UserNotFoundException;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.auth.internal.mapper.UserMapper;
import ci.kpata.backend.auth.internal.repository.UserRepository;
import ci.kpata.backend.shared.validation.PhoneNumberNormalizer;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PHONE_NUMBER = "+2250700000000";
    private static final String RAW_PHONE_NUMBER = "0700000000";
    private static final String PASSWORD = "s3cret-password";

    @Mock
    private UserRepository repo;

    @Mock
    private JwtProvider provider;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserMapper mapper;

    @Mock
    private PhoneNumberNormalizer normalizer;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_withValidCredentials_returnsTokenBuiltFromAuthenticatedRoles() {

        // AuthenticationManager.authenticate(...) is backed by a DaoAuthenticationProvider,
        // which returns an Authentication whose principal is the UserDetails loaded by
        // UserDetailsServiceImpl (see AuthService#login) — not the raw phone number.
        UserDetails userDetails = org.springframework.security.core.userdetails.User
            .withUsername(PHONE_NUMBER)
            .password(PASSWORD)
            .authorities(List.of(new SimpleGrantedAuthority("CUSTOMER")))
            .build();

        var authenticated = new UsernamePasswordAuthenticationToken(
            userDetails, PASSWORD, List.of(new SimpleGrantedAuthority("CUSTOMER")));

        when(normalizer.normalize(PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
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
        when(normalizer.normalize(PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(authenticationManager.authenticate(any())).thenThrow(cause);

        LoginRequestDto loginRequestDto = new LoginRequestDto(PHONE_NUMBER, "wrong-password");

        assertThatThrownBy(() ->
            authService.login(loginRequestDto))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasCause(cause);
    }

    @Test
    void signup_withNewPhoneNumber_savesEncodedPasswordAndReturnsTokenUsingNormalizedPhoneNumber() {

        // RAW_PHONE_NUMBER (local format) vs PHONE_NUMBER (E.164, what normalizer.normalize
        // returns) are deliberately different here: this test only passes if every place that
        // touches the phone number after signup — uniqueness check, saved entity, JWT claims —
        // uses the normalized form, not the raw one the client sent.
        var dto = new SignupRequestDto("Jane", "Doe", RAW_PHONE_NUMBER, PASSWORD, "jane.doe@example.com");

        when(normalizer.normalize(RAW_PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(encoder.encode(PASSWORD)).thenReturn("hashed-password");
        when(provider.createToken(any())).thenReturn("signed-token");

        AuthResponseDto response = authService.signup(dto);

        assertThat(response.token()).isEqualTo("signed-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());

        User saved = userCaptor.getValue();
        assertThat(saved.getFirstname()).isEqualTo("Jane");
        assertThat(saved.getLastname()).isEqualTo("Doe");
        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE_NUMBER);
        assertThat(saved.getPassword()).isEqualTo("hashed-password");
        assertThat(saved.getRoles()).containsExactly(Role.CUSTOMER);

        ArgumentCaptor<UserClaimsDto> claimsCaptor = ArgumentCaptor.forClass(UserClaimsDto.class);
        verify(provider).createToken(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue().phoneNumber()).isEqualTo(PHONE_NUMBER);
    }

    @Test
    void signup_withExistingPhoneNumber_throwsAndNeverSaves() {

        var dto = new SignupRequestDto("Jane", "Doe", RAW_PHONE_NUMBER, PASSWORD, "jane.doe@example.com");

        when(normalizer.normalize(RAW_PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(dto))
            .isInstanceOf(UserAlreadyExistsException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void signup_withExistingEmail_throwsAndNeverSaves() {

        var dto = new SignupRequestDto("Jane", "Doe", RAW_PHONE_NUMBER, PASSWORD, "jane.doe@example.com");

        when(normalizer.normalize(RAW_PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(repo.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(dto))
            .isInstanceOf(UserAlreadyExistsException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void signup_withBlankEmail_treatsItAsNoEmail() {

        var dto = new SignupRequestDto("Jane", "Doe", RAW_PHONE_NUMBER, PASSWORD, "  ");

        when(normalizer.normalize(RAW_PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(encoder.encode(PASSWORD)).thenReturn("hashed-password");
        when(provider.createToken(any())).thenReturn("signed-token");

        authService.signup(dto);

        verify(repo, never()).existsByEmailIgnoreCase(any());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void signup_withNullEmail_skipsEmailUniquenessCheck() {

        var dto = new SignupRequestDto("Jane", "Doe", RAW_PHONE_NUMBER, PASSWORD, null);

        when(normalizer.normalize(RAW_PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(encoder.encode(PASSWORD)).thenReturn("hashed-password");
        when(provider.createToken(any())).thenReturn("signed-token");

        authService.signup(dto);

        verify(repo, never()).existsByEmailIgnoreCase(any());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void signup_whenSaveRacesWithConcurrentSignup_throwsUserAlreadyExists() {

        var dto = new SignupRequestDto("Jane", "Doe", RAW_PHONE_NUMBER, PASSWORD, "jane.doe@example.com");

        when(normalizer.normalize(RAW_PHONE_NUMBER)).thenReturn(PHONE_NUMBER);
        when(repo.existsByPhoneNumber(PHONE_NUMBER)).thenReturn(false);
        when(repo.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(false);
        when(encoder.encode(PASSWORD)).thenReturn("hashed-password");
        when(repo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> authService.signup(dto))
            .isInstanceOf(UserAlreadyExistsException.class);

        verify(provider, never()).createToken(any());
    }

    @Test
    void findUserByPhoneNumber_withExistingUser_returnsMappedDto() {

        User user = User
            .builder()
            .firstname("Jane")
            .lastname("Doe")
            .phoneNumber(PHONE_NUMBER)
            .password("hashed-password")
            .roles(java.util.Set.of(Role.CUSTOMER))
            .build();
        UserDto expected = new UserDto("Jane", "Doe", PHONE_NUMBER, null, java.util.Set.of(Role.CUSTOMER));

        when(repo.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.of(user));
        when(mapper.toDto(user)).thenReturn(expected);

        UserDto result = authService.findUserByPhoneNumber(PHONE_NUMBER);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void findUserByPhoneNumber_withUnknownPhoneNumber_throwsUserNotFound() {

        when(repo.findByPhoneNumber(PHONE_NUMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.findUserByPhoneNumber(PHONE_NUMBER))
            .isInstanceOf(UserNotFoundException.class);
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
