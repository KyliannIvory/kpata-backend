package ci.kpata.backend.auth.internal.security;

import ci.kpata.backend.auth.internal.jwt.JwtFilter;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.shared.web.ErrorResponseWriter;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Wires the JWT bearer authentication filter into Spring Security and defines
 * which routes are publicly browsable versus which require authentication.
 * <p>
 * Access is deny-by-default: only the routes explicitly listed as public in
 * {@link #filterChain(HttpSecurity)} skip authentication (search/browse endpoints,
 * login and signup); every other route falls through to {@code anyRequest().authenticated()}.
 * This way a new endpoint added later (e.g. confirming an appointment, viewing a
 * customer's history/stats) is protected automatically instead of being exposed
 * until someone remembers to lock it down.
 * <p>
 * Credential-checking beans ({@code AuthenticationManager}, {@code PasswordEncoder})
 * live separately in {@link AuthenticationConfig} — they change for a different
 * reason (login/hashing strategy) than the route policy defined here.
 */
@Configuration
@EnableWebSecurity
public class JwtWebSecurityConfig {

    private final JwtProvider jwtProvider;

    private final ErrorResponseWriter errorResponseWriter;

    public JwtWebSecurityConfig(JwtProvider jwtProvider, ErrorResponseWriter errorResponseWriter) {
        this.jwtProvider = jwtProvider;
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * Allows all origins/methods/headers; tighten to known origins before production.
     * <p>
     * TODO(auth): remplacer {@code "*"} par la ou les origines réelles de ton frontend
     * (ex. {@code http://localhost:5173} en dev, puis le domaine de prod), idéalement
     * via une propriété configurable comme {@code jwt.secret} l'est déjà (@Value avec
     * valeur par défaut), pour ne pas recompiler à chaque changement d'environnement.
     * Pas bloquant tant que tu testes uniquement avec curl/Postman (le CORS n'est
     * appliqué que par les navigateurs), mais ça le devient dès que le frontend appelle
     * l'API depuis une page ouverte dans un navigateur. Détails : docs/auth.md, §6.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) {

        http.csrf(AbstractHttpConfigurer::disable);

        http.sessionManagement(
                config ->
                        config.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.cors(Customizer.withDefaults());

        // Returns a plain 401 instead of a login-page redirect when auth is required but
        // missing/invalid. Runs in the Spring Security filter chain, before
        // DispatcherServlet, so GlobalExceptionHandler can't handle this case — the
        // ErrorResponseWriter call below builds the same ErrorResponseDto JSON by hand.
        // Prefers the specific reason JwtFilter stashed (e.g. "Expired or invalid JWT
        // token") when there was actually a rejected token; otherwise this is a plain
        // "no credentials at all" failure, where authException.getMessage() is null, hence
        // the generic fallback.
        http.exceptionHandling(exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(
                        (request,
                         response,
                         authException) -> {

                            String message = Objects.requireNonNullElse(
                                    (String) request.getAttribute(JwtFilter.TOKEN_ERROR_ATTRIBUTE),
                                    Objects.requireNonNullElse(
                                            authException.getMessage(),
                                            "Authentication is required to access this resource"));

                            errorResponseWriter.write(
                                    response, HttpStatus.UNAUTHORIZED, message, request.getRequestURI());
                        }));

        http.authorizeHttpRequests(config -> {

            // Autorise le forward ET le dispatch ERROR internes de Spring Boot (vers /error)
            // sans authentification : sinon anyRequest().authenticated() plus bas les
            // rejette avec un 401 vide, écrasant le vrai statut (404, 405, 500...) d'une
            // exception non interceptée par GlobalExceptionHandler. Le catch-all
            // Exception.class de GlobalExceptionHandler traite ensuite ces cas avant même
            // qu'ils atteignent BasicErrorController, donc leur corps suit bien le contrat
            // ErrorResponseDto (voir Javadoc de GlobalExceptionHandler).
            config
                    .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR)
                    .permitAll();

            config
                    .requestMatchers("/auth/login", "/auth/signup")
                    .permitAll();

            // TODO: placeholder paths — update once the salon/treatment/availability
            // controllers exist, to match their actual browse/search routes.
            config
                    .requestMatchers(HttpMethod.GET, "/salons/**", "/treatments/**",
                            "/availabilities/**")
                    .permitAll();

            config
                    .anyRequest()
                    .authenticated();
        });

        JwtFilter customFilter = new JwtFilter(jwtProvider);
        http.addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
