package ci.kpata.backend.auth.internal.service;

/**
 * Thrown when a signup attempt uses a phone number that is already registered.
 * Callers outside this package (e.g. a {@code @ControllerAdvice}) should
 * catch this type and map it to an HTTP 409 response.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
