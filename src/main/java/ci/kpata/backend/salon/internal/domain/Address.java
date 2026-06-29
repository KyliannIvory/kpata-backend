package ci.kpata.backend.salon.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;


@Embeddable
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
class Address {

    @Column(nullable = false)
    private String city;

    private String municipality;

    @Column(nullable = false)
    private String neighborhood;

    private String street;

    @Column(name = "location_hint", length = 500)
    private String locationHint;

}
