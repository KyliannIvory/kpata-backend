# Gestion des erreurs du module `auth` — diagnostic et plan d'action

> Document pédagogique. Objectif : que tu comprennes **le problème, sa cause racine,
> et pourquoi chaque étape du correctif est nécessaire**, pour l'implémenter toi-même.
> Rien ici n'est un correctif appliqué — c'est un guide.
>
> Pour savoir **dans quel ordre** traiter les TODO 1 à 6 ci-dessous par rapport aux
> autres TODO du projet (code + `docs/auth-frontend-readiness.md`), voir la feuille de
> route unique en tête de `docs/auth-frontend-readiness.md`, section 0. Ce document-ci
> reste la référence pour le *détail* de chaque étape de gestion d'erreurs.

## 1. Le problème observé

En testant l'API avec `curl` (app lancée en local, Postgres via `docker-compose`), voici ce
qui a été constaté :

| Requête | Statut attendu | Statut réel obtenu | Corps |
|---|---|---|---|
| Signup avec numéro déjà utilisé | `409 Conflict` | **`401`** | vide |
| Login avec mauvais mot de passe | `401 Unauthorized` | `401` (correct, mais par accident — voir §2) | vide |
| Signup avec champs invalides (`@Valid` échoue) | `400 Bad Request` | **`401`** | vide |
| Signup avec JSON malformé | `400 Bad Request` | **`401`** | vide |
| Route protégée sans token | `401 Unauthorized` | `401` (correct) | vide |
| Route inexistante, **avec un token valide** | `404 Not Found` | **`401`** | vide |

Le point commun : **dès qu'une erreur survient — peu importe sa nature réelle — la
réponse finale est un `401` avec un corps vide.** Même le cas "login échoué", qui *semble*
correct, ne l'est que par coïncidence : c'est censé être un `401`, mais pas pour la bonne
raison, et pas avec un corps exploitable.

### Pourquoi c'est bloquant pour le frontend

Sans corps de réponse et sans statut fiable, le frontend ne peut pas :
- afficher "ce numéro est déjà utilisé" vs "mot de passe incorrect" (deux messages UX
  très différents, un seul code HTTP)
- afficher les erreurs de validation par champ (ex. souligner le champ `email` en rouge)
- distinguer une vraie erreur serveur (bug backend) d'un problème d'authentification côté
  client (token expiré)

Il faut donc corriger ça **avant** d'écrire la couche d'appel API / gestion d'erreurs du
frontend, sinon tu devras tout reprendre une fois le backend corrigé.

## 2. Cause racine

Deux problèmes distincts se cumulent. Le premier est la cause du symptôme "tout devient
401" ; le second est ce qui fait qu'il n'y a *rien de propre* à afficher même une fois le
premier corrigé.

### 2.1 Le dispatch `/error` n'est pas autorisé par Spring Security

Regarde `JwtWebSecurityConfig.filterChain(...)` :

```java
config
    .dispatcherTypeMatchers(DispatcherType.FORWARD)
    .permitAll();
```

Quand une exception non interceptée remonte jusqu'à Spring Boot (une `RuntimeException`
métier, une erreur de validation, une route introuvable...), Spring Boot ne construit
pas la réponse d'erreur directement : il fait un **forward interne vers `/error`**, gérée
par `BasicErrorController`. Ce forward interne est de type `DispatcherType.ERROR` — **pas**
`FORWARD`. Deux types de dispatch différents dans la servlet API.

Résultat : la règle ci-dessus ne s'applique pas à ce forward. Ce dernier retombe donc sur
la règle générale `anyRequest().authenticated()`. Comme ce forward interne n'a pas de
header `Authorization`, Spring Security considère l'utilisateur comme non authentifié et
déclenche le point d'entrée custom défini plus haut dans le même fichier :

```java
http.exceptionHandling(exceptionHandling ->
        exceptionHandling.authenticationEntryPoint(
                (request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage())));
```

Ce `sendError(401, ...)` **écrase** le vrai statut que Spring avait déjà calculé (409, 400,
404...) et produit une réponse vide, parce que `authException.getMessage()` est ici `null`
(c'est une authentification manquante générique, pas un vrai échec de credentials).

C'est un piège classique : la règle `dispatcherTypeMatchers(FORWARD)` a été écrite avec
la bonne intention ("laisser passer les dispatches internes de Spring Boot"), mais elle
vise le mauvais type de dispatch.

### 2.2 Aucun `@ControllerAdvice` ne mappe les exceptions métier

Même en réparant le point 2.1, tu n'obtiendras que le comportement **par défaut** de
Spring Boot pour chaque exception :
- `MethodArgumentNotValidException` (échec `@Valid`) → 400, avec un corps générique
  Spring (pas forcément le format que tu veux exposer au frontend)
- `UserAlreadyExistsException`, `InvalidCredentialsException`, `InvalidTokenException` →
  ces exceptions custom n'ont **aucune annotation `@ResponseStatus`** et ne sont
  interceptées nulle part → Spring les traite comme des erreurs inattendues → **500**,
  alors que la Javadoc de ces classes dit explicitement qu'elles devraient devenir
  409/401/401 (regarde les commentaires dans
  `auth/internal/service/UserAlreadyExistsException.java` et
  `auth/internal/service/InvalidCredentialsException.java` — le code a été écrit en
  anticipant ce mapping, mais personne ne l'a câblé).

Donc : réparer 2.1 seul ne suffit pas à obtenir les bons codes de statut. Il faut aussi
2.2 pour que chaque exception métier soit traduite explicitement.

## 3. Plan d'action — TODOs, dans l'ordre

Coche-les au fur et à mesure. L'ordre est important : chaque étape est plus facile à
vérifier si la précédente est en place.

- [x] **TODO 1 — Définir un format de réponse d'erreur commun**
- [x] **TODO 2 — Créer un `@RestControllerAdvice` global qui mappe les exceptions métier**
- [x] **TODO 2bis — Cas particulier : les 401 levés par le filtre JWT et le point d'entrée
      d'authentification n'atteignent jamais le `@RestControllerAdvice`**
- [x] **TODO 3 — Gérer proprement les erreurs de validation (`@Valid`)** — `@Valid` et
      JSON malformé (`HttpMessageNotReadableException`) faits
- [x] **TODO 4 — Corriger le filtre de sécurité en filet de sécurité (dispatch `ERROR`)**
- [x] **TODO 5 — Revalider tous les scénarios avec `curl`**
- [x] **TODO 6 (bonus, hors périmètre du bug) — Exposer `/auth/logout`**

---

### TODO 1 — Définir un format de réponse d'erreur commun

**Pourquoi en premier ?** Parce que TODO 2 et TODO 3 vont tous les deux construire ce
même objet. Si tu improvises le format exception par exception, tu vas te retrouver avec
des formes différentes selon le type d'erreur, et le frontend devra gérer plusieurs
formats. Un seul contrat, partout.

**Quoi faire :** un DTO (record) dans `shared`, par exemple
`ci.kpata.backend.shared.web.ErrorResponseDto`, avec au minimum :

```java
public record ErrorResponseDto(
        Instant timestamp,
        int status,
        String error,       // libellé HTTP, ex. "Conflict"
        String message,     // message lisible pour l'utilisateur/le dev
        String path,        // request.getRequestURI()
        List<FieldErrorDto> fieldErrors // vide sauf en cas d'erreur de validation
) {
    public record FieldErrorDto(String field, String message) {}
}
```

C'est un exemple de forme, pas une obligation — l'important est que **tu choisisses une
forme unique et que tu la documentes**, car c'est ce contrat que le frontend va coder en
dur dans son client HTTP (ex. un `try/catch` générique qui lit toujours `error.message`).

---

### TODO 2 — Créer le `@RestControllerAdvice` global ✅ fait, autrement que prévu ci-dessous

**Ce qui a réellement été construit** diffère du squelette illustratif plus bas (gardé pour
sa valeur pédagogique — il montre l'approche naïve avant optimisation) : au lieu d'un
`@ExceptionHandler` par exception, `UserAlreadyExistsException` et
`InvalidCredentialsException` héritent maintenant d'une classe mère commune,
`ci.kpata.backend.shared.web.ApplicationException`, qui porte son propre `HttpStatus`.
`GlobalExceptionHandler` n'a plus qu'**un seul** `@ExceptionHandler(ApplicationException.class)`,
qui lit `exception.getStatus()` — ça couvre automatiquement toute exception présente et
future qui hérite de cette classe, sans toucher au handler à chaque ajout. Ça règle aussi,
de fait, le "point d'attention" Modulith ci-dessous : la dépendance va de
`auth.internal.service` vers `shared.web`, jamais l'inverse, donc `GlobalExceptionHandler`
n'a jamais besoin d'importer une classe `internal` d'un module. Détail complet : voir
`ApplicationException.java` et `docs/auth-system-overview.md`, §5 (journal des décisions).

**Pourquoi :** c'est le mécanisme Spring standard pour intercepter une exception
*avant* qu'elle ne devienne une erreur non gérée qui force Spring Boot à faire son forward
interne vers `/error` (le point sensible du §2.1). Un `@ExceptionHandler` qui matche
l'exception court-circuite complètement ce chemin : plus besoin que le forward `/error`
fonctionne pour ces cas précis.

**Où :** par exemple `ci.kpata.backend.shared.web.GlobalExceptionHandler` (package
`shared`, puisque ça concerne toute l'app, pas seulement `auth` — même si aujourd'hui
seules des exceptions du module `auth` existent).

**Quoi mapper, avec le code HTTP indiqué par la Javadoc déjà écrite dans le code :**

| Exception | Statut | Package | Gérable ici via `@ExceptionHandler` ? |
|---|---|---|---|
| `UserAlreadyExistsException` | 409 | `auth.internal.service` | ✅ oui |
| `InvalidCredentialsException` | 401 | `auth.internal.service` | ✅ oui |
| `InvalidTokenException` | 401 | `auth.internal.jwt` | ❌ **non — voir TODO 2bis** |

**Important, avant d'implémenter :** ne mets *pas* `InvalidTokenException` dans ce
`@RestControllerAdvice`. Ce ne serait pas faux en soi, mais ce serait du **code mort** :
cette exception n'est jamais levée pendant l'exécution d'une méthode de controller, donc
`@ExceptionHandler` ne peut structurellement pas l'intercepter ici. Explication complète et
solution : TODO 2bis, juste après.

Les deux exceptions du haut du tableau (`UserAlreadyExistsException`,
`InvalidCredentialsException`), elles, sont bien levées depuis `AuthService` — appelé
directement par `AuthController` — donc dans le chemin normal de `DispatcherServlet`. Pour
elles, le `@RestControllerAdvice` classique ci-dessous fonctionne tel quel.

Squelette illustratif (à adapter, pas à copier tel quel) :

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleConflict(
            UserAlreadyExistsException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // ... idem pour InvalidTokenException

    private ResponseEntity<ErrorResponseDto> build(
            HttpStatus status, String message, HttpServletRequest request) {
        var body = new ErrorResponseDto(
                Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
```

**Point d'attention :** ces trois exceptions sont actuellement dans des packages
`internal` (`auth.internal.service`, `auth.internal.jwt`). Si le projet utilise Spring
Modulith (le `pom.xml` référence `spring-modulith` — à vérifier), un package `internal`
n'est normalement **pas censé être visible depuis l'extérieur du module** `auth`. Comme
`GlobalExceptionHandler` vivrait dans `shared`, il ne devrait *a priori* pas pouvoir
importer ces classes sans casser l'encapsulation du module. Deux options à évaluer
toi-même selon ce que tu trouves en creusant le module `auth` :
1. `GlobalExceptionHandler` reste dans `shared` et ne gère que des exceptions
   *génériques* (validation, 404, 500 catch-all) ; chaque module (`auth`, plus tard
   `appointment`, etc.) définit son **propre** `@RestControllerAdvice` local pour ses
   exceptions internes.
2. Ces exceptions remontent au niveau du package public du module (`ci.kpata.backend.auth`,
   à côté de `AuthController`) plutôt que dans `internal`.

L'option 1 est probablement la plus cohérente avec l'architecture modulaire déjà en place
(regarde comment `AuthController` est le seul type public du module `auth`) — mais
vérifie par toi-même avant de trancher.

---

### TODO 2bis — Cas particulier : les 401 du filtre JWT et du point d'entrée d'authentification ✅ fait

**État actuel (mis à jour après implémentation) :** le comportement décrit dans cette
section a évolué une fois implémenté — voir `docs/auth-system-overview.md`, §3 et §5, pour
le comportement exact et à jour (`JwtFilter` ne rejette plus rien lui-même : un token
invalide est traité comme l'absence de token, et c'est `authorizeHttpRequests` qui décide
si c'est un problème selon que la route est publique ou protégée). Le texte ci-dessous
reste utile pour comprendre le problème initial et pourquoi `ErrorResponseWriter` a été
créé, mais ne décrit plus exactement le code final.

**Pourquoi ce TODO existe séparément de TODO 2 :** en vérifiant le code, deux endroits du
projet produisent déjà un `401`, mais **ni l'un ni l'autre ne passe par `DispatcherServlet`**
— donc aucun `@ExceptionHandler` ne pourra jamais les intercepter, peu importe comment tu
écris ton `GlobalExceptionHandler`. Si tu ne traites pas ce cas séparément, ces deux
endroits continueront à répondre avec un corps vide (ou avec la page d'erreur générique de
Spring Boot une fois TODO 4 fait), en contradiction avec le contrat `ErrorResponseDto`
promis en §4.

**Le concept à comprendre : `Filter` vs `DispatcherServlet`**

```
Requête HTTP
    ↓
Chaîne de Filter Servlet (dont JwtFilter, et le mécanisme d'exceptionHandling
de Spring Security qui appelle l'authenticationEntryPoint)
    ↓
DispatcherServlet  ←── @RestControllerAdvice / @ExceptionHandler n'agissent
    ↓                   qu'à partir d'ici, sur ce que déclenche l'exécution
Controller              d'une méthode de controller.
    ↓
Service
```

Un `Filter` Servlet (comme `JwtFilter extends OncePerRequestFilter`) s'exécute **avant**
`DispatcherServlet`. Une exception levée — ou attrapée — à l'intérieur d'un filtre n'entre
donc jamais dans le mécanisme de dispatch de Spring MVC, et ne peut pas être vue par un
`@ExceptionHandler`, qui n'existe que côté `DispatcherServlet`. C'est une limite
structurelle, pas un oubli de configuration.

**Les deux endroits concrets à corriger (constat initial — voir note d'état ci-dessus
pour le comportement final) :**

1. `src/main/java/ci/kpata/backend/auth/internal/jwt/JwtFilter.java`, dans le
   `catch (InvalidTokenException e)` — faisait `response.sendError(401, e.getMessage())`.
   C'est le cas "token expiré / révoqué / invalide".

2. `src/main/java/ci/kpata/backend/auth/internal/security/JwtWebSecurityConfig.java`,
   dans l'`authenticationEntryPoint` (appelé par Spring Security quand une route protégée
   est appelée sans être authentifié) — faisait aussi
   `response.sendError(401, authException.getMessage())`. C'est le cas "route protégée
   sans token".

Les deux ont exactement le même symptôme (`sendError` au lieu du format `ErrorResponseDto`)
et donc la même solution.

**Solution : construire et écrire le JSON toi-même, à ces deux endroits, au lieu de
`sendError`.**

`sendError(status, message)` ne fait que fixer un code HTTP et déclencher, plus tard, le
mécanisme de page d'erreur du serveur (celui-là même que tu corriges dans TODO 4) — il
**n'écrit pas** un corps JSON. C'est pour ça que ces deux endroits produisent aujourd'hui
un corps vide. Puisque `@ExceptionHandler` est hors jeu ici (voir plus haut), la seule
option qui reste est d'écrire la réponse directement dans le filtre/l'entry point, avec les
outils bas niveau de la Servlet API :

- `response.setStatus(...)`
- `response.setContentType("application/json")`
- écrire le JSON dans `response.getWriter()`, en sérialisant ton `ErrorResponseDto` (TODO 1)
  avec le mapper Jackson exposé comme bean par Spring.

  **Piège rencontré en le construisant** : ce projet tourne en Spring Boot 4 / Jackson 3,
  où le bean auto-configuré par Spring s'appelle `JsonMapper`
  (`tools.jackson.databind.json.JsonMapper`) — **pas** `ObjectMapper`
  (`com.fasterxml.jackson.databind.ObjectMapper`, Jackson 2, ce que la plupart des
  tutos/réponses en ligne supposent encore). Injecter `ObjectMapper` par constructeur
  échoue au démarrage (`No qualifying bean of type 'com.fasterxml.jackson...ObjectMapper'`)
  puisqu'aucun bean de ce type n'existe dans ce contexte Spring. `jjwt-jackson` (dépendance
  du JWT) utilise, lui, Jackson 2 en interne — les deux coexistent sur le classpath sans
  rapport entre eux, d'où la confusion possible.

**Piège à éviter : ne duplique pas cette logique de sérialisation à deux endroits.**
`JwtFilter` et `JwtWebSecurityConfig` ont besoin exactement du même petit bout de code
(construire un `ErrorResponseDto`, le sérialiser, l'écrire dans la réponse). Deux options
raisonnables :
1. Un petit composant partagé (ex. `ci.kpata.backend.shared.web.ErrorResponseWriter`,
   `@Component`, avec une méthode du style
   `write(HttpServletResponse response, HttpStatus status, String message, String path)`),
   injecté dans les deux endroits.
2. Une méthode statique utilitaire si tu préfères éviter un bean pour si peu.

L'option 1 est probablement plus propre ici parce que `JwtFilter` n'est **pas** un bean
Spring aujourd'hui (il est instancié à la main dans `JwtWebSecurityConfig` via
`new JwtFilter(jwtProvider)`) — il faudra de toute façon lui passer explicitement une
dépendance en plus dans son constructeur, qu'elle vienne d'un bean injecté dans
`JwtWebSecurityConfig` ou d'un objet construit à la main. Un petit composant réutilisable
évite au moins de réécrire la sérialisation deux fois.

**Vérifié par `curl` (voir `docs/auth-system-overview.md` §3 pour le tableau complet) :**
- Route protégée sans token → 401, `ErrorResponseDto` non vide, message générique. ✅
- Route protégée avec token invalide/expiré → 401, `ErrorResponseDto` non vide, message
  précis ("Expired or invalid JWT token"). ✅
- Route **publique** avec token invalide → token ignoré, requête traitée normalement
  (comportement ajouté après coup, voir la note d'état en tête de section). ✅

**Toujours ouvert :** `JwtFilter` n'a toujours **aucun test dédié** (seul `JwtProviderTest`
existe, et il teste `JwtProvider`, pas le filtre lui-même) — les trois scénarios ci-dessus
n'ont été vérifiés que manuellement via `curl`, pas verrouillés par un test automatisé.
À faire à un moment (pas forcément maintenant, pas bloquant pour TODO 3).

---

### TODO 3 — Gérer les erreurs de validation (`@Valid`) ✅ fait

**État actuel (mis à jour après implémentation) :** `@ExceptionHandler(MethodArgumentNotValidException.class)`
est fait — `SignupRequestDto` invalide → `400` + `fieldErrors` peuplé (un par champ), voir
`docs/auth-system-overview.md` §4 et §5 pour le détail et la vérification `curl`. Le second
cas décrit plus bas dans cette section est fait aussi :
`@ExceptionHandler(HttpMessageNotReadableException.class)` gère le JSON malformé — un
`POST /auth/signup` avec un corps JSON syntaxiquement invalide (ex. `{not valid`) renvoie
maintenant un `400` propre avec `fieldErrors` vide (voir `docs/auth-system-overview.md` §5,
entrée du 2026-08-14).

**Point de départ concret :** `GlobalExceptionHandler` existe déjà (`shared/web/GlobalExceptionHandler.java`)
avec une méthode `handleException(ApplicationException, HttpServletRequest)`. Tu n'écris
pas ce fichier depuis zéro — tu **ajoutes** une ou deux méthodes `@ExceptionHandler`
supplémentaires à côté de celle qui existe déjà, pour des exceptions qui n'héritent *pas*
d'`ApplicationException` (`MethodArgumentNotValidException` et
`HttpMessageNotReadableException` sont levées par Spring lui-même, pas par ton code métier
— rien à changer côté `ApplicationException`). `ErrorResponseDto` existe aussi déjà, avec
son champ `fieldErrors` (pluriel) prêt à être peuplé.

**Pourquoi séparément de TODO 2 :** `MethodArgumentNotValidException` n'est pas une
exception métier que *toi* tu lances — elle est levée automatiquement par Spring quand
un DTO annoté `@Valid` (comme `SignupRequestDto`) échoue sa validation. Elle contient une
**liste** de champs en erreur (`BindingResult`), pas un message unique. C'est ce qui
justifie le champ `fieldErrors` du TODO 1 : c'est le seul cas qui le remplit.

**Quoi faire :** ajouter un `@ExceptionHandler(MethodArgumentNotValidException.class)`
dans le même `GlobalExceptionHandler` (celui-ci peut légitimement vivre dans `shared`,
puisque `@Valid` est un mécanisme transverse, pas spécifique à `auth`), qui parcourt
`ex.getBindingResult().getFieldErrors()` pour peupler `fieldErrors`, avec un statut 400.

Pense aussi à `HttpMessageNotReadableException` (le cas "JSON malformé" testé) — même
logique, statut 400, message plus générique puisqu'il n'y a pas de champ précis en cause.

---

### TODO 4 — Filet de sécurité : autoriser aussi le dispatch `ERROR` ✅ fait

**État actuel (clôturé le 2026-08-14) :** les deux moitiés sont faites. Le routage
(`dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()`) était
déjà en place. Le contrat JSON manquant (décrit plus bas, "ce qui reste") est maintenant
géré par un `@ExceptionHandler(Exception.class)` catch-all ajouté à `GlobalExceptionHandler`
— voir le code pour le détail (`instanceof ErrorResponse` pour préserver le vrai statut
d'une exception qui en porte déjà un, sinon 500 générique + log serveur). Vérifié par
`curl` : route inexistante + token valide → `404` avec `ErrorResponseDto` complet
(`message`, `fieldErrors` inclus, plus le corps nu de `BasicErrorController`) ; `GET` sur
une route qui n'accepte que `POST` → `405` idem. Non-régression vérifiée sur les cas
existants (409 doublon, 400 JSON malformé, 401 mauvais mot de passe) + suite de tests
(`AuthServiceTest`, `JwtProviderTest`, etc.) toujours verte (27 tests).

**Pourquoi encore nécessaire si TODO 2/3 gèrent déjà tout ?** Parce que TODO 2/3 ne
couvrent que les exceptions *que tu as anticipées*. Un vrai bug non prévu (une
`NullPointerException` qui t'a échappé, par exemple) continuera à emprunter le chemin
"exception non interceptée → forward `/error`". Sans ce TODO 4, ce genre de bug futur
redeviendrait un 401 vide et te ferait perdre du temps en debug plus tard, pour la même
raison qu'aujourd'hui. Ce n'est pas redondant avec TODO 2/3, c'est un filet de sécurité
pour l'inconnu.

**Quoi faire (fait) :** dans `JwtWebSecurityConfig`, élargir la règle :

```java
config
        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR)
        .permitAll();
```

**Contrat JSON manquant, maintenant corrigé :** `BasicErrorController` répondait sur
`/error` avec **son propre format JSON** (`timestamp/status/error/path`, sans `message` ni
`fieldErrors`), constaté par `curl` sur deux cas :

```json
// avant correctif — route inexistante, token valide -> 404
{"timestamp":"2026-08-14T09:29:24.761Z","status":404,"error":"Not Found","path":"/some/nonexistent/route"}

// avant correctif — GET sur une route qui n'accepte que POST -> 405
{"timestamp":"2026-08-14T09:29:43.205Z","status":405,"error":"Method Not Allowed","path":"/auth/login"}
```

Un `@ExceptionHandler(Exception.class)` catch-all dans `GlobalExceptionHandler` intercepte
maintenant ces cas *avant* qu'ils n'atteignent `BasicErrorController` — piège évité : un
catch-all naïf (toujours 500) aurait transformé ces `404`/`405` légitimes en faux positifs
"erreur serveur". La solution retenue distingue les deux via l'interface
`org.springframework.web.ErrorResponse`, implémentée depuis Spring Framework 6 par
plusieurs exceptions du framework qui portent déjà leur vrai statut
(`HttpRequestMethodNotSupportedException` → 405, `NoResourceFoundException` → 404...) :
si l'exception l'implémente, son statut réel est réutilisé (`errorResponse.getStatusCode()`) ;
sinon, c'est traité comme un bug réellement imprévu → `500`, message générique fixe (jamais
`exception.getMessage()`, qui pourrait exposer des détails internes), avec la stack trace
complète loguée côté serveur — seul endroit où un tel bug pourra jamais être repéré.

---

### TODO 5 — Revalider tous les scénarios avec `curl` ✅ fait

**État actuel (vérifié le 2026-08-14, exécuté par toi) :** les 8 scénarios rejoués tels
quels, 7/8 conformes à l'attendu (statut **et** corps). Le seul écart — test 1 (signup
neuf) reçu en `409` au lieu de `201` — s'explique par un script lancé deux fois de suite :
le compte existait déjà depuis le premier passage. Pas un bug ; le chemin "signup → `201`"
avait de toute façon déjà été exercé pendant les vérifications de TODO 3/4. Phase 0 est
maintenant close (reste seulement `TODO LoginRequestDto`, voir `auth-frontend-readiness.md`
§0).

Une fois TODO 1 à 4 faits, rejoue exactement les mêmes requêtes que celles testées plus
haut dans la conversation, et vérifie que chaque statut et chaque corps sont corrects :

```bash
# 1. Signup OK -> 201 + {"token": "..."}
curl -i -X POST http://localhost:8080/auth/signup -H "Content-Type: application/json" \
  -d '{"firstname":"Test","lastname":"User","phoneNumber":"0501020304","password":"password123","email":"t1@example.com"}'

# 2. Signup doublon -> 409 + corps ErrorResponseDto explicite
curl -i -X POST http://localhost:8080/auth/signup -H "Content-Type: application/json" \
  -d '{"firstname":"Test","lastname":"User","phoneNumber":"0501020304","password":"password123","email":"t2@example.com"}'

# 3. Login mauvais mdp -> 401 + corps explicite (pas vide)
curl -i -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"phoneNumber":"0501020304","password":"wrong"}'

# 4. Validation (champs invalides) -> 400 + fieldErrors peuplé
curl -i -X POST http://localhost:8080/auth/signup -H "Content-Type: application/json" \
  -d '{"firstname":"","lastname":"User","phoneNumber":"123","password":"short","email":"bad"}'

# 5. JSON malformé -> 400
curl -i -X POST http://localhost:8080/auth/signup -H "Content-Type: application/json" -d '{not valid'

# 6. Route protégée sans token -> 401
curl -i http://localhost:8080/some/protected/route

# 7. Route inexistante avec token valide -> 404 (pas 401 !)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"phoneNumber":"0501020304","password":"password123"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
curl -i http://localhost:8080/some/protected/route -H "Authorization: Bearer $TOKEN"

# 8. Login (route PUBLIQUE) avec un token invalide fourni -> ignoré, PAS un 401 lié au token
# (déjà vérifié pour TODO 2bis, à revérifier ici avec le reste une fois tout fait)
curl -i -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -H "Authorization: Bearer garbage.invalid.token" \
  -d '{"phoneNumber":"0501020304","password":"password123"}'
```

Le test 7 est le plus important : c'est celui qui prouve que le point d'entrée
d'authentification custom ne pollue plus plus les erreurs qui n'ont rien à voir avec
l'authentification. Le test 8 vérifie que le token invalide est bien ignoré sur une route
publique plutôt que de bloquer la requête (comportement ajouté après TODO 2bis, voir
`docs/auth-system-overview.md` §3).

---

### TODO 6 (bonus, indépendant du bug 401) — Exposer `/auth/logout` ✅ fait

**État actuel (vérifié le 2026-08-14) :** `AuthController` expose `POST /auth/logout`,
protégée (absente de `permitAll()`). Vérifié par `curl` : sans header `Authorization` →
`401` (bloqué avant même le controller) ; avec un token valide → `204 No Content` ; le même
token réutilisé ensuite sur une route protégée → `401 "Token has been revoked"` (révocation
immédiate confirmée) ; logout une seconde fois avec ce token déjà révoqué → `401` propre,
pas de crash. `./mvnw test -P unit-tests` toujours vert (27 tests).

**Constat (avant correctif) :** `AuthService.logout(String authorizationValue)` existait
déjà et fonctionnait (il révoque le token dans la blacklist en mémoire de `JwtProvider`),
mais aucune route ne l'appelait — `AuthController` n'avait que `signup` et `login`.

**Pourquoi c'est nécessaire pour le frontend :** sans cette route, un "logout" côté
client ne peut être qu'une suppression locale du token stocké (localStorage/cookie). Le
token reste valide côté serveur jusqu'à expiration (5 minutes actuellement, donc impact
limité, mais faux quand même si l'expiration change plus tard).

**Quoi faire :** dans `AuthController`, ajouter :

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
    service.logout(authorization);
    return ResponseEntity.noContent().build();
}
```

Cette route n'a pas besoin d'être ajoutée à `permitAll()` dans `JwtWebSecurityConfig` :
elle doit au contraire **rester protégée** (`anyRequest().authenticated()` s'applique
déjà par défaut), sinon n'importe qui pourrait révoquer le token de n'importe qui en
devinant/volant juste la valeur du header — bon réflexe de sécurité à garder en tête ici :
exiger que la requête soit déjà authentifiée avant de la laisser révoquer *son propre*
token.

## 4. Pour le frontend : ce que tu peux déjà anticiper

Une fois ce plan appliqué, le contrat d'erreur que ton client HTTP pourra présumer
stable est :

```json
{
  "timestamp": "2026-08-08T18:31:48Z",
  "status": 409,
  "error": "Conflict",
  "message": "An account with this phone number already exists",
  "path": "/auth/signup",
  "fieldErrors": []
}
```

avec `fieldErrors` peuplé uniquement pour les 400 de validation, par ex. :

```json
{
  "fieldErrors": [
    {"field": "phoneNumber", "message": "Numéro de téléphone ivoirien invalide (...)"},
    {"field": "password", "message": "size must be between 8 and 100"}
  ]
}
```

Tu peux donc déjà écrire ton wrapper `fetch`/`axios` frontend en te basant sur cette
forme, **à condition** que le backend soit effectivement corrigé avant que tu ne
branches la vraie gestion d'erreurs (pas juste le happy path) — et en particulier que
**TODO 2bis** soit fait, sinon les deux scénarios "token expiré" et "route protégée sans
token" continueront à renvoyer un corps vide malgré TODO 1/2/3, puisqu'ils ne passent pas
par le même mécanisme.
