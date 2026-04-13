package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AntStylePathMatcherTest {

    @Nested
    class DoubleStarMatchesZeroDirectories {

        @Test
        void matches_doubleStarSlashWildcardExtension_matchesFileInCurrentDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
        }

        @Test
        void matches_doubleStarSlashWildcardExtension_matchesFileInSubDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("sub/Foo.java"))).isTrue();
        }

        @Test
        void matches_doubleStarSlashWildcardExtension_matchesFileInDeepSubDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("sub/sub2/Foo.java"))).isTrue();
        }

        @Test
        void matches_doubleStarSlashFilename_matchesFileInCurrentDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/Foo.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
        }

        @Test
        void matches_doubleStarSlashFilename_matchesFileInSubDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/Foo.java");

            // When / Then
            assertThat(matcher.matches(Path.of("sub/Foo.java"))).isTrue();
        }

        @Test
        void matches_middleDoubleStar_matchesWithZeroIntermediateDirs() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("com/**/Test.java");

            // When / Then
            assertThat(matcher.matches(Path.of("com/Test.java"))).isTrue();
        }

        @Test
        void matches_middleDoubleStar_matchesWithOneIntermediateDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("com/**/Test.java");

            // When / Then
            assertThat(matcher.matches(Path.of("com/foo/Test.java"))).isTrue();
        }

        @Test
        void matches_middleDoubleStar_matchesWithMultipleIntermediateDirs() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("com/**/Test.java");

            // When / Then
            assertThat(matcher.matches(Path.of("com/foo/bar/Test.java"))).isTrue();
        }
    }

    @Nested
    class BasicPatterns {

        @Test
        void matches_singleStar_matchesFileInCurrentDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
        }

        @Test
        void matches_singleStar_doesNotMatchFileInSubDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("sub/Foo.java"))).isFalse();
        }

        @Test
        void matches_doubleStar_matchesAnything() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**");

            // When / Then
            assertThat(matcher.matches(Path.of("anything"))).isTrue();
            assertThat(matcher.matches(Path.of("sub/anything"))).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"Foo.kt", "sub/Foo.kt", "Foo.txt"})
        void matches_wildcardExtension_doesNotMatchDifferentExtension(String nonMatchingPath) {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of(nonMatchingPath))).isFalse();
        }
    }

    @Nested
    class ExcludePatterns {

        @Test
        void matches_doubleStarSlashDirDoubleStar_matchesFileInThatDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/test/**");

            // When / Then
            assertThat(matcher.matches(Path.of("test/Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("src/test/Foo.java"))).isTrue();
        }

        @Test
        void matches_dirSlashDoubleStar_matchesNestedFiles() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("gen/**");

            // When / Then
            assertThat(matcher.matches(Path.of("gen/Generated.java"))).isTrue();
            assertThat(matcher.matches(Path.of("gen/sub/Generated.java"))).isTrue();
        }

        @Test
        void matches_dirSlashDoubleStar_doesNotMatchOtherDir() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("gen/**");

            // When / Then
            assertThat(matcher.matches(Path.of("src/Generated.java"))).isFalse();
        }
    }
}
