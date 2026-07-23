package ci.kpata.backend.auth.internal.security;

import ci.kpata.backend.auth.internal.jwt.JwtFilter;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

    public JwtWebSecurityConfig(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /** Allows all origins/methods/headers; tighten to known origins before production. */
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
        // missing/invalid.
        http.exceptionHandling(exceptionHandling ->
                exceptionHandling.authenticationEntryPoint(
                        (request,
                         response,
                         authException) ->

                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                        authException.getMessage())));

        http.authorizeHttpRequests(config -> {

            config
                    .dispatcherTypeMatchers(DispatcherType.FORWARD)
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
