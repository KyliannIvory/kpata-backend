package ci.kpata.backend.auth.internal.repository;

import ci.kpata.backend.auth.internal.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence access for {@link User}. Used by {@code AuthService} for
 * signup/login and by {@code UserDetailsServiceImpl} to authenticate
 * requests, since a user's phone number acts as their login identifier.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by their phone number, which doubles as the username
     * used throughout the auth flow (login, JWT subject).
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Check if a user exists in the database based on their phone number.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
