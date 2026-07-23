package ci.kpata.backend.auth.internal.jwt;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.dto.UserClaimsDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Issues, parses and revokes JWT access tokens.
 * <p>
 * Authentication is fully stateless: {@link #getAuthentication(String)} trusts the
 * subject and roles embedded in the token's claims and never queries the database.
 * A role change or account deactivation only takes effect once the token is renewed
 * or explicitly revoked — see {@link #revokeToken(String)}.
 * <p>
 * Revocation adds the token to an in-memory blacklist, checked before its natural
 * expiration. Each entry is purged automatically once that expiration passes, so
 * the map only ever holds currently-revoked, not-yet-expired tokens.
 * <p>
 * TODO Redis: this blacklist is in-memory only — it is lost on restart and not shared
 * across instances. Once the app is scaled horizontally, replace {@code revokedTokens}
 * with a shared store that has native TTL support (e.g. Redis: {@code SET token EX
 * secondsUntilExpiry}), which removes the need for {@link #purgeExpiredRevokedTokens()} too.
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    private static final long CLEANUP_INTERVAL_MS = 900000;

    private static final String ROLES_CLAIM = "auth";

    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String secretText;

    @Value("${jwt.expiration}")
    private long validityInMilliseconds;

    private SecretKey secretKey;

    /**
     * Revoked token -> that token's own expiration date, so it can be purged once it's moot.
     */
    private final Map<String, Date> revokedTokens = new ConcurrentHashMap<>();

    /**
     * Derives the signing key from {@code jwt.secret} once Spring has injected it.
     */
    @PostConstruct
    protected void init() {
        secretKey = Keys.hmacShaKeyFor(secretText.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a signed JWT for the given user, using their phone number as subject
     * and their roles as the {@value #ROLES_CLAIM} claim.
     *
     * @return the compact, signed JWT string
     */
    public String createToken(UserClaimsDto userDto) {

        Set<String> roleNames = userDto
                .roles()
                .stream()
                .filter(Objects::nonNull)
                .map(Role::name)
                .collect(Collectors.toSet());

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        String token = Jwts
                .builder()
                .subject(userDto.phoneNumber())
                .claim(ROLES_CLAIM, roleNames)
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();

        log.info("Token issued for subject={}, roles={}, expiresAt={}",
                userDto.phoneNumber(), roleNames, validity);

        return token;
    }

    /**
     * Extracts the raw token from an {@code Authorization} header value, stripping the
     * {@code Bearer } prefix. The single place that knows this header format, so
     * {@code JwtFilter} and {@code AuthService} don't each parse it their own way.
     *
     * @return the raw token, or {@code null} if the header is absent or not a bearer token
     */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Parses and verifies a token's signature and expiration.
     *
     * @return the token's claims (subject, roles, expiration...)
     * @throws InvalidTokenException if the signature is invalid, the token is
     *                               expired, or it is otherwise malformed
     */
    private Claims parseAndVerify(String token) {
        try {
            return Jwts
                    .parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected invalid or expired JWT: {}", e.getMessage());
            throw new InvalidTokenException("Expired or invalid JWT token");
        }
    }

    /**
     * Extracts the subject (phone number) from a token, verifying it first.
     *
     * @throws InvalidTokenException if the token is invalid or expired
     */
    public String getUsername(String token) {
        return parseAndVerify(token).getSubject();
    }

    /**
     * Revokes a token before its natural expiration (e.g. on logout) by adding it
     * to the blacklist checked by {@link #getAuthentication(String)}, keyed alongside
     * its own expiration date so {@link #purgeExpiredRevokedTokens()} can drop it
     * once that date passes.
     *
     * @throws InvalidTokenException if the token is invalid or expired
     */
    public void revokeToken(String token) {
        Claims claims = parseAndVerify(token);
        revokedTokens.put(token, claims.getExpiration());
        log.info("Token revoked for subject={}, expiresAt={}",
                claims.getSubject(), claims.getExpiration());
    }

    /**
     * Drops blacklist entries whose token has already expired naturally — from
     * that point on {@link #getAuthentication(String)} would reject them anyway via
     * the JWT expiration check, so keeping them around only wastes memory.
     * Runs every {@link #CLEANUP_INTERVAL_MS} ms.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    void purgeExpiredRevokedTokens() {
        Date now = new Date();
        int sizeBefore = revokedTokens.size();
        revokedTokens
                .values()
                .removeIf(expiration -> expiration.before(now));
        log.debug("Purged {} expired entries from the revoked-token blacklist ({} remaining)",
                sizeBefore - revokedTokens.size(), revokedTokens.size());
    }

    /**
     * Builds a Spring Security {@link Authentication} directly from a token's claims —
     * no database lookup is performed, so the resulting authorities reflect the roles
     * that were embedded in the token when it was issued.
     *
     * @throws InvalidTokenException if the token was revoked, is expired, or is invalid
     */
    public Authentication getAuthentication(String token) {

        if (revokedTokens.containsKey(token)) {
            log.warn("Rejected a revoked token");
            throw new InvalidTokenException("Token has been revoked");
        }

        Claims claims = parseAndVerify(token);

        List<GrantedAuthority> authorities = ((List<?>) claims.get(ROLES_CLAIM))
                .stream()
                .map(Object::toString)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        log.debug("Authenticated subject={} with authorities={}",
                claims.getSubject(), authorities);

        return new UsernamePasswordAuthenticationToken(claims.getSubject(), "", authorities);
    }
}
