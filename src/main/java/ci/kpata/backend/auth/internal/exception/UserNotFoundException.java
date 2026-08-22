package ci.kpata.backend.auth.internal.exception;

import ci.kpata.backend.shared.exception.NotFoundException;

/**
 * Thrown when a valid token names an account that no longer exists (e.g. deleted between
 * token issuance and use). Carries its shared {@code 404} via {@link NotFoundException},
 * mapped to a response automatically.
 */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
