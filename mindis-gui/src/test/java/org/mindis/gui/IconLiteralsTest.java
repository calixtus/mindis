package org.mindis.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.kordamp.ikonli.materialdesign2.MaterialDesignA;

import org.junit.jupiter.api.Test;

/// Every Ikonli literal written in the sources must name a real glyph.
///
/// A mistyped one is invisible until the control is built, and then it throws -
/// so a sidebar entry or a toolbar button can be broken by a typo that compiles,
/// passes review and only shows up when someone opens that screen. Resolving
/// them here needs no JavaFX toolkit, so unlike the tests built on
/// [FxTest] this one also runs on a headless CI machine.
class IconLiteralsTest {

    /// Ikonli literals are `<pack prefix>-<glyph>`, e.g. `mdi2c-church`.
    private static final Pattern ICON_LITERAL = Pattern.compile("\"(mdi2[a-z]-[a-z0-9-]+)\"");

    @Test
    void everyIconLiteralResolves() throws IOException {
        Set<String> known = knownLiterals();
        List<String> unresolved = findIconLiterals().stream()
                .filter(literal -> !known.contains(literal))
                .distinct()
                .sorted()
                .toList();
        assertEquals(List.of(), unresolved,
                "These Ikonli literals name no glyph in the packs on the module path: " + unresolved);
    }

    /// Every glyph the materialdesign2 pack declares, read off the enums themselves.
    ///
    /// Ikonli's own `IkonResolver` would answer this directly, but it lives in
    /// `org.kordamp.ikonli.core`, which this module has no reason to require
    /// otherwise. The pack classes are one per initial letter, and not every letter
    /// has one; `getDescription()` is called reflectively so that no `Ikon` type -
    /// also from core - has to be named here.
    private static Set<String> knownLiterals() {
        Module pack = MaterialDesignA.class.getModule();
        Set<String> known = new HashSet<>();
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            Class<?> type = Class.forName(pack, "org.kordamp.ikonli.materialdesign2.MaterialDesign" + letter);
            if (type == null) {
                continue;
            }
            for (Object constant : type.getEnumConstants()) {
                try {
                    known.add((String) type.getMethod("getDescription").invoke(constant));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot read the glyph names of " + type, e);
                }
            }
        }
        return known;
    }

    private static List<String> findIconLiterals() throws IOException {
        // The :gui test task runs with mindis-gui as its working directory.
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                        .flatMap(IconLiteralsTest::literalsIn)
                        .toList();
        }
    }

    private static Stream<String> literalsIn(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Matcher matcher = ICON_LITERAL.matcher(content);
        return matcher.results().map(result -> result.group(1)).toList().stream();
    }
}
