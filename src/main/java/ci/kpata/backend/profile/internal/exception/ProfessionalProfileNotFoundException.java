package ci.kpata.backend.profile.internal.exception;

import ci.kpata.backend.shared.exception.NotFoundException;

/**
 * Thrown when a user has no professional profile (e.g. a customer-only account). Carries
 * its shared {@code 404} via {@link NotFoundException}, mapped to a response automatically.
 */
public class ProfessionalProfileNotFoundException extends NotFoundException {

    public ProfessionalProfileNotFoundException(String message) {
        super(message);
    }
}
