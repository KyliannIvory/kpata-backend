package ci.kpata.backend.shared.exception;

import ci.kpata.backend.shared.web.ErrorResponseDto;
import ci.kpata.backend.shared.web.GlobalExceptionHandler;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for exceptions that represent a known, expected failure with a
 * well-defined HTTP status — as opposed to an unexpected bug, which stays a
 * plain {@link RuntimeException} and falls back to a generic 500.
 * <p>
 * Each subclass names a specific failure (e.g. {@code UserAlreadyExistsException})
 * and fixes its own {@link HttpStatus} by passing it to the constructor. This lets
 * {@link GlobalExceptionHandler} map <em>any</em> such exception to a response with
 * a single {@code @ExceptionHandler(ApplicationException.class)}: Spring matches
 * handlers against the exception's actual type and its supertypes, so catching this
 * base type is enough to catch every subclass, present or future, without
 * {@code GlobalExceptionHandler} needing to know each one by name.
 * <p>
 * This also keeps module boundaries clean: a subclass like
 * {@code UserAlreadyExistsException} can live in an {@code internal} package (hidden
 * from other modules by Spring Modulith) and still depend on this shared type — the
 * dependency points from the module into {@code shared}, never the other way around,
 * so {@code shared} never needs to import a module's internal classes.
 * <p>
 * Never thrown directly (hence {@code abstract} and a {@code protected} constructor):
 * always throw a specific subclass so the failure is self-documenting at the call site.
 */
@Getter
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;

    /**
     * @param message human-readable description, suitable for exposing to API callers
     *                (see {@link ErrorResponseDto#message()}) — keep it free of internal
     *                details a caller shouldn't see
     * @param status  the HTTP status {@link GlobalExceptionHandler} will respond with
     * @param cause   the lower-level exception this one wraps, if any (e.g. a Spring
     *                Security {@code AuthenticationException}), so it isn't lost from
     *                the stack trace even though it never reaches the HTTP response
     */
    protected ApplicationException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    protected ApplicationException(String message , HttpStatus status){
        super(message);
        this.status = status;
    }
}
