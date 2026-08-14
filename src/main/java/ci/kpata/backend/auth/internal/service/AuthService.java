package ci.kpata.backend.auth.internal.service;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.dto.LoginRequestDto;
import ci.kpata.backend.auth.internal.dto.AuthResponseDto;
import ci.kpata.backend.auth.internal.dto.SignupRequestDto;
import ci.kpata.backend.auth.internal.dto.UserClaimsDto;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.auth.internal.repository.UserRepository;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates login and signup: delegates credential checks and token issuing.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository repo;

    private final JwtProvider provider;

    private final PasswordEncoder encoder;

    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository repo, JwtProvider provider, PasswordEncoder encoder,
                       AuthenticationManager authenticationManager) {
        this.repo = repo;
        this.provider = provider;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Verifies the phone number/password pair, then issues an access token carrying
     * the roles found by authentication — never roles supplied by the caller.
     *
     * @throws InvalidCredentialsException if the phone number/password pair is wrong
     */
    public AuthResponseDto login(LoginRequestDto request) {

        String phoneNumber = request.phoneNumber();
        String password = request.password();

        log.info("Login attempt for phoneNumber={}", phoneNumber);

        Authentication result;
        try {
            result = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(phoneNumber, password));
        } catch (AuthenticationException e) {
            log.warn("Login failed for phoneNumber={}: {}", phoneNumber, e.getMessage());
            throw new InvalidCredentialsException("Invalid credentials", e);
        }

        // Safe here specifically: authenticationManager is backed by a
        // DaoAuthenticationProvider (see AuthenticationConfig), which always returns the
        // UserDetails loaded by UserDetailsServiceImpl as the principal. This does NOT hold
        // for every Authentication in the app — JwtProvider#getAuthentication builds one
        // with a raw String principal instead, for every request JwtFilter authenticates.
        // Don't copy this cast onto SecurityContextHolder's current authentication elsewhere.
        UserDetails userDetails = (UserDetails) result.getPrincipal();
        Set<Role> roles = Objects
                .requireNonNull(userDetails, "Authenticated principal must not be null")
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .map(Role::valueOf)
                .collect(Collectors.toSet());

        UserClaimsDto claims = new UserClaimsDto(phoneNumber, roles);
        String token = provider.createToken(claims);

        log.info("Login succeeded for phoneNumber={}, roles={}", phoneNumber, roles);

        return new AuthResponseDto(token);
    }

    public AuthResponseDto signup(SignupRequestDto dto) {

        log.info("Signup attempt for phoneNumber={}", dto.phoneNumber());

        if (repo.existsByPhoneNumber(dto.phoneNumber())) {
            log.warn("Signup rejected, phoneNumber={} already registered", dto.phoneNumber());
            throw new UserAlreadyExistsException(
                    "An account with this phone number already exists");
        }

        Set<Role> roles = Set.of(Role.CUSTOMER);

        repo.save(User
                .builder()
                .firstname(dto.firstname())
                .lastname(dto.lastname())
                .phoneNumber(dto.phoneNumber())
                .email(dto.email())
                .roles(roles)
                .password(encoder.encode(dto.password()))
                .build());

        UserClaimsDto claims = new UserClaimsDto(dto.phoneNumber(), roles);

        String token = provider.createToken(claims);

        log.info("User registered: phoneNumber={}, roles={}", dto.phoneNumber(), roles);

        return new AuthResponseDto(token);
    }

    /**
     * Revokes the token carried by the given {@code Authorization} header value, if any.
     * Silently does nothing when the header is missing or isn't a bearer token, since
     * there is then nothing to revoke.
     */
    public void logout(String authorizationValue) {

        String token = JwtProvider.extractBearerToken(authorizationValue);

        if (token != null) {
            log.info("Logout: revoking token");
            provider.revokeToken(token);
        } else {
            log.debug("Logout called without a bearer token, nothing to revoke");
        }
    }
}
