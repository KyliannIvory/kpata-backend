package ci.kpata.backend.auth.internal.jwt;

import ci.kpata.backend.auth.internal.exception.InvalidTokenException;
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

    /**
     * Request attribute key under which the reason a token was rejected is stashed, so
     * {@code JwtWebSecurityConfig}'s {@code authenticationEntryPoint} can surface it —
     * this class itself never rejects the request (see {@link #doFilterInternal}).
     */
    public static final String TOKEN_ERROR_ATTRIBUTE = "ci.kpata.backend.auth.tokenError";

    private final JwtProvider jwtProvider;

    public JwtFilter(JwtProvider provider) {
        jwtProvider = provider;
    }

    /**
     * Validates the request's token, if any, and sets the resulting {@link Authentication}
     * on the security context when it's valid. This class never rejects the request itself:
     * a revoked, expired or malformed token is treated the same as no token at all — the
     * security context is left empty and the filter chain proceeds regardless. Whether that
     * matters is decided further down the chain, by Spring Security's authorization rules
     * (see {@code JwtWebSecurityConfig#filterChain}): a public route proceeds anonymously,
     * a protected route gets rejected by the {@code authenticationEntryPoint}, which reads
     * {@link #TOKEN_ERROR_ATTRIBUTE} to report the specific reason when there is one.
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

            log.warn("Ignoring invalid token on {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());

            SecurityContextHolder.clearContext();
            request.setAttribute(TOKEN_ERROR_ATTRIBUTE, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
