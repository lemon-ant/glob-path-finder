package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileMatchingUtilsTest {

    // ---------- composePattern ----------
    @Test
    void composePattern_emptyRest_returnsNull() throws Exception {
        // Given
        String[] pathSegments = {"a", "b"};

        // When
        String result = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "composePattern", 2, pathSegments, false);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void composePattern_withTrailingSlash_appendsSlashAtEnd() throws Exception {
        // Given
        String[] pathSegments = {"a", "b"};

        // When
        String result = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "composePattern", 0, pathSegments, true);

        // Then
        assertThat(result).isEqualTo("a/b/");
    }

    @Test
    void composePattern_withoutTrailingSlash_buildsSlashSeparated() throws Exception {
        // Given
        String[] pathSegments = {"a", "b", "*.txt"};

        // When
        String result = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "composePattern", 1, pathSegments, false);

        // Then
        assertThat(result).isEqualTo("b/*.txt");
    }

    // ---------- extractBaseAndPattern ----------
    @Test
    void extractBaseAndPattern_absolutePrefix_stopsBeforeWildcard() throws Exception {
        // Given
        Path defaultBaseDirectory = Path.of(".").toAbsolutePath().normalize();
        String globExpression = "/var/log/nginx/*.log";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft().toString())
                .endsWith(Paths.get("/var/log/nginx").toString());
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("error.log"))).isTrue();
        assertThat(matcher.matches(Paths.get("sub/error.log"))).isFalse();
    }

    @Test
    void extractBaseAndPattern_backslashesAreNormalized() throws Exception {
        // Given
        Path defaultBaseDirectory = Path.of(".").toAbsolutePath().normalize();
        String globExpression = "src\\**\\*.java";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft().toString()).endsWith(Paths.get("src").toString());
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("main/src/java/A.java"))).isTrue();
        assertThat(matcher.matches(Paths.get("main/src/kotlin/B.kt"))).isFalse();
    }

    @Test
    void extractBaseAndPattern_relativeBase_usesDefaultBase() throws Exception {
        // Given
        Path defaultBaseDirectory =
                Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        String globExpression = "src/main/java/**/*.java";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory.resolve("src/main/java"));
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("main/src/java/A.java"))).isTrue();
        assertThat(matcher.matches(Paths.get("main/src/kotlin/A.kt"))).isFalse();
    }

    // ---------- extra coverage ----------
    @Test
    void extractBaseAndPattern_relativeStartsWithWildcard_baseRemainsDefault() throws Exception {
        // Given
        Path defaultBaseDirectory = Path.of("/tmp").toAbsolutePath().normalize();
        String globExpression = "**/*.log";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory);
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("tmp/test.log"))).isTrue();
        assertThat(matcher.matches(Paths.get("nested/test.log"))).isTrue();
    }

    @Test
    void extractBaseAndPattern_doubleStarWildcard_matchesFileInBaseDir() throws Exception {
        // Given
        Path defaultBaseDirectory = Path.of("/tmp").toAbsolutePath().normalize();
        String globExpression = "**/*.java";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("Foo.java")))
                .as("** should match zero directories (Ant/Maven convention)")
                .isTrue();
        assertThat(matcher.matches(Paths.get("sub/Bar.java"))).isTrue();
        assertThat(matcher.matches(Paths.get("a/b/Baz.java"))).isTrue();
    }

    @Test
    void extractBaseAndPattern_pathWithBracketSegment_treatsBracketsAsLiteral() throws Exception {
        // Given
        Path defaultBaseDirectory = Path.of("/tmp").toAbsolutePath().normalize();
        String globExpression = "src/a[b]/**/*.java";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory.resolve("src/a[b]"));
        assertThat(resultPair.getRight().matches(Paths.get("Foo.java"))).isTrue();
        assertThat(resultPair.getRight().matches(Paths.get("nested/Foo.java"))).isTrue();
    }

    @Test
    void extractBaseAndPattern_rootPathWithNoPattern_matchesAll() throws Exception {
        // Given
        Path defaultBaseDirectory = Path.of("/").toAbsolutePath().normalize();
        String globExpression = "/";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory);
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("anything"))).isTrue();
    }

    @Test
    void extractBaseAndPattern_trailingSlash_emptyPattern_yieldsMatchAllMatcher() throws Exception {
        // Given
        Path basePath = Path.of("/opt/data");
        Path defaultBaseDirectory = basePath.toAbsolutePath();
        String globExpression = "/opt/data/";

        // When
        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        // Then
        assertThat(resultPair.getLeft().toString()).endsWith(basePath.toString());
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("anything"))).isTrue();
        assertThat(matcher.matches(Paths.get("sub/dir/file"))).isTrue();
    }

    // ---------- isWildcardSegment ----------
    @ParameterizedTest
    @ValueSource(strings = {"*", "?.txt", "**", "*.java"})
    void isWildcardSegment_containsMeta_returnsTrue(String segment) throws Exception {
        // When
        Boolean result =
                ReflectiveMethodInvoker.invokePrivateStatic(FileMatchingUtils.class, "isWildcardSegment", segment);

        // Then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file{1,2}.log", "a[b]"})
    void isWildcardSegment_nonWildcardMetacharacters_returnsFalse(String segment) throws Exception {
        // When
        Boolean result =
                ReflectiveMethodInvoker.invokePrivateStatic(FileMatchingUtils.class, "isWildcardSegment", segment);

        // Then
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "logs", "2025"})
    void isWildcardSegment_plain_returnsFalse(String segment) throws Exception {
        // When
        Boolean result =
                ReflectiveMethodInvoker.invokePrivateStatic(FileMatchingUtils.class, "isWildcardSegment", segment);

        // Then
        assertThat(result).isFalse();
    }
}
