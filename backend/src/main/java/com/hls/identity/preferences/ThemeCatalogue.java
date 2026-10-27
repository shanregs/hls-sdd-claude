package com.hls.identity.preferences;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

/** The preset catalogue (spec 039): one JSON resource, served to both clients, the single source of truth. */
@Component
public class ThemeCatalogue {

    public static final String DEFAULT_PRESET = "classic";

    private final Map<String, Object> document;

    public ThemeCatalogue() {
        try (InputStream in = getClass().getResourceAsStream("/themes/theme-presets.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            this.document = JsonParserFactory.getJsonParser().parseMap(json);
        } catch (IOException e) {
            throw new IllegalStateException("theme-presets.json missing", e);
        }
    }

    /** The whole document: version, presets, statusChips. */
    public Map<String, Object> document() {
        return document;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> presets() {
        return (List<Map<String, Object>>) document.get("presets");
    }

    public Optional<Map<String, Object>> preset(String id) {
        return presets().stream().filter(p -> id.equals(p.get("id"))).findFirst();
    }

    /** The colour set of one variant, for example {@code variant("classic", true)}. */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, String>> variant(String presetId, boolean dark) {
        return preset(presetId).map(p -> (Map<String, String>) p.get(dark ? "dark" : "light"));
    }
}
