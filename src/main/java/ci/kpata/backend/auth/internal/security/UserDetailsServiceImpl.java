package ci.kpata.backend.auth.internal.security;

import ci.kpata.backend.auth.internal.domain.User;
import ci.kpata.backend.auth.internal.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a {@link User} by phone number and adapts it to Spring Security's
 * {@link UserDetails}, so it can be checked by {@code AuthenticationManager}
 * during login (see {@code AuthenticationConfig}).
 * <p>
 * The domain {@link User} never leaves this class as-is: only the fields
 * Spring Security needs (password, authorities) are copied onto the
 * framework's own {@code User} builder, fully qualified below to avoid
 * clashing with the domain type of the same name.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository repo;

    public UserDetailsServiceImpl(UserRepository repo) {
        this.repo = repo;
    }

    /**
     * @throws UsernameNotFoundException if no user has this phone number.
     *                                   The message intentionally omits the phone number, and
     *                                   {@code DaoAuthenticationProvider} collapses this into a
     *                                   generic
     *                                   {@code BadCredentialsException} by default, so callers
     *                                   can't use this
     *                                   to enumerate which phone numbers are registered.
     */
    @Override
    public UserDetails loadUserByUsername(@NonNull String username)
            throws UsernameNotFoundException {

        log.debug("Loading user by phoneNumber={}", username);

        final User user = repo
                .findByPhoneNumber(username)
                .orElseThrow(() -> {
                    log.warn("No account found for phoneNumber={}", username);
                    return new UsernameNotFoundException("User not found");
                });

        var authorities = user
                .getRoles()
                .stream()
                .map(role ->
                        new SimpleGrantedAuthority(role.name())
                )
                .toList();

        // accountExpired/accountLocked/credentialsExpired/disabled are hardcoded
        // to false because User has no account-status field yet. Once account
        // status (e.g. suspended/banned) is modeled, wire it in here instead.
        return org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
