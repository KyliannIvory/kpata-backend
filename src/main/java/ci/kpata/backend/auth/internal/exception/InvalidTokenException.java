package ci.kpata.backend.auth.internal.exception;

/**
 * Thrown when a JWT is expired, malformed, has an invalid signature, or has
 * been explicitly revoked. Callers outside this package should catch this
 * type (e.g. in a security filter or {@code @ControllerAdvice}) and map it
 * to an HTTP 401 response.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
