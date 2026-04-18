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

    @Nested
    class QuestionMarkWildcard {

        @Test
        void matches_questionMark_matchesSingleChar() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("Fo?.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("Fox.java"))).isTrue();
        }

        @Test
        void matches_questionMark_doesNotMatchZeroChars() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("Fo?.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Fo.java"))).isFalse();
        }

        @Test
        void matches_questionMark_doesNotMatchTwoChars() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("Fo?.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Fooo.java"))).isFalse();
        }

        @Test
        void matches_multipleQuestionMarks_matchExactCount() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("?oo?ar.java");

            // When / Then
            assertThat(matcher.matches(Path.of("FooBar.java"))).isTrue();
            assertThat(matcher.matches(Path.of("FooBar.kt"))).isFalse();
        }

        @Test
        void matches_questionMarkInSubDir_matchesSingleCharInSegment() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("src/?oo/Bar.java");

            // When / Then
            assertThat(matcher.matches(Path.of("src/foo/Bar.java"))).isTrue();
            assertThat(matcher.matches(Path.of("src/fo/Bar.java"))).isFalse();
        }
    }

    @Nested
    class ConsecutiveStars {

        @Test
        void matches_consecutiveStarsInSegment_treatedAsSingleStar() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("Foo***.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("FooBar.java"))).isTrue();
        }

        @Test
        void matches_consecutiveStarsInSegment_doesNotCrossSegmentBoundary() {
            // Given - *** in a segment acts like * (single-segment wildcard), not like **
            PathMatcher matcher = AntStylePathMatcher.compile("com/***.java");

            // When / Then
            assertThat(matcher.matches(Path.of("com/Test.java"))).isTrue();
            assertThat(matcher.matches(Path.of("com/foo/Test.java"))).isFalse();
        }

        @Test
        void matches_multipleConsecutiveStarsWithinSegment_matchesAnyChars() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("Foo***.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("FooBar.java"))).isTrue();
            assertThat(matcher.matches(Path.of("FooBarBaz.java"))).isTrue();
        }
    }

    @Nested
    class MultipleDoubleStars {

        @Test
        void matches_twoDoubleStars_matchesInterleavedDirs() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/test/**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("test/Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("src/test/Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("src/test/sub/Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("a/b/test/c/d/Foo.java"))).isTrue();
        }

        @Test
        void matches_twoDoubleStars_doesNotMatchWrongExtension() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/test/**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("src/test/sub/Foo.kt"))).isFalse();
        }

        @Test
        void matches_twoDoubleStars_noSegmentBetween_matchesLongPath() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/**/*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("a/b/c/d/Foo.java"))).isTrue();
        }

        @Test
        void matches_threeDoubleStars_matchesDeepPath() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/a/**/b/**");

            // When / Then
            assertThat(matcher.matches(Path.of("a/b/file"))).isTrue();
            assertThat(matcher.matches(Path.of("x/a/y/b/z/file"))).isTrue();
            assertThat(matcher.matches(Path.of("a/b"))).isTrue();
        }

        @Test
        void matches_threeDoubleStars_doesNotMatchMissingRequiredSegment() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/a/**/b/**");

            // When / Then
            assertThat(matcher.matches(Path.of("x/y/b/file"))).isFalse();
            assertThat(matcher.matches(Path.of("a/x/y/file"))).isFalse();
        }
    }

    @Nested
    class MixedWildcards {

        @Test
        void matches_questionMarkAndSingleStar_combinedInSegment() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("?oo*.java");

            // When / Then
            assertThat(matcher.matches(Path.of("Foo.java"))).isTrue();
            assertThat(matcher.matches(Path.of("FooBar.java"))).isTrue();
            assertThat(matcher.matches(Path.of("oo.java"))).isFalse();
        }

        @Test
        void matches_doubleStarAndQuestionMark_inDifferentSegments() {
            // Given
            PathMatcher matcher = AntStylePathMatcher.compile("**/?.java");

            // When / Then
            assertThat(matcher.matches(Path.of("A.java"))).isTrue();
            assertThat(matcher.matches(Path.of("sub/A.java"))).isTrue();
            assertThat(matcher.matches(Path.of("Foo.java"))).isFalse();
        }
    }
}
