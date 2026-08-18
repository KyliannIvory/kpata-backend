package ci.kpata.backend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept out of {@code BackendApplication} on purpose: a {@code @WebMvcTest} slice loads
 * that class as its configuration source, and an {@code @Enable*} annotation declared
 * directly on it still applies even though the slice's component scan otherwise skips
 * unrelated {@code @Configuration} classes. With {@code @EnableJpaAuditing} on {@code
 * BackendApplication} itself, every {@code @WebMvcTest} tried to wire JPA auditing
 * infrastructure with no entities in the slice context and failed with "JPA metamodel
 * must not be empty". Moving it here fixes that: full app boot still picks it up via
 * component scanning, but a web-only slice doesn't.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
