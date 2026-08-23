package ci.kpata.backend.profile.internal.exception;

import ci.kpata.backend.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a user has no professional profile (e.g. a customer-only account). Carries
 * its own {@link HttpStatus#NOT_FOUND} via {@link ApplicationException}, mapped to a
 * {@code 404} response automatically.
 */
public class ProfessionalProfileNotFoundException extends ApplicationException {

    private static final HttpStatus STATUS_CODE = HttpStatus.NOT_FOUND;

    public ProfessionalProfileNotFoundException(String message) {
        super(message, STATUS_CODE);
    }
}
