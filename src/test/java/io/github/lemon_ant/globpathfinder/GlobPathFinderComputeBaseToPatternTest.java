package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.FileMatchingUtils.computeBaseToPattern;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for computeBaseToPattern(...) to cover:
 * 1) final fallback branch when includeGlobs are empty after trimming;
 * 2) collectingAndThen branch when MATCH_ALL is present for a base (empty set stored).
 */
class GlobPathFinderComputeBaseToPatternTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void computeBaseToPattern_blankIncludeGlobs_trimmedOut_returnsBaseMappedToEmptySet() {
        // given
        Path normalizedBaseDir = temporaryDirectory.toAbsolutePath().normalize();
        // All entries are blank and will be trimmed to null → filtered out → stream becomes empty
        Set<String> includeGlobPatterns = Set.of("", " ", "\t", "   ");

        // when
        Map<Path, Set<java.nio.file.PathMatcher>> baseToMatchers =
                computeBaseToPattern(normalizedBaseDir, includeGlobPatterns);

        // then
        // Same fallback branch as above
        assertThat(baseToMatchers)
                .as("All-blank patterns must be ignored; fallback returns base → empty set")
                .hasSize(1)
                .containsKey(normalizedBaseDir);
        assertThat(baseToMatchers.get(normalizedBaseDir)).isEmpty();
    }

    @Test
    void computeBaseToPattern_matchAllForGroupedBase_collapsesToEmptySet() {
        // given
        Path normalizedBaseDir = temporaryDirectory.toAbsolutePath().normalize();
        // "src" has no wildcards ⇒ extracted base = <baseDir>/src, tail = MATCH_ALL
        // The presence of MATCH_ALL in the grouped set must collapse the set to empty (our sentinel).
        Set<String> includeGlobPatterns = Set.of(
                "src", // no wildcards → MATCH_ALL under <baseDir>/src
                "src/**/*.java", // redundant once MATCH_ALL is present
                "src/**/impl/**" // redundant once MATCH_ALL is present
                );

        // when
        Map<Path, Set<java.nio.file.PathMatcher>> baseToMatchers =
                computeBaseToPattern(normalizedBaseDir, includeGlobPatterns);

        // then
        Path extractedGroupedBase = normalizedBaseDir.resolve("src").normalize();

        assertThat(baseToMatchers)
                .as("Only one grouped base ('src') is expected")
                .hasSize(1)
                .containsKey(extractedGroupedBase);

        assertThat(baseToMatchers.get(extractedGroupedBase))
                .as("Presence of MATCH_ALL must collapse matcher set to empty")
                .isEmpty();
    }
}
