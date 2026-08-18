package ci.kpata.backend.shared.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;

/**
 * Converts a phone number to a single canonical E.164 form, so the same real number always
 * compares equal regardless of the format the caller sent (local {@code 0788112233} vs E.164
 * {@code +2250788112233} both pass {@link IvoryCoastPhone}). Callers must normalize once and
 * reuse that value everywhere the number is compared or stored — see {@code
 * AuthService#signup} — otherwise the two formats silently resolve to different accounts.
 */
@Component
public class PhoneNumberNormalizer {

    private static final String DEFAULT_REGION = "CI";

    /**
     * @throws IllegalStateException if {@code rawPhoneNumber} can't be parsed. This is a
     *                                "should never happen" defensive check, not user input
     *                                validation: every caller (login/signup) only receives
     *                                DTOs already validated by {@link IvoryCoastPhone} via
     *                                {@code @Valid}, so a parse failure here means that
     *                                validation was bypassed, not that the user typed a bad
     *                                number.
     */
    public String normalize(String rawPhoneNumber) {

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

        try {
            Phonenumber.PhoneNumber parsed = phoneUtil.parse(rawPhoneNumber, DEFAULT_REGION);
            return phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);

        } catch (NumberParseException e) {
            throw new IllegalStateException(
                "Failed to normalize phone number after validation passed", e);
        }
    }
}
