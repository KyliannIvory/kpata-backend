# Gestion des erreurs du module `auth` — diagnostic et plan d'action

> Document pédagogique. Objectif : que tu comprennes **le problème, sa cause racine,
> et pourquoi chaque étape du correctif est nécessaire**, pour l'implémenter toi-même.
> Rien ici n'est un correctif appliqué — c'est un guide.

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

- [ ] **TODO 1 — Définir un format de réponse d'erreur commun**
- [ ] **TODO 2 — Créer un `@RestControllerAdvice` global qui mappe les exceptions métier**
- [ ] **TODO 3 — Gérer proprement les erreurs de validation (`@Valid`)**
- [ ] **TODO 4 — Corriger le filtre de sécurité en filet de sécurité (dispatch `ERROR`)**
- [ ] **TODO 5 — Revalider tous les scénarios avec `curl`**
- [ ] **TODO 6 (bonus, hors périmètre du bug) — Exposer `/auth/logout`**

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

### TODO 2 — Créer le `@RestControllerAdvice` global

**Pourquoi :** c'est le mécanisme Spring standard pour intercepter une exception
*avant* qu'elle ne devienne une erreur non gérée qui force Spring Boot à faire son forward
interne vers `/error` (le point sensible du §2.1). Un `@ExceptionHandler` qui matche
l'exception court-circuite complètement ce chemin : plus besoin que le forward `/error`
fonctionne pour ces cas précis.

**Où :** par exemple `ci.kpata.backend.shared.web.GlobalExceptionHandler` (package
`shared`, puisque ça concerne toute l'app, pas seulement `auth` — même si aujourd'hui
seules des exceptions du module `auth` existent).

**Quoi mapper, avec le code HTTP indiqué par la Javadoc déjà écrite dans le code :**

| Exception | Statut | Package |
|---|---|---|
| `UserAlreadyExistsException` | 409 | `auth.internal.service` |
| `InvalidCredentialsException` | 401 | `auth.internal.service` |
| `InvalidTokenException` | 401 | `auth.internal.jwt` |

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

### TODO 3 — Gérer les erreurs de validation (`@Valid`)

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

### TODO 4 — Filet de sécurité : autoriser aussi le dispatch `ERROR`

**Pourquoi encore nécessaire si TODO 2/3 gèrent déjà tout ?** Parce que TODO 2/3 ne
couvrent que les exceptions *que tu as anticipées*. Un vrai bug non prévu (une
`NullPointerException` qui t'a échappé, par exemple) continuera à emprunter le chemin
"exception non interceptée → forward `/error`". Sans ce TODO 4, ce genre de bug futur
redeviendrait un 401 vide et te ferait perdre du temps en debug plus tard, pour la même
raison qu'aujourd'hui. Ce n'est pas redondant avec TODO 2/3, c'est un filet de sécurité
pour l'inconnu.

**Quoi faire :** dans `JwtWebSecurityConfig`, élargir la règle :

```java
config
        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR)
        .permitAll();
```

Vérifie ensuite que `BasicErrorController` (celui fourni par défaut par Spring Boot)
répond avec un JSON correct sur `/error` pour un cas non intercepté — sinon envisage
d'ajouter un `@ExceptionHandler(Exception.class)` catch-all dans `GlobalExceptionHandler`
en complément (statut 500, message générique, sans détails d'implémentation exposés au
client pour ne pas fuiter d'infos sensibles).

---

### TODO 5 — Revalider tous les scénarios avec `curl`

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
```

Le test 7 est le plus important : c'est celui qui prouve que le point d'entrée
d'authentification custom ne pollue plus plus les erreurs qui n'ont rien à voir avec
l'authentification.

---

### TODO 6 (bonus, indépendant du bug 401) — Exposer `/auth/logout`

**Constat :** `AuthService.logout(String authorizationValue)` existe déjà et fonctionne
(il révoque le token dans la blacklist en mémoire de `JwtProvider`), mais **aucune route
ne l'appelle** — `AuthController` n'a que `signup` et `login`.

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
branches la vraie gestion d'erreurs (pas juste le happy path).
