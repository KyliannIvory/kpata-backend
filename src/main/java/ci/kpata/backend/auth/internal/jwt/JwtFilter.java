package ci.kpata.backend.auth.internal.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the {@link SecurityContextHolder} from a {@code Bearer} JWT found on
 * incoming requests, so downstream authorization checks see an authenticated user.
 * Runs once per request (see {@link OncePerRequestFilter}).
 */
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtFilter(JwtProvider provider) {
        jwtProvider = provider;
    }

    /**
     * Reads the {@code Authorization} header and strips the {@code Bearer } prefix.
     *
     * @return the raw token, or {@code null} if the header is absent or not a bearer token
     */
    private String extractToken(HttpServletRequest req) {

        String bearerToken = req.getHeader("Authorization");

        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {

            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Validates the request's token, if any, and sets the resulting {@link Authentication}
     * on the security context when it's valid. A revoked, expired or malformed token
     * short-circuits the filter chain with a 401 instead of propagating the exception.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        try {
            if (token != null) {

                Authentication auth = jwtProvider.getAuthentication(token);
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(auth);
            }
        } catch (InvalidTokenException e) {

            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
