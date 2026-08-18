package ci.kpata.backend.auth.internal.service;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.dto.*;
import ci.kpata.backend.auth.internal.exception.InvalidCredentialsException;
import ci.kpata.backend.auth.internal.exception.UserAlreadyExistsException;
import ci.kpata.backend.auth.internal.exception.UserNotFoundException;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.auth.internal.mapper.UserMapper;
import ci.kpata.backend.auth.internal.repository.UserRepository;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import ci.kpata.backend.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository repo;

    private final JwtProvider provider;

    private final PasswordEncoder encoder;

    private final AuthenticationManager authenticationManager;

    private final UserMapper mapper;

    private final PhoneNumberNormalizer normalizer;

    /**
     * Verifies the phone number/password pair, then issues an access token carrying
     * the roles found by authentication — never roles supplied by the caller.
     *
     * @throws InvalidCredentialsException if the phone number/password pair is wrong
     */
    public AuthResponseDto login(LoginRequestDto request) {

        String phoneNumber = normalizer.normalize(request.phoneNumber());
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

    /**
     * Normalizes the phone number once ({@code phone}) and reuses that same value for the
     * uniqueness check, the persisted entity and the JWT claims. Comparing or storing a mix
     * of the raw {@code dto.phoneNumber()} and the normalized form let the same real number
     * silently resolve to two different accounts (fixed 2026-08-18, see docs/auth.md §6.3).
     */
    public AuthResponseDto signup(SignupRequestDto dto) {

        log.info("Signup attempt for phoneNumber={}", dto.phoneNumber());

        String email = dto.email();
        String phone = normalizer.normalize(dto.phoneNumber());

        validateSignup(phone, email);

        if (email != null && email.isBlank()) {
            email = null;
        }

        Set<Role> roles = Set.of(Role.CUSTOMER);

        User user = User.builder()
            .firstname(dto.firstname())
            .lastname(dto.lastname())
            .phoneNumber(phone)
            .email(email)
            .roles(roles)
            .password(encoder.encode(dto.password()))
            .build();

        try {
            repo.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Signup race condition detected for phoneNumber={}: {}", phone, e.getMessage());
            throw new UserAlreadyExistsException(
                "An account with this phone number or email already exists");
        }

        UserClaimsDto claims = new UserClaimsDto(phone, roles);

        String token = provider.createToken(claims);

        log.info("User registered: phoneNumber={}, roles={}", phone, roles);

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

    /**
     * Looks up the full {@code User} behind a token's subject and maps it to the DTO
     * {@code GET /auth/me} returns — never the entity itself, which carries the password.
     *
     * @throws UserNotFoundException if the token is valid but the account it names no
     *                               longer exists (e.g. deleted after the token was issued)
     */
    public UserDto findUserByPhoneNumber(String phoneNumber) {

        User user = repo.findByPhoneNumber(phoneNumber).orElseThrow(() ->
            new UserNotFoundException("user not found")
        );
        return mapper.toDto(user);
    }

    private void validateSignup(String phone, String email) {

        if (repo.existsByPhoneNumber(phone)) {
            log.warn("Signup rejected, phoneNumber={} already registered", phone);
            throw new UserAlreadyExistsException(
                "An account with this phone number already exists");
        }

        if (email != null
            && !email.isBlank()
            && repo.existsByEmailIgnoreCase(email)) {
            log.warn("Signup rejected, email={} already registered", email);
            throw new UserAlreadyExistsException(
                "An account with this email already exists");
        }
    }
}
