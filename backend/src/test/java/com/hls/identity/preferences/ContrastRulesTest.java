package com.hls.identity.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

/** Spec 039: the contrast rule against the shared vectors, and every shipped preset variant (SC-002). */
class ContrastRulesTest {

    @Test
    @SuppressWarnings("unchecked")
    void matchesTheSharedVectors() throws Exception {
        Path file = Path.of("..", "specs", "039-user-themes", "contracts", "contrast-vectors.json");
        Map<String, Object> doc = JsonParserFactory.getJsonParser().parseMap(Files.readString(file));
        List<Map<String, Object>> cases = (List<Map<String, Object>>) doc.get("cases");
        assertThat(cases).isNotEmpty();
        for (Map<String, Object> c : cases) {
            boolean dark = "dark".equals(c.get("variant"));
            Map<String, String> v = new ThemeCatalogue().variant("classic", dark).orElseThrow();
            String accent = (String) c.get("accent");
            String label = accent + " " + c.get("variant");
            assertThat(ContrastRules.round2(ContrastRules.ratio(accent, v.get("surface"))))
                    .as(label)
                    .isEqualTo(((Number) c.get("accentOnSurface")).doubleValue());
            boolean pass = ContrastRules.passes(accent, v.get("surface"), v.get("background"));
            assertThat(pass).as(label).isEqualTo(c.get("pass"));
            String suggested = pass ? accent : ContrastRules.adjust(accent, dark, v.get("surface"), v.get("background"));
            assertThat(suggested).as(label).isEqualTo(c.get("suggested"));
        }
    }

    @Test
    void everyPresetVariantIsReadable() {
        ThemeCatalogue catalogue = new ThemeCatalogue();
        assertThat(catalogue.presets()).hasSize(4);
        for (Map<String, Object> preset : catalogue.presets()) {
            for (boolean dark : new boolean[] {false, true}) {
                Map<String, String> v = catalogue.variant((String) preset.get("id"), dark).orElseThrow();
                String label = preset.get("id") + (dark ? " dark" : " light");
                for (String text : List.of("text", "textMuted")) {
                    assertThat(ContrastRules.ratio(v.get(text), v.get("surface"))).as(label + text).isGreaterThanOrEqualTo(4.5);
                    assertThat(ContrastRules.ratio(v.get(text), v.get("background"))).as(label + text).isGreaterThanOrEqualTo(4.5);
                }
                for (String colour : List.of("primary", "accent", "error")) {
                    assertThat(ContrastRules.ratio(v.get(colour), v.get("surface"))).as(label + colour).isGreaterThanOrEqualTo(4.5);
                    assertThat(ContrastRules.ratio(v.get(colour), v.get("background"))).as(label + colour).isGreaterThanOrEqualTo(4.5);
                }
                assertThat(ContrastRules.ratio(v.get("onPrimary"), v.get("primary"))).as(label + " header").isGreaterThanOrEqualTo(4.5);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void statusChipsAreReadable() {
        Map<String, Object> chips = (Map<String, Object>) new ThemeCatalogue().document().get("statusChips");
        for (String variant : List.of("light", "dark")) {
            Map<String, Map<String, String>> set = (Map<String, Map<String, String>>) chips.get(variant);
            assertThat(set).containsKeys("approved", "pending", "rejected");
            set.forEach((name, c) -> assertThat(ContrastRules.ratio(c.get("text"), c.get("background")))
                    .as(variant + " " + name).isGreaterThanOrEqualTo(4.5));
        }
    }
}
