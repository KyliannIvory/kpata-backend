package ci.kpata.backend.auth;

import ci.kpata.backend.auth.internal.dto.AuthResponseDto;
import ci.kpata.backend.auth.internal.dto.LoginRequestDto;
import ci.kpata.backend.auth.internal.dto.SignupRequestDto;
import ci.kpata.backend.auth.internal.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // TODO(auth): exposer GET /auth/me.
    // Doit retourner les infos de l'utilisateur courant (jamais le password !), à partir
    // du token présent sur la requête. Nécessaire pour que le frontend sache "qui est
    // connecté" après un refresh de page sans avoir à décoder le JWT côté client.
    // Concepts, options de conception et critères d'acceptation détaillés :
    // docs/auth-frontend-readiness.md, section 2.
}
