# Module `auth`

Doc unique de suivi du module. Le détail "pourquoi" des décisions déjà prises vit dans
l'historique git (`git log`) et la Javadoc du code concerné — ce fichier ne garde que ce
qui reste utile pour avancer.

## 1. Flux

```
Requête HTTP
    ↓
JwtFilter                    (lit le token, peuple SecurityContextHolder si valide)
    ↓
authorizeHttpRequests        (route publique ? sinon → authenticationEntryPoint)
    ↓
DispatcherServlet
    ↓
AuthController                (signup / login / logout / me)
    ↓
AuthService                   (règles métier)
    ↓
JwtProvider / UserRepository / PasswordEncoder / UserMapper
```

Erreur métier (ex. `UserAlreadyExistsException`) → `GlobalExceptionHandler`. Erreur liée au
token ou route non trouvée → déjà tranchée avant `DispatcherServlet` ou via le catch-all
`Exception.class` de `GlobalExceptionHandler` — détail dans la Javadoc de cette classe.

## 2. Composants

| Composant | Rôle |
|---|---|
| `AuthController` | `/auth/signup`, `/auth/login`, `/auth/logout` (protégée), `/auth/me` (protégée) |
| `AuthService` | Règles métier ; émet/révoque le token, mappe `User` → `UserDto` |
| `UserMapper` | MapStruct, `User` → `UserDto` uniquement (pas de sens inverse, voir §4) |
| `JwtProvider` | Émet/valide/révoque les JWT ; blacklist en mémoire (TODO Redis si scaling) |
| `JwtFilter` | Lit le token par requête ; un token invalide est ignoré, pas rejeté (voir §3) |
| `JwtWebSecurityConfig` | Routes publiques/protégées, dispatch `ERROR` autorisé (pas de CORS, voir §6) |
| `ApplicationException` / `GlobalExceptionHandler` | Contrat d'erreur unique, voir §4 |

## 3. Comportement face à un token JWT

`JwtFilter` ne rejette jamais une requête lui-même : un token absent, invalide, expiré ou
révoqué est traité pareil — la requête continue sans authentification. C'est
`authorizeHttpRequests` qui décide ensuite : route publique → passe (même un vieux token
fourni par erreur, ex. sur `/auth/login`, est ignoré) ; route protégée → `401`, avec le
message précis repris depuis l'attribut `JwtFilter.TOKEN_ERROR_ATTRIBUTE` s'il y en a un.

## 4. Contrat d'erreur

Toute erreur gérée (métier, validation, JSON malformé, 404/405, 500 imprévu) renvoie la
même forme :

```json
{
  "timestamp": "2026-08-14T13:26:44.723Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Expired or invalid JWT token",
  "path": "/some/protected/route",
  "fieldErrors": []
}
```

`fieldErrors` n'est peuplé que pour un échec `@Valid`. Détail des mécanismes (avant vs
après `DispatcherServlet`) : Javadoc de `GlobalExceptionHandler` et `ErrorResponseWriter`.

## 5. État d'avancement

- [x] Contrat d'erreur unique + `GlobalExceptionHandler` (métier, `@Valid`, JSON malformé, catch-all)
- [x] 401 structurés côté filtre (avant `DispatcherServlet`)
- [x] Dispatch `ERROR` autorisé (404/405 ne redeviennent plus des 401 vides)
- [x] `POST /auth/logout`
- [x] `GET /auth/me`
- [x] Valider `LoginRequestDto` (`@NotBlank` sur `phoneNumber`/`password`, comme `SignupRequestDto`)
- [x] Tests `AuthController` + config de sécurité (`AuthControllerTest`, `@WebMvcTest` + vrai `JwtWebSecurityConfig`)

## 6. Décisions et TODO ouverts

### 6.1 Par où commencer (prêt, pas bloqué par autre chose)

1. **`java.util.Date` → `java.time.Instant` dans `JwtProvider`** (exigence Sonar) : TODO posé
   dans la Javadoc de la classe, avec le détail (utiliser `Instant`, pas `LocalDateTime` —
   raison expliquée sur place — et convertir aux frontières puisque JJWT 0.13.0 impose encore
   `Date` sur `expiration()`/`issuedAt()`/`getExpiration()`).

### 6.2 Bloqué par autre chose (rien à faire avant que le déclencheur arrive)

- **Routes placeholder `/salons/**`, `/treatments/**`, `/availabilities/**`**
  (`JwtWebSecurityConfig`) : à ajuster seulement quand les controllers salon/treatment/
  availability existeront, pour matcher leurs vraies routes de browse/recherche.
- **Blacklist de tokens révoqués en mémoire** — perdue au redémarrage, non partagée entre
  instances. Non pertinent tant que l'app tourne en une seule instance ; à revoir si scaling
  horizontal (TODO Redis posé dans `JwtProvider`).

### 6.3 Décidé (pour référence, rien à faire)

- **Email dupliqué au signup → 500 au lieu d'un 409 propre** — corrigé le 2026-08-18 :
  `UserRepository.existsByEmailIgnoreCase` ajouté, vérifié dans `AuthService.validateSignup`
  (email `null`/vide ignoré, comme le choix assumé de le rendre optionnel). `signup` traite en
  plus `DataIntegrityViolationException` comme filet de sécurité pour une race condition entre
  la vérification et l'insertion.
- **`phoneNumber` jamais normalisé** — corrigé le 2026-08-18 : `PhoneNumberNormalizer`
  (E.164 via `PhoneNumberUtil`, région `CI`) appelé une seule fois en tête de
  `AuthService.login`/`signup`, et la valeur normalisée réutilisée partout ensuite —
  vérification d'unicité, entité sauvegardée, claims du JWT — pour qu'un même numéro envoyé
  sous deux formats désigne toujours le même compte.
- **Principal `Authentication` incohérent** : `AuthService.login` porte un `UserDetails`,
  `JwtProvider#getAuthentication` (donc toute route protégée) porte un `String`. Décision
  du 2026-08-17 : laissé tel quel. Ne pas caster en `UserDetails` ailleurs que dans `login`.
  Détail : Javadoc de `JwtProvider#getAuthentication`.
- ~~CORS~~ — décidé et appliqué le 2026-08-17 : le frontend (Next.js App Router) proxifie
  systématiquement toutes les requêtes vers l'API via son serveur (Server Actions/Server
  Components), jamais depuis du JS exécuté dans le navigateur — CORS n'est appliqué que par
  les navigateurs, donc jamais exercé ici. Entièrement supprimé (pas juste restreint) : bean
  `corsConfigurationSource`, `http.cors(...)`, champ `allowedOrigins`, propriété
  `app.cors.allowed-origins`. Le supprimer plutôt que le garder restreint sert de garde-fou :
  si du code frontend appelle l'API en direct depuis le navigateur par erreur, l'absence de
  config CORS fait échouer la requête au lieu de la laisser passer. Pour la même raison, CSRF
  reste désactivé (`http.csrf(...disable)`) — l'API n'authentifie jamais via cookie posé par
  Spring Boot, seulement via header `Authorization` construit par du code serveur de confiance,
  donc rien que CSRF protégerait.
- ~~Token 5 min, pas de refresh~~ — décidé le 2026-08-17 : expiration augmentée pragmatiquement à
  1h (`application.yaml`, `jwt.expiration`). Pas de refresh token pour l'instant ; à revisiter si
  1h s'avère trop court à l'usage.

## 7. Vérification rapide (`curl`)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"phoneNumber":"...","password":"..."}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

curl -i http://localhost:8080/auth/me -H "Authorization: Bearer $TOKEN"        # 200 + UserDto sans password
curl -i http://localhost:8080/auth/me                                          # 401
curl -i -X POST http://localhost:8080/auth/logout -H "Authorization: Bearer $TOKEN"  # 204
curl -i http://localhost:8080/auth/me -H "Authorization: Bearer $TOKEN"        # 401, token révoqué
```
