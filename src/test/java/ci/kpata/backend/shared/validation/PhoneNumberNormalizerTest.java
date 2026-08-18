package ci.kpata.backend.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberNormalizerTest {

    private static final String CANONICAL_E164 = "+2250701234567";

    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer();

    @ParameterizedTest
    @ValueSource(strings = {"0701234567", "+2250701234567", "2250701234567"})
    void normalize_returnsSameCanonicalE164_regardlessOfInputFormat(String value) {
        assertThat(normalizer.normalize(value)).isEqualTo(CANONICAL_E164);
    }

    @Test
    void normalize_throwsIllegalState_forUnparsableValue() {
        assertThatThrownBy(() -> normalizer.normalize("not-a-phone-number"))
            .isInstanceOf(IllegalStateException.class)
            .hasCauseInstanceOf(com.google.i18n.phonenumbers.NumberParseException.class);
    }
}
