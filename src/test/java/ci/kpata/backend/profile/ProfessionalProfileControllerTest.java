package ci.kpata.backend.profile;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ci.kpata.backend.auth.internal.domain.Role;
import ci.kpata.backend.auth.internal.dto.UserClaimsDto;
import ci.kpata.backend.auth.internal.jwt.JwtProvider;
import ci.kpata.backend.auth.internal.security.JwtWebSecurityConfig;
import ci.kpata.backend.profile.internal.domain.SalonRole;
import ci.kpata.backend.profile.internal.dto.ProfessionalProfileDto;
import ci.kpata.backend.profile.internal.exception.ProfessionalProfileNotFoundException;
import ci.kpata.backend.profile.internal.service.ProfessionalProfileService;
import ci.kpata.backend.shared.web.ErrorResponseWriter;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfessionalProfileController.class)
@Import({JwtWebSecurityConfig.class, JwtProvider.class, ErrorResponseWriter.class})
class ProfessionalProfileControllerTest {

    private static final String PHONE_NUMBER = "+2250700000000";
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ProfessionalProfileService service;

    private String validBearerToken() {
        String token = jwtProvider.createToken(
                new UserClaimsDto(PHONE_NUMBER, Set.of(Role.CUSTOMER)));
        return "Bearer " + token;
    }

    @Test
    void getProfessional_withoutToken_returns200WithDto() throws Exception {

        ProfessionalProfileDto dto = new ProfessionalProfileDto(
                UUID.randomUUID(), "Hair stylist, five years of experience", SalonRole.EMPLOYEE);
        when(service.getProInformationByUserId(USER_ID)).thenReturn(dto);

        mvc.perform(get("/professionals/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salonRole").value("EMPLOYEE"));
    }

    @Test
    void getProfessional_withValidTokenAndExistingProfile_returns200WithDto() throws Exception {

        ProfessionalProfileDto dto = new ProfessionalProfileDto(
                UUID.randomUUID(), "Hair stylist, five years of experience", SalonRole.EMPLOYEE);
        when(service.getProInformationByUserId(USER_ID)).thenReturn(dto);

        mvc.perform(get("/professionals/{userId}", USER_ID)
                        .header("Authorization", validBearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salonRole").value("EMPLOYEE"));
    }

    @Test
    void getProfessional_whenProfileNotFound_returns404() throws Exception {

        when(service.getProInformationByUserId(USER_ID))
                .thenThrow(new ProfessionalProfileNotFoundException("Doesn't have a professional profile"));

        mvc.perform(get("/professionals/{userId}", USER_ID)
                        .header("Authorization", validBearerToken()))
                .andExpect(status().isNotFound());
    }
}
