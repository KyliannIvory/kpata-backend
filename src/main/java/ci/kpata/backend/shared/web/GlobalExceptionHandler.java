package ci.kpata.backend.shared.web;

import ci.kpata.backend.shared.exception.ApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions raised while handling a request into the common
 * {@link ErrorResponseDto} JSON shape, instead of letting Spring Boot fall back to its
 * default (inconsistent, mostly-empty) error responses.
 * <p>
 * {@code @RestControllerAdvice} registers this class's {@code @ExceptionHandler} methods
 * globally, for every {@code @RestController} in the app. Spring only ever consults it
 * for exceptions raised <em>during {@code DispatcherServlet}'s handling of a controller
 * method</em> — an exception thrown earlier in the request, e.g. inside a {@code Filter}
 * such as {@code JwtFilter}, runs before {@code DispatcherServlet} even sees the request
 * and will never reach this class — see {@link ErrorResponseWriter} for how that case is
 * handled instead, and {@code docs/auth.md} §3 for the overall request flow.
 * <p>
 * Handlers below are matched from most to least specific: known business exceptions
 * ({@link ApplicationException}), known framework exceptions ({@code @Valid} failures,
 * malformed JSON), and finally {@link #handleUnexpectedException} as a last resort for
 * anything else — including bugs nobody anticipated. That last handler is what keeps
 * <em>every</em> error response, known or not, on the same {@link ErrorResponseDto}
 * contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Catches every {@link ApplicationException} subclass — Spring matches this handler
     * against the exception's actual runtime type and its supertypes, so a single method
     * here covers every current and future subclass without listing them individually.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponseDto> handleException(
        ApplicationException exception, HttpServletRequest request) {

        return build(
            exception.getStatus(),
            exception.getMessage(),
            request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleNotValidArgument(
        MethodArgumentNotValidException exception,
        HttpServletRequest request) {

        List<ErrorResponseDto.FieldErrorDto> errorDtos = new ArrayList<>();

        List<FieldError> fieldErrors = exception.getFieldErrors();

        for (FieldError fieldError : fieldErrors) {

            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            errorDtos.add(new ErrorResponseDto.FieldErrorDto(field, message));

        }

        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, errorDtos);
    }

    /**
     * Handles malformed JSON in an {@code @RequestBody} (e.g. syntactically invalid
     * JSON). Thrown while Spring resolves the controller method's arguments — still
     * inside {@code DispatcherServlet}'s handling, unlike the {@code JwtFilter} case
     * described in this class's Javadoc — so {@code @ExceptionHandler} can catch it here.
     * <p>
     * The exception is deliberately not taken as a parameter: {@link
     * HttpMessageNotReadableException#getMessage()} exposes internal JSON parser details
     * (character position, parser class name) that shouldn't reach the client, and there
     * is no specific field to report, so {@code fieldErrors} stays empty.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleNotReadableMessageException(
        HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST, "JSON parsing failed", request);
    }

    /**
     * Last-resort handler: catches anything not matched by a more specific handler above.
     * Without it, an unanticipated exception (e.g. a {@code NullPointerException}) would
     * propagate past this class and fall back to Spring Boot's default {@code /error}
     * handling, whose JSON body doesn't follow the {@link ErrorResponseDto} contract.
     * <p>
     * Since Spring Framework 6, several framework-thrown exceptions that already carry
     * their own correct HTTP status — e.g. {@code HttpRequestMethodNotSupportedException}
     * (405) or {@code NoResourceFoundException} (404) — implement {@link ErrorResponse}.
     * Reusing that status here means a legitimate 404/405 isn't misreported as a 500.
     * Anything that does <em>not</em> implement {@link ErrorResponse} is treated as a
     * genuinely unexpected bug: 500, a fixed generic message (never
     * {@code exception.getMessage()}, which could leak internal details to the client),
     * and the full exception logged server-side — this handler is the only place such a
     * bug will ever surface.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(
        Exception exception, HttpServletRequest request) {

        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, status.getReasonPhrase(), request);
        }

        log.error("Unexpected exception on {} {}",
            request.getMethod(), request.getRequestURI(), exception);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    /**
     * Builds the {@link ErrorResponseDto} body shared by every handler in this class,
     * with an empty {@code fieldErrors} list.
     */
    private ResponseEntity<ErrorResponseDto> build(
        HttpStatus status, String message, HttpServletRequest request) {

        return build(status, message, request, List.of());
    }

    /**
     * Builds the {@link ErrorResponseDto} body shared by every handler in this class.
     */
    private ResponseEntity<ErrorResponseDto> build(
        HttpStatus status, String message, HttpServletRequest request,
        List<ErrorResponseDto.FieldErrorDto> fieldErrors) {

        ErrorResponseDto body =
            new ErrorResponseDto(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors);

        return ResponseEntity.status(status).body(body);
    }

}
