package ci.kpata.backend.shared.web;

import java.time.Instant;
import java.util.List;

/**
 * The single JSON shape every error response returned by this API uses, built by
 * {@link GlobalExceptionHandler}. Having one contract regardless of the failure
 * (business exception, validation, ...) means a frontend client can parse every
 * error response the same way instead of handling a different shape per endpoint.
 *
 * @param timestamp when the error was produced
 * @param status    the HTTP status code (e.g. {@code 409}), duplicated from the
 *                   response's own status line so it's also readable from the body
 * @param error     the HTTP status's reason phrase (e.g. {@code "Conflict"})
 * @param message   human-readable description of what went wrong, safe to display —
 *                  never the raw message of an internal/technical exception
 * @param path      the request URI that produced the error ({@code request.getRequestURI()})
 * @param fieldErrors per-field validation errors; empty except when the failure is a
 *                   {@code @Valid} validation failure, in which case it holds one entry
 *                   per invalid field
 */
public record ErrorResponseDto(

    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldErrorDto> fieldErrors
) {
    /**
     * One invalid field from a {@code @Valid} validation failure.
     *
     * @param field   the name of the invalid field (e.g. {@code "phoneNumber"})
     * @param message why it's invalid (e.g. {@code "must not be blank"})
     */
    public record FieldErrorDto(String field, String message) {
    }
}
