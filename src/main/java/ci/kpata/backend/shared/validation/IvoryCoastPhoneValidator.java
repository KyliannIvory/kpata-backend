package ci.kpata.backend.shared.validation;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IvoryCoastPhoneValidator implements ConstraintValidator<IvoryCoastPhone, String> {

    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {

        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(value, "CI");
            return phoneUtil.isValidNumber(number) && phoneUtil.getRegionCodeForNumber(number).equals("CI");
        } catch (Exception e) {
            return false;
        }
    }
}
