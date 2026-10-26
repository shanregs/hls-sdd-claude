package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.reporting.internal.TileRegistry;
import com.hls.support.MasterDataTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spec 028 SC-003: every key in {@code contracts/tile-catalog.md} is registered and has a parity test; this fails when a tile
 * is added to the catalog (or registered) without one. A parity test is a test class named {@code *Tiles*Test} or
 * {@code *Comparison*Test} that names the tile key (a key family such as {@code managers.tasks.} counts when the class
 * builds the key from its prefix).
 */
class TileParityCoverageTest extends MasterDataTestBase {

    private static final Pattern ROW = Pattern.compile("^[|] [A-Za-z ]+ [|] ([a-zA-Z]+(?:[.][a-zA-Z]+)+) [|]");

    /** Keys whose parity test is named by class rather than by the key text. */
    private static final Map<String, String> BY_CLASS = Map.of("managers.comparison", "ManagerComparisonTest.java");

    @Autowired
    TileRegistry registry;

    private static Path root() {
        Path here = Path.of("").toAbsolutePath();
        return Files.isDirectory(here.resolve("specs")) ? here : here.getParent();
    }

    private static List<String> catalogKeys() throws IOException {
        Path catalog = root().resolve("specs/028-management-dashboard/contracts/tile-catalog.md");
        List<String> keys = new ArrayList<>();
        for (String line : Files.readAllLines(catalog, StandardCharsets.UTF_8)) {
            Matcher m = ROW.matcher(line);
            if (m.find()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }

    private static Map<Path, String> parityClasses() throws IOException {
        Path tests = root().resolve("backend/src/test/java");
        try (Stream<Path> files = Files.walk(tests)) {
            return files.filter(p -> p.getFileName().toString().matches(".*(Tiles|Comparison).*Test[.]java"))
                    .collect(Collectors.toMap(p -> p, p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }));
        }
    }

    @Test
    void theCatalogIsReadAndEveryKeyIsRegistered() throws IOException {
        List<String> keys = catalogKeys();
        assertThat(keys).hasSizeGreaterThanOrEqualTo(24).doesNotHaveDuplicates();
        Set<String> registered = registry.manifestFor(Set.of(Role.ADMIN)).stream()
                .map(e -> e.definition().key())
                .collect(Collectors.toSet());
        assertThat(registered).containsAll(keys);
    }

    @Test
    void everyRegisteredTileIsInTheCatalog() throws IOException {
        List<String> keys = catalogKeys();
        List<String> unlisted = registry.manifestFor(Set.of(Role.ADMIN)).stream()
                .map(e -> e.definition().key())
                .filter(k -> !k.startsWith("test.") && !k.startsWith("ext."))
                .filter(k -> !keys.contains(k))
                .toList();
        assertThat(unlisted).as("registered tiles missing from contracts/tile-catalog.md").isEmpty();
    }

    @Test
    void everyCatalogKeyHasAParityTestClass() throws IOException {
        Map<Path, String> classes = parityClasses();
        List<String> uncovered = new ArrayList<>();
        for (String key : catalogKeys()) {
            String family = key.substring(0, key.lastIndexOf('.') + 1) + "\"";
            String named = BY_CLASS.get(key);
            boolean covered = classes.entrySet().stream().anyMatch(e -> {
                String file = e.getKey().getFileName().toString();
                return (named != null && file.equals(named))
                        || e.getValue().contains("\"" + key + "\"")
                        || e.getValue().contains(key + "\"")
                        || e.getValue().contains("/" + key)
                        || e.getValue().contains(family);
            });
            if (!covered) {
                uncovered.add(key);
            }
        }
        assertThat(uncovered).as("catalog keys without a parity test class").isEmpty();
    }
}
