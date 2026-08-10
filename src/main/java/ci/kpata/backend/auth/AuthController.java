package ci.kpata.backend.auth;

import ci.kpata.backend.auth.internal.dto.AuthResponseDto;
import ci.kpata.backend.auth.internal.dto.LoginRequestDto;
import ci.kpata.backend.auth.internal.dto.SignupRequestDto;
import ci.kpata.backend.auth.internal.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    // TODO(auth): exposer POST /auth/logout.
    // AuthService#logout(String) existe déjà et fonctionne, mais aucune route ne l'appelle.
    // Doit rester protégée (ne PAS l'ajouter à permitAll() dans JwtWebSecurityConfig) :
    // seul l'utilisateur authentifié doit pouvoir révoquer SON propre token.
    // Détail complet + squelette : docs/auth-error-handling.md, TODO 6.

    // TODO(auth): exposer GET /auth/me.
    // Doit retourner les infos de l'utilisateur courant (jamais le password !), à partir
    // du token présent sur la requête. Nécessaire pour que le frontend sache "qui est
    // connecté" après un refresh de page sans avoir à décoder le JWT côté client.
    // Concepts, options de conception et critères d'acceptation détaillés :
    // docs/auth-frontend-readiness.md, section 2.
}
