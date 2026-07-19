package ci.kpata.backend.treatment.internal.domain;

import ci.kpata.backend.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "treatments")
class Treatment extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12)
    @DecimalMin(value = "0", message = "The price can't be negative.")
    @NotNull
    private BigDecimal price;

    @Column(name = "duration_in_minutes", nullable = false)
    @Min(value = 5, message = "The minimum duration is 5 minutes.")
    @NotNull
    private Integer durationInMinutes;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

}
