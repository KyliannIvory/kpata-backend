package ci.kpata.backend.auth.internal.domain;

import ci.kpata.backend.shared.domain.BaseEntity;
import ci.kpata.backend.shared.validation.IvoryCoastPhone;
import ci.kpata.backend.shared.validation.StrictEmail;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.UUID;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
@Builder
class User extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 50)
    @NotBlank
    private String firstname;

    @Column(nullable = false, length = 50)
    @NotBlank
    private String lastname;

    @IvoryCoastPhone
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    @NotBlank
    private String phoneNumber;

    @Column(nullable = false)
    @NotBlank
    private String password;

    @Column(unique = true, length = 100)
    @StrictEmail
    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles",joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50)
    private Set<Role> roles = new HashSet<>();
}
