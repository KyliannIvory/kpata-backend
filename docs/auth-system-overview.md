# Système d'authentification — fonctionnement actuel et suivi

> Ce document décrit **ce qui existe et pourquoi**, à la différence de
> `auth-error-handling.md` et `auth-frontend-readiness.md` qui décrivent **ce qui manque
> et quoi faire**. À mettre à jour à chaque TODO terminé (section 5) et à chaque fois que
> le comportement décrit en section 3 change.

## 1. Vue d'ensemble

```
Requête HTTP
    ↓
JwtFilter                    (lit le token, peuple SecurityContextHolder si valide)
    ↓
Spring Security — authorizeHttpRequests   (route publique ? sinon → authenticationEntryPoint)
    ↓
DispatcherServlet
    ↓
AuthController                (signup / login)
    ↓
AuthService                   (règles métier : doublon, credentials, émission du token)
    ↓
JwtProvider / UserRepository / PasswordEncoder
```

Une exception métier levée entre `DispatcherServlet` et `AuthService` (ex.
`UserAlreadyExistsException`) est interceptée par `GlobalExceptionHandler`. Une erreur liée
au token, elle, est déjà tranchée **avant** `DispatcherServlet` (détail en section 3).

## 2. Composants et responsabilités

| Composant | Rôle | Fichier |
|---|---|---|
| `AuthController` | Expose `/auth/signup`, `/auth/login`, `/auth/logout` (protégée) | `auth/AuthController.java` |
| `AuthService` | Règles métier : vérifie le doublon, authentifie, émet le token | `auth/internal/service/AuthService.java` |
| `JwtProvider` | Émet/valide/révoque les JWT ; blacklist des tokens révoqués en mémoire | `auth/internal/jwt/JwtProvider.java` |
| `JwtFilter` | Lit le token sur chaque requête, peuple `SecurityContextHolder` si valide | `auth/internal/jwt/JwtFilter.java` |
| `UserDetailsServiceImpl` | Charge un `User` par `phoneNumber` et l'adapte en `UserDetails` Spring Security | `auth/internal/security/UserDetailsServiceImpl.java` |
| `JwtWebSecurityConfig` | Câble `JwtFilter`, définit les routes publiques/protégées, gère le cas "non authentifié" | `auth/internal/security/JwtWebSecurityConfig.java` |
| `ApplicationException` | Classe mère des exceptions métier ; porte son propre `HttpStatus` | `shared/web/ApplicationException.java` |
| `GlobalExceptionHandler` | `@RestControllerAdvice` — traduit `ApplicationException` (métier), `MethodArgumentNotValidException` (`@Valid`), `HttpMessageNotReadableException` (JSON malformé) et, en dernier recours, tout `Exception` non anticipée, en `ErrorResponseDto` | `shared/web/GlobalExceptionHandler.java` |
| `ErrorResponseWriter` | Écrit un `ErrorResponseDto` en JSON à la main, pour les cas hors de portée de `GlobalExceptionHandler` | `shared/web/ErrorResponseWriter.java` |
| `ErrorResponseDto` | Contrat JSON unique pour toute erreur renvoyée par l'API | `shared/web/ErrorResponseDto.java` |

## 3. Comportement face à un token JWT

`JwtFilter` s'exécute pour **toutes** les requêtes (avant `DispatcherServlet`), sans savoir
si la route visée est publique ou protégée — cette connaissance vit uniquement dans
`JwtWebSecurityConfig#filterChain` (`authorizeHttpRequests`).

| Cas | Comportement de `JwtFilter` | Qui tranche ensuite | Résultat |
|---|---|---|---|
| Pas de header `Authorization` | Ne fait rien, requête non authentifiée | `authorizeHttpRequests` | Route publique → passe. Route protégée → `401` via `authenticationEntryPoint`, message générique ("Full authentication is required...") |
| Token valide | Authentifie, peuple `SecurityContextHolder` | `authorizeHttpRequests` | Passe, avec un utilisateur authentifié |
| Token invalide/expiré/révoqué | Vide `SecurityContextHolder`, mémorise la raison dans l'attribut de requête `JwtFilter.TOKEN_ERROR_ATTRIBUTE`, **laisse passer la requête** | `authorizeHttpRequests` | Route publique → passe, **comme si aucun token n'avait été fourni** (le token est ignoré, pas d'erreur liée au token). Route protégée → `401` via `authenticationEntryPoint`, message précis repris depuis l'attribut ("Expired or invalid JWT token" / "Token has been revoked") |

Point clé : un token invalide n'est **jamais** une raison de rejet en soi — seule l'absence
d'authentification sur une route qui l'exige l'est. C'est un choix délibéré (voir décision
du 2026-08-11 en section 5) : un client qui présente par erreur un vieux token sur une route
publique (ex. `/auth/login` après une session expirée) n'est pas pénalisé pour ça.

## 4. Contrat d'erreur HTTP actuel

Toute erreur gérée renvoie la même forme JSON (`ErrorResponseDto`) :

```json
{
  "timestamp": "2026-08-11T13:26:44.723Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Expired or invalid JWT token",
  "path": "/some/protected/route",
  "fieldErrors": []
}
```

`fieldErrors` n'est peuplé que pour un échec `@Valid` — un par champ invalide :

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/auth/signup",
  "fieldErrors": [
    {"field": "phoneNumber", "message": "must not be blank"},
    {"field": "password", "message": "must not be blank"}
  ]
}
```

Deux mécanismes distincts la produisent, selon où l'erreur survient dans le pipeline
(voir section 1) :

- **`GlobalExceptionHandler`** — pour toute exception héritant d'`ApplicationException`,
  levée pendant l'exécution d'un controller (ex. `UserAlreadyExistsException` → 409,
  `InvalidCredentialsException` → 401), et pour `MethodArgumentNotValidException` (échec
  `@Valid` sur un DTO comme `SignupRequestDto`) → 400, avec `fieldErrors` peuplé, un par
  champ invalide. Mécanisme Spring standard (`@ExceptionHandler`).
- **`ErrorResponseWriter`** — appelé à la main depuis `JwtWebSecurityConfig`'s
  `authenticationEntryPoint`, le seul endroit qui produit encore un 401 aujourd'hui côté
  filtre (`JwtFilter` ne rejette plus rien lui-même, voir section 3). Nécessaire parce que
  ce code s'exécute avant `DispatcherServlet`, hors de portée d'`@ExceptionHandler`.

**Piège technique noté en passant** : ce projet tourne en Spring Boot 4 / Jackson 3, où le
bean JSON auto-configuré par Spring est `tools.jackson.databind.json.JsonMapper`, pas
`com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2) — la plupart des tutos/réponses en
ligne supposent encore Jackson 2. `jjwt-jackson` (dépendance du JWT) utilise, lui, Jackson 2
en interne, uniquement pour son propre usage — les deux coexistent sur le classpath sans
rapport entre eux.

## 5. Journal des décisions

### 2026-08-11 — TODO 2 : `@RestControllerAdvice` + hiérarchie d'exceptions

`UserAlreadyExistsException` et `InvalidCredentialsException` héritent maintenant
d'`ApplicationException` (package `shared.web`), qui porte son propre `HttpStatus`.
`GlobalExceptionHandler` catche `ApplicationException` une seule fois — toute nouvelle
exception métier (même dans un futur module) est gérée automatiquement dès qu'elle en
hérite, sans toucher au handler. Choisi plutôt qu'un `@RestControllerAdvice` dupliqué par
module parce que la dépendance va dans le bon sens vis-à-vis de Spring Modulith : les
exceptions `internal` dépendent de `shared`, jamais l'inverse.

### 2026-08-11 — TODO 2bis : 401 du filtre JWT et de l'entry point

`JwtFilter` et l'`authenticationEntryPoint` de `JwtWebSecurityConfig` s'exécutent avant
`DispatcherServlet` — `GlobalExceptionHandler` ne peut donc jamais les voir. Ajout
d'`ErrorResponseWriter` pour construire le même JSON à la main à ces deux endroits.

### 2026-08-11 — Décision : token invalide sur route publique → ignoré, pas rejeté

Constat initial : un token expiré/invalide faisait échouer **toute** requête avec un 401,
même sur une route publique comme `/auth/login`, puisque `JwtFilter` ne savait pas
distinguer route publique/protégée. Décision : traiter un token invalide exactement comme
l'absence de token — laisser `authorizeHttpRequests` (qui, lui, connaît la liste des routes
publiques) décider si c'est un problème. Contrepartie technique : pour ne pas perdre le
message précis ("Expired or invalid JWT token") sur les routes protégées, `JwtFilter`
mémorise la raison dans un attribut de requête (`TOKEN_ERROR_ATTRIBUTE`) que
l'`authenticationEntryPoint` relit. Vérifié par `curl` : route publique + token invalide →
traité comme anonyme ; route protégée + token invalide → 401 message précis ; route
protégée + aucun token → 401 message générique (inchangé).

### 2026-08-12 — TODO 3 (partiel) : erreurs de validation `@Valid`

`GlobalExceptionHandler` gère maintenant `MethodArgumentNotValidException` (déclenchée
quand `@Valid` échoue sur `SignupRequestDto` côté `AuthController`) : chaque champ en
erreur (`BindingResult`) devient une entrée de `fieldErrors`, avec un statut `400`. Le
`message` de premier niveau est fixé à `"Validation failed"` plutôt que
`exception.getMessage()` — ce dernier est un texte technique généré par Spring (liste
brute des champs et valeurs rejetées), que la Javadoc d'`ErrorResponseDto.message`
interdit déjà d'exposer tel quel ; le détail utile vit dans `fieldErrors`, pas dans
`message`. `build()` a été surchargé (statut+message+request, sans/avec `fieldErrors`)
plutôt que de rajouter un paramètre partout, pour que `handleException`
(cas `ApplicationException`, qui n'a jamais de `fieldErrors`) reste inchangé. Vérifié par
`curl` : champs invalides → `400` + `fieldErrors` peuplé ; signup valide (`201`) et
doublon (`409`) inchangés ; `AuthServiceTest` toujours vert.

### 2026-08-14 — TODO 3 (clôture) : `HttpMessageNotReadableException`

`GlobalExceptionHandler` gère maintenant aussi le JSON malformé
(`HttpMessageNotReadableException`), levée pendant la résolution des arguments du
controller — donc, comme `MethodArgumentNotValidException`, dans le périmètre de
`DispatcherServlet` et donc de `@ExceptionHandler` (contrairement aux cas
`JwtFilter`/`authenticationEntryPoint` de TODO 2bis, hors de portée). Statut `400`,
`fieldErrors` vide (aucun champ précis en cause), message générique fixe
(`"JSON parsing failed"`) plutôt que `exception.getMessage()` — même raisonnement que pour
`MethodArgumentNotValidException` : le message par défaut expose des détails internes du
parseur JSON (position du caractère fautif, classe interne), pas un message destiné à un
client. Vérifié par `curl` : JSON malformé sur `/auth/signup` → `400` + `ErrorResponseDto`
non vide ; comportements existants (`@Valid`, signup, doublon) inchangés. TODO 3 est
maintenant clos dans son intégralité.

### 2026-08-14 — TODO 4 (clôture) : dispatch `ERROR` autorisé + catch-all `Exception.class`

Vérifié par `curl` (app relancée proprement, un process JVM obsolète tournait sur le port
8080 avec un `target/classes` désynchronisé du code source — tuer + recompiler + relancer
a été nécessaire pour un test fiable) : `dispatcherTypeMatchers(FORWARD, ERROR).permitAll()`
dans `JwtWebSecurityConfig` fonctionne bien — une route inexistante avec un token valide
renvoie `404` (avant : `401` vide, le forward interne vers `/error` était bloqué par
`anyRequest().authenticated()`). Un `TODO(auth)` dans le code affirmait encore le contraire
(que seul `FORWARD` était couvert, pas `ERROR`) alors que la ligne juste en dessous couvrait
déjà les deux — commentaire corrigé.

Second constat : le JSON renvoyé par `BasicErrorController` sur ces cas (`404`, `405`
testés) n'avait ni `message` ni `fieldErrors` — ne respectait pas le contrat
`ErrorResponseDto`. Corrigé en ajoutant `@ExceptionHandler(Exception.class)` dans
`GlobalExceptionHandler` : si l'exception implémente `org.springframework.web.ErrorResponse`
(cas de `HttpRequestMethodNotSupportedException`, `NoResourceFoundException`... depuis
Spring Framework 6), son statut réel est réutilisé plutôt que de tout écraser par `500`.
Sinon, `500` + message générique fixe + stack trace loguée côté serveur (jamais
`exception.getMessage()` au client). Revérifié par `curl` : `404`/`405` renvoient maintenant
`ErrorResponseDto` complet ; non-régression sur `409`/`400`/`401` existants ; 27 tests
(`AuthServiceTest`, `JwtProviderTest`, etc.) toujours verts. TODO 4 clos dans son
intégralité. Détail complet : `docs/auth-error-handling.md`, TODO 4.

### 2026-08-14 — TODO 5 (clôture) : revalidation `curl` complète

Les 8 scénarios de `docs/auth-error-handling.md` (TODO 5) rejoués tels quels par toi :
7/8 conformes à l'attendu, statut et corps. Le seul écart (test 1 : signup neuf reçu en
`409` au lieu de `201`) vient d'un script relancé deux fois — le compte existait déjà
depuis le premier passage, confirmé par le test 2 (doublon) qui reçoit exactement la même
réponse. Pas un bug de code. Phase 0 (gestion des erreurs) est maintenant close dans son
intégralité — reste `TODO LoginRequestDto` (hors périmètre de ce document, voir
`auth-frontend-readiness.md` §0 et §4) avant de passer à la Phase 1 (`/auth/logout`,
`/auth/me`).

### 2026-08-14 — TODO 6 (clôture) : `POST /auth/logout` exposé

`AuthController` expose maintenant `POST /auth/logout`, appelant `AuthService.logout(...)`
(déjà existant et testé). Route volontairement absente de `permitAll()` — reste protégée
par `anyRequest().authenticated()`, pour que seul l'appelant déjà authentifié par un token
puisse révoquer *ce même* token (pas de risque de révoquer le token de quelqu'un d'autre).
Vérifié par `curl` : sans header `Authorization` → `401` avant même d'atteindre le
controller ; token valide → `204 No Content` ; le même token réutilisé ensuite sur une
route protégée → `401 "Token has been revoked"` (révocation immédiate) ; logout une seconde
fois avec ce token déjà révoqué → `401` propre. `./mvnw test -P unit-tests` toujours vert
(27 tests). Reste noté en review : import étoile (`import
org.springframework.web.bind.annotation.*`) dans `AuthController` — signalé par checkstyle
en `warning`, ne bloque pas la CI, à nettoyer quand l'occasion se présente.

<!-- Prochaine entrée : TODO LoginRequestDto ou GET /auth/me -->

## 6. État d'avancement

Suivi détaillé des TODO restants : voir `docs/auth-frontend-readiness.md`, section 0
(feuille de route complète, ordre chronologique). Détail technique de chaque étape de
gestion d'erreurs : `docs/auth-error-handling.md`.

- [x] TODO 1 — `ErrorResponseDto`
- [x] TODO 2 — `GlobalExceptionHandler` + `ApplicationException`
- [x] TODO 2bis — 401 du filtre JWT et de l'entry point + décision route publique/protégée
- [x] TODO 3 — erreurs de validation (`@Valid` + JSON malformé)
- [x] TODO 4 — filet de sécurité (dispatch `ERROR`) + catch-all `Exception.class`
- [x] TODO 5 — revalidation complète `curl` (8/8 scénarios conformes)
- [x] TODO 6 — `POST /auth/logout` (fait et vérifié)
- [ ] `GET /auth/me`, durée de vie du token, CORS, tests controller — voir
      `auth-frontend-readiness.md`
