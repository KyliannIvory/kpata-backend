package ci.kpata.backend.auth.internal.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the {@link SecurityContextHolder} from a {@code Bearer} JWT found on
 * incoming requests, so downstream authorization checks see an authenticated user.
 * Runs once per request (see {@link OncePerRequestFilter}).
 */
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    private final JwtProvider jwtProvider;

    public JwtFilter(JwtProvider provider) {
        jwtProvider = provider;
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

        String token = JwtProvider.extractBearerToken(request.getHeader("Authorization"));

        try {
            if (token != null) {

                Authentication auth = jwtProvider.getAuthentication(token);
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(auth);

                log.debug("Authenticated {} {} as subject={}",
                        request.getMethod(), request.getRequestURI(), auth.getName());
            } else {
                log.debug("No bearer token on {} {}, proceeding unauthenticated",
                        request.getMethod(), request.getRequestURI());
            }
        } catch (InvalidTokenException e) {

            log.warn("Rejecting {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());

            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
