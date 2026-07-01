package ci.kpata.backend.profile.internal.domain;

import ci.kpata.backend.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "professional_profiles")
@Builder
class ProfessionalProfile extends BaseEntity {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name= "salon_id", nullable = false)
    private UUID salonId;

    @Column(columnDefinition = "TEXT")
    private String description;
}