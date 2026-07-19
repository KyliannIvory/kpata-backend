package ci.kpata.backend.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IvoryCoastPhoneValidatorTest {

    private final IvoryCoastPhoneValidator validator = new IvoryCoastPhoneValidator();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void isValid_returnsTrue_whenValueIsBlank(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0701234567", "+2250701234567", "2250701234567"})
    void isValid_returnsTrue_forValidIvoryCoastNumbers(String value) {
        assertThat(validator.isValid(value, null)).isTrue();
    }

    @Test
    void isValid_returnsFalse_forNumberFromAnotherCountry() {
        assertThat(validator.isValid("+33612345678", null)).isFalse();
    }

    @Test
    void isValid_returnsFalse_forNumberWithInvalidLength() {
        assertThat(validator.isValid("0612345678", null)).isFalse();
    }

    @Test
    void isValid_returnsFalse_forUnparsableValue() {
        assertThat(validator.isValid("not-a-phone-number", null)).isFalse();
    }
}
