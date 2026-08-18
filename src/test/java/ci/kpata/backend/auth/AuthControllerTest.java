package ci.kpata.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.dto.AuthResponseDto;
import ci.kpata.backend.auth.internal.dto.LoginRequestDto;
import ci.kpata.backend.auth.internal.dto.UserClaimsDto;
import ci.kpata.backend.auth.internal.dto.UserDto;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.auth.internal.security.JwtWebSecurityConfig;
import ci.kpata.backend.auth.internal.service.AuthService;
import ci.kpata.backend.auth.internal.exception.InvalidCredentialsException;
import ci.kpata.backend.auth.internal.exception.UserNotFoundException;
import ci.kpata.backend.shared.web.ErrorResponseWriter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the controller through the real security filter chain ({@link
 * JwtWebSecurityConfig} is explicitly imported, since {@code @WebMvcTest} doesn't scan
 * plain {@code @Configuration} classes), with {@link AuthService} mocked out — business
 * rules are {@code AuthServiceTest}'s job, this class checks HTTP status codes,
 * {@code @Valid} enforcement and public-vs-protected routing instead.
 * <p>
 * {@link JwtProvider} is a real bean here (not mocked): protected-route tests generate an
 * actual signed token with it and send it as a real {@code Bearer} header, so requests go
 * through the real {@code JwtFilter} — the same path a real client hits — instead of
 * faking authentication with {@code @WithMockUser}, which would bypass {@code JwtFilter}
 * entirely and hide bugs in how the token is read.
 */
@WebMvcTest(AuthController.class)
@Import({JwtWebSecurityConfig.class, JwtProvider.class, ErrorResponseWriter.class})
class AuthControllerTest {

    private static final String PHONE_NUMBER = "+2250700000000";
    private static final String PASSWORD = "s3cret-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AuthService authService;

    private String validBearerToken() {
        String token = jwtProvider.createToken(
                new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER)));
        return "Bearer " + token;
    }

    @Test
    void signup_withValidPayload_returns201WithToken() throws Exception {

        when(authService.signup(any())).thenReturn(new AuthResponseDto("signed-token"));

        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "Jane",
                                  "lastname": "Doe",
                                  "phoneNumber": "%s",
                                  "password": "%s",
                                  "email": "jane.doe@example.com"
                                }
                                """.formatted(PHONE_NUMBER, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("signed-token"));
    }

    @Test
    void signup_withBlankFields_returns400AndNeverCallsService() throws Exception {

        mvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "",
                                  "lastname": "",
                                  "phoneNumber": "",
                                  "password": "",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verifyNoInteractions(authService);
    }

    @Test
    void login_withValidCredentials_returns200WithToken() throws Exception {

        when(authService.login(any())).thenReturn(new AuthResponseDto("signed-token"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber": "%s", "password": "%s"}
                                """.formatted(PHONE_NUMBER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-token"));
    }

    /**
     * Confirms {@link LoginRequestDto}'s {@code @NotBlank}/{@code @Size} constraints
     * (docs/auth.md §5) actually reject an empty login before {@code AuthService} runs,
     * instead of falling through to a generic "Invalid credentials" 401.
     */
    @Test
    void login_withBlankFields_returns400AndNeverCallsService() throws Exception {

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber": "", "password": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verifyNoInteractions(authService);
    }

    @Test
    void login_withWrongCredentials_returns401WithErrorContract() throws Exception {

        when(authService.login(any()))
                .thenThrow(new InvalidCredentialsException("Invalid credentials", null));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber": "%s", "password": "%s"}
                                """.formatted(PHONE_NUMBER, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void logout_withInvalidToken_returns401AndNeverCallsService() throws Exception {

        mvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer some-garbage-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void logout_withValidToken_returns204() throws Exception {

        mvc.perform(post("/auth/logout")
                        .header("Authorization", validBearerToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void whoAmI_withoutToken_returns401() throws Exception {

        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void whoAmI_withValidToken_returns200WithUserDto() throws Exception {

        UserDto userDto = new UserDto(
                "Jane", "Doe", PHONE_NUMBER, "jane.doe@example.com", Set.of(Role.CUSTOMER));
        when(authService.findUserByPhoneNumber(PHONE_NUMBER)).thenReturn(userDto);

        mvc.perform(get("/auth/me")
                        .header("Authorization", validBearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(PHONE_NUMBER));
    }

    @Test
    void whoAmI_whenAccountNoLongerExists_returns404() throws Exception {

        when(authService.findUserByPhoneNumber(PHONE_NUMBER))
                .thenThrow(new UserNotFoundException("user not found"));

        mvc.perform(get("/auth/me")
                        .header("Authorization", validBearerToken()))
                .andExpect(status().isNotFound());
    }
}
