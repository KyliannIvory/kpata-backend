package ci.kpata.backend.auth.internal.exception;

import ci.kpata.backend.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a valid token names an account that no longer exists (e.g. deleted between
 * token issuance and use). Carries its own {@link HttpStatus#NOT_FOUND} via {@link
 * ApplicationException}, mapped to a {@code 404} response automatically.
 */
public class UserNotFoundException extends ApplicationException {

    private static final HttpStatus STATUS_CODE = HttpStatus.NOT_FOUND;

    public UserNotFoundException(String message) {
        super(message, STATUS_CODE);
    }
}
