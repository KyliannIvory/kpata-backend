package ci.kpata.backend.auth.internal.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.dto.UserClaimsDto;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-with-enough-bytes-for-hs256";
    private static final String PHONE_NUMBER = "+2250700000000";

    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "secretText", SECRET);
        ReflectionTestUtils.setField(provider, "validityInMilliseconds", 60_000L);
        ReflectionTestUtils.invokeMethod(provider, "init");
    }

    @Test
    void createToken_thenGetAuthentication_roundTripsSubjectAndRoles() {

        var claims = new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER, Role.PRO));

        String token = provider.createToken(claims);
        assertThat(token).isNotBlank();

        var authentication = provider.getAuthentication(token);

        assertThat(authentication.getName()).isEqualTo(PHONE_NUMBER);
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("CUSTOMER", "PRO");
    }

    @Test
    void getUsername_returnsTheTokenSubject() {

        var claims = new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER));
        String token = provider.createToken(claims);

        assertThat(provider.getUsername(token)).isEqualTo(PHONE_NUMBER);
    }

    @Test
    void getAuthentication_withMalformedToken_throwsInvalidTokenException() {

        assertThatThrownBy(() -> provider.getAuthentication("not-a-jwt"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getAuthentication_withTamperedSignature_throwsInvalidTokenException() {

        var claims = new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER));
        String token = provider.createToken(claims);
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertThatThrownBy(() -> provider.getAuthentication(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getAuthentication_withExpiredToken_throwsInvalidTokenException() {

        ReflectionTestUtils.setField(provider, "validityInMilliseconds", -1_000L);
        var claims = new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER));
        String token = provider.createToken(claims);

        assertThatThrownBy(() -> provider.getAuthentication(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getAuthentication_afterRevocation_throwsInvalidTokenException() {

        var claims = new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER));
        String token = provider.createToken(claims);

        provider.revokeToken(token);

        assertThatThrownBy(() -> provider.getAuthentication(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void extractBearerToken_withValidHeader_returnsRawToken() {
        assertThat(JwtProvider.extractBearerToken("Bearer abc123")).isEqualTo("abc123");
    }

    @Test
    void extractBearerToken_withMissingHeader_returnsNull() {
        assertThat(JwtProvider.extractBearerToken(null)).isNull();
    }

    @Test
    void extractBearerToken_withNonBearerHeader_returnsNull() {
        assertThat(JwtProvider.extractBearerToken("Basic dXNlcjpwYXNz")).isNull();
    }
}
