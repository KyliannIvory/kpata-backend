package ci.kpata.backend.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = IvoryCoastPhoneValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IvoryCoastPhone {

    String message() default "Numéro de téléphone ivoirien invalide (ex : +2250701020304 ou 0701020304)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
