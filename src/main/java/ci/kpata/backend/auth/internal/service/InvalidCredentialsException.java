package ci.kpata.backend.auth.internal.service;

import ci.kpata.backend.shared.web.ApplicationException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a login attempt fails because the phone number/password pair
 * is wrong. Callers outside this package (e.g. a {@code @ControllerAdvice})
 * should catch this type and map it to an HTTP 401 response.
 */
public class InvalidCredentialsException extends ApplicationException {

    private static final HttpStatus STATUS_CODE = HttpStatus.UNAUTHORIZED;

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, STATUS_CODE, cause);
    }
}
