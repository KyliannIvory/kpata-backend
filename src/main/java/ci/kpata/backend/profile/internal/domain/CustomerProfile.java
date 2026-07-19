package ci.kpata.backend.profile.internal.domain;

import ci.kpata.backend.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "customer_profiles")
@Builder
class CustomerProfile extends BaseEntity {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

}
