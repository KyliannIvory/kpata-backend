package ci.kpata.backend.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;

import java.lang.annotation.*;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Email(
    regexp = "^[a-zA-Z0-9._%]+@[a-zA-Z0-9._]+\\.[a-zA-Z]{2,}$",
    message = "Please provide a valid email address."
)
public @interface StrictEmail {

    String message() default "The email address format is invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
