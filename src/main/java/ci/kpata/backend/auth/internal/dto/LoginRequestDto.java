package ci.kpata.backend.auth.internal.dto;

/**
 * What a client is allowed to send to log in. Deliberately has no
 * {@code roles} field: a caller cannot claim its own permissions, only
 * {@code AuthService} — from the authenticated result — decides them.
 *
 * TODO(auth): contrairement à {@link SignupRequestDto}, ce DTO n'a aucune contrainte
 * de validation ({@code @NotBlank}...). Résultat : un {@code phoneNumber} ou
 * {@code password} vide/absent passe le {@code @Valid} du controller sans broncher et va
 * jusqu'à {@code AuthenticationManager}, qui finit par rejeter la requête via
 * {@code InvalidCredentialsException} (donc pas de crash), mais avec un message générique
 * "Invalid credentials" au lieu d'un 400 explicite du style "phoneNumber requis". Ajoute
 * les mêmes annotations que {@link SignupRequestDto} ici, puis vérifie avec un test que
 * {@code @Valid} rejette bien un login avec un champ vide avant même d'atteindre le
 * service. Détails : docs/auth-frontend-readiness.md, section 4.
 */
public record LoginRequestDto(

        String phoneNumber,
        String password
) {
}
