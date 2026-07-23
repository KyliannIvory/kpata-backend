package ci.kpata.backend.auth.internal.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Provides the beans that verify a user's credentials at login.
 * <p>
 * Kept separate from {@link JwtWebSecurityConfig}: this class changes when the
 * credential-checking strategy changes (password hashing algorithm, how users are
 * looked up), whereas {@link JwtWebSecurityConfig} changes when the route access
 * policy changes — two unrelated reasons to change, so two classes.
 */
@Configuration
public class AuthenticationConfig {

    /**
     * Backs the login flow: checks credentials against {@link UserDetailsService} +
     * {@link PasswordEncoder}.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            @Autowired PasswordEncoder encoder,
            @Autowired UserDetailsService userDetailsService) {

        var authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(encoder);

        return new ProviderManager(authProvider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(12);
    }
}
