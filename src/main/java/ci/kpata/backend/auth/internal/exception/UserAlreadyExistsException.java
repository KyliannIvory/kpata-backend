package ci.kpata.backend.auth.internal.exception;

import ci.kpata.backend.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a signup attempt uses a phone number that is already registered.
 * Carries its own {@link HttpStatus#CONFLICT} via {@link ApplicationException}, so
 * {@code GlobalExceptionHandler} maps it to a {@code 409} response automatically —
 * no per-exception handler needed there.
 */
public class UserAlreadyExistsException extends ApplicationException {

    private static final HttpStatus STATUS_CODE = HttpStatus.CONFLICT;

    public UserAlreadyExistsException(String message) {
        super(message, STATUS_CODE);
    }
}
