package ci.kpata.backend.appointment.internal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "appointments")
@Builder
class Appointment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "treatment_id", nullable = false)
    private UUID treatmentId;

    @Column(name ="salon_id", nullable = false)
    private UUID salonId;

    @NotNull
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @NotNull
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @NotNull
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;
}
