package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileSystems;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Validation tests for glob patterns used by GlobPathFinder.
 * <p>
 * Policy:
 * - Truly malformed patterns should fail fast with IllegalArgumentException.
 * - Valid (even if unusual) patterns should not throw.
 * <p>
 * Notes:
 * - We emulate library validation via FileSystems.getDefault().getPathMatcher("glob:" + pattern).
 * - This matches how JDK parses glob-syntax before translating it to regex.
 */
class GlobPatternValidationTest {

    // ---------------------- Negative cases (must throw) ----------------------

    static Stream<String> malformedGlobProvider() {
        return Stream.of(
                // Unclosed character class
                "**.java[",
                // Unclosed alternatives block
                "{*.java,**/*.java",
                // Invalid range in character class
                "**.[z-a]",
                // Lone opening brace
                "*.{");
    }

    static Stream<String> validButWeirdGlobProvider() {
        return Stream.of(
                // Valid with proper "glob:" prefix handling (':' is just a literal here)
                "*:bad",
                // Reminder: **.java is valid; behavior nuance vs **/*.java is matching scope, not syntax
                "**.java",
                // Alternation with group is valid
                "{*.java,**/*.java}",
                // Mixed wildcards
                "**/*.*",
                // Simple classic
                "*.java");
    }

    // ---------------------- Positive cases (must NOT throw) ------------------

    /**
     * Validates a glob pattern by asking the default FS provider to compile a PathMatcher.
     * Throws IllegalArgumentException on any syntax problem with a clear message.
     */
    private static void validateGlobOrThrow(String pattern) {
        try {
            // Compile to trigger JDK glob parsing; we discard the matcher itself.
            FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (RuntimeException e) {
            // Normalize to IAE with the original cause and pattern echoed for clarity.
            throw new IllegalArgumentException("Invalid glob syntax: '" + pattern + "': " + e.getMessage(), e);
        }
    }

    @ParameterizedTest(name = "malformedGlobProvider[{index}] -> ''{0}'' should throw")
    @MethodSource("malformedGlobProvider")
    void validateGlobOrThrow_malformedGlob_shouldThrowIAE(String badGlob) {
        assertThatThrownBy(() -> validateGlobOrThrow(badGlob))
                .as("Pattern should be rejected: %s", badGlob)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(badGlob);
    }

    // ---------------------- Helper ----------------------

    @ParameterizedTest(name = "validButWeirdGlobProvider[{index}] -> ''{0}'' should be accepted")
    @MethodSource("validButWeirdGlobProvider")
    void validateGlobOrThrow_validButWeirdGlob_shouldNotThrow(String weirdButValid) {
        assertThatCode(() -> validateGlobOrThrow(weirdButValid))
                .as("Pattern should be accepted: %s", weirdButValid)
                .doesNotThrowAnyException();
    }
}
