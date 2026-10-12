package com.hls.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.settings.internal.SettingKey;
import com.hls.settings.internal.SettingKey.Group;
import org.junit.jupiter.api.Test;

/** Spec 011 T008: the catalog's ranges, parsing and group split. */
class SettingKeyTest {

    @Test
    void integersMustBeWholeNumbersInsideTheRange() {
        SettingKey min = SettingKey.PASSWORD_MIN_LENGTH;
        assertThat(min.normalize("8")).contains("8");
        assertThat(min.normalize(" 64 ")).contains("64");
        assertThat(min.normalize("010")).contains("10");
    }

    @Test
    void valuesOutsideTheRangeOrNotWholeAreRefused() {
        SettingKey min = SettingKey.PASSWORD_MIN_LENGTH;
        assertThat(min.normalize("7")).isEmpty();
        assertThat(min.normalize("65")).isEmpty();
        assertThat(min.normalize("10.5")).isEmpty();
        assertThat(min.normalize("-10")).isEmpty();
        assertThat(min.normalize("abc")).isEmpty();
        assertThat(min.normalize("")).isEmpty();
        assertThat(min.normalize(null)).isEmpty();
        assertThat(min.normalize("99999999999")).isEmpty();
    }

    @Test
    void theVersionNeedsThreeNumbers() {
        SettingKey version = SettingKey.MOBILE_MIN_APP_VERSION;
        assertThat(version.normalize("1.2.0")).contains("1.2.0");
        assertThat(version.normalize("01.02.003")).contains("1.2.3");
        assertThat(version.normalize("2")).isEmpty();
        assertThat(version.normalize("1.2")).isEmpty();
        assertThat(version.normalize("1.2.x")).isEmpty();
        assertThat(version.normalize("1.2.3.4")).isEmpty();
    }

    @Test
    void everyDefaultIsInsideItsOwnRange() {
        for (SettingKey key : SettingKey.values()) {
            assertThat(key.normalize(key.builtInDefault())).as(key.key()).isPresent();
        }
    }

    @Test
    void groupsSplitTheElevenSettingsAndKeysAreUnique() {
        assertThat(SettingKey.of(Group.SYSTEM)).hasSize(3);
        assertThat(SettingKey.of(Group.SECURITY)).hasSize(8);
        assertThat(SettingKey.values()).extracting(SettingKey::key).doesNotHaveDuplicates();
        assertThat(SettingKey.byKey("lockout.duration-minutes")).contains(SettingKey.LOCKOUT_DURATION_MINUTES);
        assertThat(SettingKey.byKey("nope")).isEmpty();
        assertThat(Group.parse("Security")).contains(Group.SECURITY);
        assertThat(Group.parse("business")).isEmpty();
    }
}
