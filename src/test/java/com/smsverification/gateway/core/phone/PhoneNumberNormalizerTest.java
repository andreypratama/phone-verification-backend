package com.smsverification.gateway.core.phone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {

    private final PhoneNumberNormalizer normalizer = new PhoneNumberNormalizer("62");

    @Test
    void normalizesIndonesianLocalNumber() {
        assertThat(normalizer.normalize("0812 3456-7890")).isEqualTo("+6281234567890");
    }

    @Test
    void normalizesCountryCodeWithoutPlus() {
        assertThat(normalizer.normalize("6281234567890")).isEqualTo("+6281234567890");
    }

    @Test
    void keepsValidE164Number() {
        assertThat(normalizer.normalize("+62 (812) 3456 7890")).isEqualTo("+6281234567890");
    }

    @Test
    void rejectsInvalidOrTooShortNumbers() {
        assertThatThrownBy(() -> normalizer.normalize("08ABC"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize("0812"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize("62+81234567890"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> normalizer.normalize("++6281234567890"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
