package ci.kpata.backend.auth.internal.service;

/**
 * Thrown when a login attempt fails because the phone number/password pair
 * is wrong. Callers outside this package (e.g. a {@code @ControllerAdvice})
 * should catch this type and map it to an HTTP 401 response.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
