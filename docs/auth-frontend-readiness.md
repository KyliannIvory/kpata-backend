# Auth : ce qu'il manque pour que le frontend soit "prêt"

> Document pédagogique. Complète `docs/auth-error-handling.md`, qui couvre déjà en
> détail le `@ControllerAdvice` et l'endpoint `/auth/logout` manquant — pas répété ici.
> Rien ici n'est un correctif appliqué : chaque section t'explique le problème et les
> options, à toi d'implémenter (les TODO correspondants sont posés directement dans le
> code, avec un renvoi vers la section concernée).

## 0. Feuille de route complète — l'ordre dans lequel avancer

Ce document et `docs/auth-error-handling.md` ont chacun leur propre plan interne, mais
aucun des deux ne dit dans quel ordre traiter les TODO de **l'un par rapport à l'autre**
(ni par rapport aux TODO posés directement dans le code). C'est l'objet de cette section —
**c'est le point d'entrée unique** : commence ici, elle te renvoie vers la bonne section du
bon document à chaque étape.

**Pourquoi cet ordre précis et pas un autre :** le critère principal est *"est-ce que
l'étape suivante est vérifiable proprement sans celle-ci ?"*. Tant que la gestion
d'erreurs n'est pas corrigée, tout retourne un `401` vide — donc tester `/auth/me`,
`/auth/logout` ou la validation de `LoginRequestDto` donnerait des résultats trompeurs
(tu ne saurais pas si un `401` vient d'un vrai problème d'auth ou du bug de fond). D'où la
phase 0 en premier. Les phases suivantes sont ensuite ordonnées par rapport
gain/effort : les corrections rapides et isolées avant les fonctionnalités qui demandent
plus de conception.

### Phase 0 — Réparer la gestion d'erreurs (bloquant pour tout le reste)

Détail complet de chaque étape : `docs/auth-error-handling.md`.

- [x] **TODO 1** — définir `ErrorResponseDto` (le contrat JSON commun)
- [x] **TODO 2** — `@RestControllerAdvice` pour `UserAlreadyExistsException` (409) et
      `InvalidCredentialsException` (401) — implémenté via une hiérarchie d'exceptions
      (`ApplicationException`) plutôt qu'un handler par exception, voir
      `docs/auth-error-handling.md` TODO 2 et `docs/auth-system-overview.md` §5
- [x] **TODO 2bis** — cas particulier des 401 levés par `JwtFilter` et
      l'`authenticationEntryPoint` (ne passent pas par `@ExceptionHandler`, voir
      `JwtFilter.java` et `JwtWebSecurityConfig.java`, TODO ajoutés dans le code)
- [x] **TODO 3** — erreurs de validation (`@Valid` et JSON malformé faits, voir
      `docs/auth-error-handling.md` TODO 3)
- [ ] **TODO LoginRequestDto** (`LoginRequestDto.java`, voir §4 plus bas) — à faire *avec*
      TODO 3 plutôt qu'à part : ça te permet de vérifier le mécanisme de TODO 3 sur les
      deux DTO (`SignupRequestDto` déjà annoté, `LoginRequestDto` à annoter) au lieu de le
      valider une fois puis de le re-tester plus tard quand tu ajouteras les annotations.
- [x] **TODO 4** — filet de sécurité, `dispatcherTypeMatchers(FORWARD, ERROR)` +
      catch-all `Exception.class` (voir `docs/auth-error-handling.md` TODO 4)
- [x] **TODO 5** — revalider tous les scénarios `curl` (fait le 2026-08-14, 8/8 conformes)

### Phase 1 — Compléter les endpoints auth manquants

- [x] **`POST /auth/logout`** (fait et vérifié le 2026-08-14, voir `docs/auth-error-handling.md`
      TODO 6) — route protégée, révocation immédiate confirmée par `curl`.
- [ ] **`GET /auth/me`** (`AuthController.java`, TODO déjà posé dans le code, détail : §2
      ci-dessous) — après logout : demande plus de travail (nouveau DTO de réponse, aller
      chercher le `User` complet, décider du cas "utilisateur supprimé entre-temps").

> Note : `auth-frontend-readiness.md` proposait initialement `/me` avant `/logout` — ordre
> inversé ici volontairement, pour enchaîner sur une victoire rapide avant la tâche qui
> demande plus de décisions de conception.

### Phase 2 — Robustesse et configuration avant un vrai frontend en navigateur

- [ ] **Durée de vie du token** (`application.yaml`, TODO déjà posé dans le code, détail :
      §3 ci-dessous) — au minimum, choisir consciemment l'option 1 (augmenter
      pragmatiquement `jwt.expiration`) ; l'option 2 (refresh token complet) peut attendre.
- [ ] **CORS** (`JwtWebSecurityConfig.java`, TODO déjà posé dans le code, détail : §5
      ci-dessous) — seulement utile une fois que l'URL réelle du frontend est connue ; pas
      bloquant tant que tu testes avec `curl`/Postman.

### Phase 3 — Tests manquants au niveau controller/sécurité

- [ ] **Tests `AuthController` + config de sécurité** (détail : §6 ci-dessous) — en
      dernier, volontairement : ces tests couvrent des comportements qui viennent tout
      juste d'être stabilisés par les phases 0 à 2 (bons codes de statut, `/me`, `/logout`,
      CORS). Les écrire avant aurait signifié les réécrire plusieurs fois.

### Backlog — pas urgent, pas dans l'ordre chronologique ci-dessus

Ces TODO existent dans le code mais ne bloquent rien pour l'instant :

- `JwtProvider.java` (TODO Redis) — la blacklist de tokens révoqués est en mémoire, donc
  perdue au redémarrage et non partagée entre instances. Non pertinent tant que l'app
  tourne en une seule instance ; à traiter seulement si/quand le projet passe à plusieurs
  instances (scaling horizontal).
- `JwtWebSecurityConfig.java:111` (routes `/salons/**`, `/treatments/**`,
  `/availabilities/**`) — routes placeholder pour des contrôleurs qui n'existent pas
  encore. À corriger quand ces modules seront créés, pas avant.

## 1. Vue d'ensemble

| Besoin frontend | État actuel | Où creuser |
|---|---|---|
| Signup / login → token | ✅ fonctionne | `AuthController`, `AuthService` |
| Erreurs en JSON cohérent (400/401/409...) | ✅ tous les cas corrigés et revalidés de bout en bout par `curl` (8/8 scénarios, TODO 5) | `docs/auth-error-handling.md` |
| `POST /auth/logout` | ✅ fait et vérifié (route protégée, révocation immédiate) | `docs/auth-error-handling.md`, TODO 6 |
| `GET /auth/me` | ❌ n'existe pas | §2 ci-dessous |
| Validation du login (`LoginRequestDto`) | ❌ aucune contrainte | §4 ci-dessous |
| Durée de vie du token / renouvellement | ⚠️ 5 min, pas de refresh | §3 ci-dessous |
| CORS | ⚠️ grand ouvert (`*`) | §5 ci-dessous |
| Tests controller / sécurité | ❌ aucun | §6 ci-dessous |

Les TODO correspondants sont posés dans le code (`AuthController`, `LoginRequestDto`,
`JwtWebSecurityConfig`, `application.yaml`) — cherche `TODO(auth)`.

## 2. `GET /auth/me`

### Le problème

Une fois le token stocké côté frontend (localStorage, cookie, mémoire...), comment le
frontend sait-il **qui** est connecté et **avec quel rôle**, par exemple juste après un
rechargement de page (F5) ? Deux options existent :

1. **Décoder le JWT côté client** (un JWT n'est pas chiffré, juste signé — n'importe qui
   peut lire ses claims sans la clé secrète, il peut juste pas le falsifier). Ça marche,
   mais ça duplique la logique de lecture des claims des deux côtés, et ça fige les
   données au moment où le token a été émis (si le rôle change en base entre-temps, le
   frontend ne le verra pas avant le prochain login).
2. **Demander au backend**, qui est la source de vérité. C'est le rôle de `GET /auth/me` :
   le frontend envoie son token, le backend renvoie l'état actuel de l'utilisateur.

L'option 2 est le standard côté API REST — c'est celle à implémenter.

### Le mécanisme Spring en jeu

Tu as déjà vu que `JwtFilter` (dans `auth.internal.jwt`) place un objet `Authentication`
dans le `SecurityContextHolder` pour chaque requête porteuse d'un token valide — c'est ce
qui permet à `anyRequest().authenticated()` de laisser passer la requête. Ce même objet
est ensuite injectable directement comme paramètre de méthode dans un controller Spring
MVC :

```java
@GetMapping("/me")
public ResponseEntity<?> me(Authentication authentication) {
    // authentication.getName() == le subject du JWT == le phoneNumber (voir JwtProvider#createToken)
}
```

Spring résout ce paramètre pour toi (via un `HandlerMethodArgumentResolver` fourni par
Spring Security) — pas besoin d'appeler `SecurityContextHolder` toi-même dans le
controller.

### Piège découvert (2026-08-14, en review) : le principal n'est pas toujours un `UserDetails`

En travaillant sur TODO 4, une revue de code a trouvé une incohérence entre les deux
endroits du projet qui construisent un `Authentication` :

- `AuthService.login` (le flux `POST /auth/login`) récupère son `Authentication` via
  `AuthenticationManager.authenticate(...)`, puis fait `(UserDetails) result.getPrincipal()`
  — ça fonctionne *ici* parce que cet `AuthenticationManager` est adossé à un
  `DaoAuthenticationProvider`, qui renvoie toujours le `UserDetails` chargé par
  `UserDetailsServiceImpl` comme principal.
- `JwtProvider#getAuthentication` (le flux emprunté par **chaque requête protégée**, via
  `JwtFilter`) construit lui son `Authentication` avec `claims.getSubject()` — un `String`
  brut (le `phoneNumber`), pas un `UserDetails`.

Autrement dit : le `Authentication` que `SecurityContextHolder` contient pendant l'exécution
d'un controller **dépend de comment la requête a été authentifiée**. Pour `/auth/login`
lui-même ce n'est pas un problème (il n'y a pas encore de token à ce stade). Mais pour
**toute route protégée par token** — donc `GET /auth/me` par définition, puisqu'elle exige
un token — l'`Authentication` que tu recevras vient toujours de `JwtProvider`, jamais
d'`AuthService.login`.

**Conséquence concrète pour toi :** si tu écris `/me` en copiant le pattern
`(UserDetails) authentication.getPrincipal()` vu dans `AuthService.login` (par réflexe,
puisque c'est le seul exemple du projet qui lit un principal), tu obtiendras un
`ClassCastException` sur **chaque** appel à `/me` en pratique, puisque `/me` ne peut être
appelée qu'avec un token, donc via `JwtProvider#getAuthentication` — jamais via
`AuthService.login`.

**Bonne nouvelle :** le squelette montré plus haut dans cette section (`authentication.getName()`)
évite déjà le piège — `getName()` renvoie le `phoneNumber` que le principal soit un `String`
ou un `UserDetails` (dans les deux cas, `UsernamePasswordAuthenticationToken#getName()`
retombe sur une représentation textuelle cohérente). Tant que tu récupères l'utilisateur
complet via `UserRepository` **à partir du `phoneNumber`** (comme `UserDetailsServiceImpl`
le fait déjà) plutôt que de caster le principal, tu n'as rien à corriger.

**Décision à prendre consciemment avant d'implémenter `/me`**, si tu veux aller plus loin
qu'un simple contournement :

1. **Ne rien changer** — `getName()` + lookup par `phoneNumber` suffit pour `/me`. Le plus
   simple ; ne règle pas l'incohérence de fond, mais elle ne te bloque pas ici.
2. **Faire construire un `UserDetails` par `JwtProvider#getAuthentication` aussi**, en
   rechargeant l'utilisateur via `UserDetailsServiceImpl` à chaque requête authentifiée par
   token. Rendrait les deux chemins cohérents pour du code futur qui voudrait un
   `UserDetails` — mais réintroduit une lecture base de données sur *chaque* requête
   protégée, ce que la Javadoc de `JwtProvider` revendique explicitement éviter
   ("Authentication is fully stateless... never queries the database").

L'option 1 est probablement suffisante pour `/me` tel que décrit ci-dessous — mais tranche-le
toi-même en connaissance de cause plutôt que par accident.

### Ce qu'il te reste à décider

- Le token ne contient que `phoneNumber` + `roles` (voir `UserClaimsDto`) — pas l'id, le
  prénom, l'email. Pour les renvoyer, il faut recharger le `User` complet depuis
  `UserRepository` (regarde comment `UserDetailsServiceImpl` fait déjà exactement cette
  recherche par `phoneNumber` — même pattern à réutiliser).
- Il faut un DTO de réponse qui **n'inclut jamais le password**, même haché (cohérent
  avec les autres DTO du package `auth.internal.dto`, comme `AuthResponseDto`).
- Cas limite à trancher toi-même : le token est valide, mais l'utilisateur a été
  supprimé de la base entre-temps (rare, mais possible). Que renvoyer ? Documente ton
  choix dans le code (commentaire ou Javadoc), comme le fait déjà
  `UserDetailsServiceImpl` pour son propre cas d'utilisateur introuvable.

### Critères d'acceptation

- `GET /auth/me` sans token → `401`
- `GET /auth/me` avec token valide → `200` + infos utilisateur, sans `password`
- `GET /auth/me` avec token expiré/révoqué → `401` (déjà géré par `JwtFilter`, à vérifier
  simplement que ça reste vrai)

### Fichiers à consulter

`AuthController`, `UserRepository`, `UserDetailsServiceImpl` (pattern de lookup),
`User`, `AuthResponseDto` (convention de nommage/format des DTO existants).

## 3. Durée de vie du token (5 min) et renouvellement

### Le problème

`jwt.expiration` vaut `300000` ms, soit **5 minutes**. Passé ce délai, `JwtProvider`
rejette le token (`InvalidTokenException`, voir `parseAndVerify`) et `JwtFilter` renvoie
un `401` — sans que rien ne prévienne le frontend à l'avance. Concrètement : ton
utilisateur est déconnecté en pleine utilisation, toutes les 5 minutes, sans action de
sa part. Inutilisable en l'état pour un vrai frontend.

### Le pattern standard : access token + refresh token

Dans une architecture JWT classique, on distingue :

- un **access token** : courte durée de vie (minutes), envoyé à chaque requête, c'est
  celui que tu as déjà. Sa courte durée limite les dégâts s'il est volé.
- un **refresh token** : longue durée de vie (jours/semaines), stocké côté serveur (pour
  pouvoir être révoqué), utilisé uniquement pour obtenir un nouvel access token sans
  redemander le mot de passe (`POST /auth/refresh`).

C'est une vraie fonctionnalité à part entière (nouvelle entité `RefreshToken`,
stratégie de rotation, stockage — le blacklist en mémoire actuel de `JwtProvider` ne
suffit plus, il doit survivre à un redémarrage).

### Ce que je te recommande pour l'instant

Construire un système de refresh token maintenant, avant d'avoir even un `/auth/me` ou
une gestion d'erreurs propre, serait de la sur-ingénierie à ce stade du projet. Deux
options raisonnables :

1. **Augmenter pragmatiquement `jwt.expiration`** (ex. quelques heures) pendant que tu
   avances sur le reste, et noter explicitement que c'est un compromis temporaire
   (token plus long = fenêtre de risque plus grande s'il est volé, mais pas de refresh
   token pour compenser).
2. **Implémenter le refresh token dès maintenant**, si tu veux profiter de l'occasion
   pour apprendre ce pattern pendant que le sujet JWT est encore frais.

Question pour toi : le projet a-t-il un horizon proche où plusieurs utilisateurs réels
(pas juste toi en dev) vont l'utiliser ? Si oui, ça penche vers l'option 2 assez vite. Si
c'est encore expérimental, l'option 1 + un TODO explicite suffit pour avancer.

## 4. Validation de `LoginRequestDto`

Regarde `SignupRequestDto` : chaque champ a `@NotBlank` (et plus). `LoginRequestDto` n'a
**rien**. Ce n'est pas un crash — `AuthenticationManager` finit par rejeter un
`phoneNumber` vide via `InvalidCredentialsException` — mais c'est une incohérence
factuelle : un champ manquant devrait être un `400` explicite ("phoneNumber requis"), pas
un `401` "Invalid credentials" qui laisse penser à un mauvais mot de passe. Corrige en
alignant `LoginRequestDto` sur les mêmes annotations que `SignupRequestDto`.

## 5. CORS grand ouvert

`JwtWebSecurityConfig#corsConfigurationSource` autorise `*` pour origines, méthodes et
headers. Le CORS n'est vérifié que par les navigateurs (pas par `curl`/Postman), donc ça
ne t'a pas encore posé de problème visible. Le jour où ton frontend (dans un navigateur,
sur une origine différente du backend) appelle l'API, ce réglage devient la seule chose
qui empêche/autorise l'appel. Pas urgent tant que tu es en dev solo avec `curl`, mais à
restreindre à l'origine réelle du frontend avant d'aller plus loin — idéalement rendu
configurable comme `jwt.secret` l'est déjà, pour ne pas coder en dur une URL qui changera
entre dev et prod.

## 6. Tests manquants côté `AuthController`

Tu as déjà des tests unitaires solides sur `AuthService`, `JwtProvider` et
`UserDetailsServiceImpl` (voir `src/test/...`). Il manque le niveau au-dessus : aucun
test ne vérifie `AuthController` lui-même, ni la config de sécurité — par exemple :

- une route protégée sans token → `401`
- une route publique (`/auth/login`, `/auth/signup`) → accessible sans token
- `/auth/signup` avec un doublon → le bon statut une fois `docs/auth-error-handling.md`
  appliqué

Ce n'est pas urgent avant d'avoir stabilisé `/me`, `/logout` et la gestion d'erreurs
(sinon tu réécris les mêmes tests trois fois) — mais garde-le dans ta todo-list avant de
considérer le module `auth` "terminé".

## Prochaines étapes suggérées

Voir la feuille de route complète en section 0, en tête de ce document — elle couvre
l'ordre chronologique pour l'ensemble des TODO du projet (ce document, `auth-error-handling.md`,
et les TODO posés directement dans le code), pas seulement ceux ci-dessus.
