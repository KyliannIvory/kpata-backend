package ci.kpata.backend.salon.internal.domain;

import ci.kpata.backend.shared.domain.BaseEntity;
import ci.kpata.backend.shared.validation.IvoryCoastPhone;
import ci.kpata.backend.shared.validation.StrictEmail;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "salons")
class Salon extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    @NotBlank
    private String name;

    @Embedded
    @Valid
    private Address address;

    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String description;

    @IvoryCoastPhone
    @Column(name = "phone_number",nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @StrictEmail
    @Column(nullable = false, length = 100)
    private String email;

}
