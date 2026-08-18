package ci.kpata.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest request;

    @Test
    void handleNotReadableMessageException_returns400WithGenericMessage() {

        when(request.getRequestURI()).thenReturn("/auth/signup");

        ResponseEntity<ErrorResponseDto> response =
            handler.handleNotReadableMessageException(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("JSON parsing failed");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void handleUnexpectedException_withFrameworkErrorResponse_reusesItsStatus() {

        when(request.getRequestURI()).thenReturn("/auth/signup");

        // HttpRequestMethodNotSupportedException already carries its own correct status
        // (405) via ErrorResponse — this must not be downgraded to a generic 500.
        var exception = new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<ErrorResponseDto> response =
            handler.handleUnexpectedException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().message()).isEqualTo("Method Not Allowed");
    }

    @Test
    void handleUnexpectedException_withUnknownException_returns500WithGenericMessage() {

        when(request.getRequestURI()).thenReturn("/auth/signup");
        when(request.getMethod()).thenReturn("POST");

        ResponseEntity<ErrorResponseDto> response =
            handler.handleUnexpectedException(new RuntimeException("db connection leaked"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // Never the raw exception message: it could leak internal details to the client.
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
