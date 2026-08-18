package ci.kpata.backend.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an {@link ErrorResponseDto} JSON body directly onto an {@link HttpServletResponse}.
 * <p>
 * {@link GlobalExceptionHandler} can only translate exceptions raised while
 * {@code DispatcherServlet} is handling a controller method — it never sees exceptions
 * raised earlier in the request, inside a Servlet {@code Filter} (e.g. {@code JwtFilter})
 * or Spring Security's {@code authenticationEntryPoint}, both of which run before
 * {@code DispatcherServlet} even starts. Those call sites can't rely on
 * {@code @ExceptionHandler} to produce the shared {@link ErrorResponseDto} contract, so
 * they call this component instead to build the same JSON shape by hand.
 */
@Component
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public ErrorResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Sets the response's status and writes an {@link ErrorResponseDto} body for it.
     * {@code fieldErrors} is always empty — this path never carries per-field validation
     * detail.
     *
     * @param response the response to write to; must not have been committed yet
     * @param status   the HTTP status to respond with
     * @param message  human-readable description of what went wrong
     * @param path     the request URI that produced the error
     */
    public void write(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponseDto body = new ErrorResponseDto(
                Instant.now(), status.value(), status.getReasonPhrase(), message, path, List.of());

        jsonMapper.writeValue(response.getWriter(), body);
    }
}
