# Auth : ce qu'il manque pour que le frontend soit "prêt"

> Document pédagogique. Complète `docs/auth-error-handling.md`, qui couvre déjà en
> détail le `@ControllerAdvice` et l'endpoint `/auth/logout` manquant — pas répété ici.
> Rien ici n'est un correctif appliqué : chaque section t'explique le problème et les
> options, à toi d'implémenter (les TODO correspondants sont posés directement dans le
> code, avec un renvoi vers la section concernée).

## 1. Vue d'ensemble

| Besoin frontend | État actuel | Où creuser |
|---|---|---|
| Signup / login → token | ✅ fonctionne | `AuthController`, `AuthService` |
| Erreurs en JSON cohérent (400/401/409...) | ❌ tout devient un 401 vide | `docs/auth-error-handling.md` |
| `POST /auth/logout` | ⚠️ le service existe, la route non | `docs/auth-error-handling.md`, TODO 6 |
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

Ordre suggéré, mais discute-le si tu vois une meilleure séquence pour toi :

1. `docs/auth-error-handling.md` (déjà écrit, à implémenter) — sans ça, aucune erreur
   n'est exploitable par le frontend, tout le reste en dépend pour être testable proprement.
2. `LoginRequestDto` (§4) — rapide, isolé.
3. `GET /auth/me` (§2).
4. `POST /auth/logout` (déjà détaillé dans `auth-error-handling.md`, TODO 6).
5. Durée de vie du token (§3) — décide au moins l'option 1 en attendant.
6. CORS (§5) — dès que tu connais l'URL réelle de ton frontend.
7. Tests controller/sécurité (§6).
