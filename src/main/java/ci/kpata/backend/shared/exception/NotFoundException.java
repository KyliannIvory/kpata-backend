package ci.kpata.backend.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for "no such resource" failures — a {@link HttpStatus#NOT_FOUND} fixed once
 * here instead of repeated as a {@code private static final HttpStatus} in every
 * "not found" exception across modules (e.g. {@code UserNotFoundException},
 * {@code ProfessionalProfileNotFoundException}). Still just an {@link ApplicationException}
 * subclass, so {@code GlobalExceptionHandler}'s single {@code @ExceptionHandler
 * (ApplicationException.class)} catches these the same way as any other.
 */
public class NotFoundException extends ApplicationException {

    private static final HttpStatus STATUS_CODE = HttpStatus.NOT_FOUND;

    public NotFoundException(String message) {
        super(message, STATUS_CODE);
    }
}
