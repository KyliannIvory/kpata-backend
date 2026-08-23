package ci.kpata.backend.profile.internal.domain;

import ci.kpata.backend.shared.domain.BaseEntity;
import ci.kpata.backend.shared.exception.ApplicationException;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A user's professional identity within a salon (their {@link SalonRole} there, plus a
 * free-text bio). Shares its primary key with {@code User} — one professional profile per
 * user, enforced at the DB level by {@code fk_professional_profiles_user} — rather than
 * modeling it as a JPA relationship, since {@code User} lives in the {@code auth} module's
 * {@code internal} package and is therefore hidden from other modules, {@code profile}
 * included (see {@link ApplicationException}'s Javadoc for why
 * {@code internal} packages exist). {@code userId} is stored as a plain {@code UUID} and
 * looked up directly instead of navigated through a JPA association.
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "professional_profiles")
@Builder
public class ProfessionalProfile extends BaseEntity {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "salon_id", nullable = false)
    private UUID salonId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "salon_role", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    private SalonRole salonRole;
}