package ci.kpata.backend.auth;

import ci.kpata.backend.auth.internal.dto.AuthResponseDto;
import ci.kpata.backend.auth.internal.dto.LoginRequestDto;
import ci.kpata.backend.auth.internal.dto.SignupRequestDto;
import ci.kpata.backend.auth.internal.dto.UserDto;
import ci.kpata.backend.auth.internal.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponseDto> signup(@RequestBody @Valid SignupRequestDto dto) {

        AuthResponseDto response = service.signup(dto);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody @Valid LoginRequestDto dto) {

        AuthResponseDto response = service.login(dto);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationValue) {

        service.logout(authorizationValue);

        return ResponseEntity
            .noContent()
            .build();
    }

    /**
     * {@code principal.getName()} is the JWT subject (phone number) regardless of whether
     * the underlying {@code Authentication}'s principal is a {@code String} or a {@code
     * UserDetails} — see {@code JwtProvider#getAuthentication}'s Javadoc for why that
     * distinction matters and why casting the principal here would be a mistake.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> whoAmI(Principal principal) {

        String phoneNumber = principal.getName();
        UserDto userDto = service.findUserByPhoneNumber(phoneNumber);

        return ResponseEntity.ok(userDto);
    }
}
