package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileMatchingUtilsTest {

    // ---------- composePattern ----------
    @Test
    void composePattern_emptyRest_returnsNull() throws Exception {
        String[] pathSegments = {"a", "b"};

        String result = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "composePattern", 2, pathSegments, false);

        assertThat(result).isNull();
    }

    @Test
    void composePattern_withoutTrailingSlash_buildsSlashSeparated() throws Exception {
        String[] pathSegments = {"a", "b", "*.txt"};

        String result = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "composePattern", 1, pathSegments, false);

        assertThat(result).isEqualTo("b/*.txt");
    }

    @Test
    void composePattern_withTrailingSlash_appendsSlashAtEnd() throws Exception {
        String[] pathSegments = {"a", "b"};

        String result = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "composePattern", 0, pathSegments, true);

        assertThat(result).isEqualTo("a/b/");
    }

    // ---------- isWildcardSegment ----------
    @ParameterizedTest
    @ValueSource(strings = {"*", "?.txt", "file{1,2}.log", "a[b]"})
    void isWildcardSegment_containsMeta_returnsTrue(String segment) throws Exception {
        Boolean result =
                ReflectiveMethodInvoker.invokePrivateStatic(FileMatchingUtils.class, "isWildcardSegment", segment);

        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "logs", "2025"})
    void isWildcardSegment_plain_returnsFalse(String segment) throws Exception {
        Boolean result =
                ReflectiveMethodInvoker.invokePrivateStatic(FileMatchingUtils.class, "isWildcardSegment", segment);

        assertThat(result).isFalse();
    }

    // ---------- extractBaseAndPattern ----------
    @Test
    void extractBaseAndPattern_absolutePrefix_stopsBeforeWildcard() throws Exception {
        Path defaultBaseDirectory = Path.of(".").toAbsolutePath().normalize();
        String globExpression = "/var/log/nginx/*.log";

        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        assertThat(resultPair.getLeft().toString())
                .endsWith(Paths.get("/var/log/nginx").toString());
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("error.log"))).isTrue();
        assertThat(matcher.matches(Paths.get("sub/error.log"))).isFalse();
    }

    @Test
    void extractBaseAndPattern_relativeBase_usesDefaultBase() throws Exception {
        Path defaultBaseDirectory =
                Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        String globExpression = "src/main/java/**/*.java";

        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory.resolve("src/main/java"));
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("main/src/java/A.java"))).isTrue();
        assertThat(matcher.matches(Paths.get("main/src/kotlin/A.kt"))).isFalse();
    }

    @Test
    void extractBaseAndPattern_trailingSlash_emptyPattern_yieldsMatchAllMatcher() throws Exception {
        Path basePath = Path.of("/opt/data");
        Path defaultBaseDirectory = basePath.toAbsolutePath();
        String globExpression = "/opt/data/";

        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        assertThat(resultPair.getLeft().toString()).endsWith(basePath.toString());
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("anything"))).isTrue();
        assertThat(matcher.matches(Paths.get("sub/dir/file"))).isTrue();
    }

    @Test
    void extractBaseAndPattern_backslashesAreNormalized() throws Exception {
        Path defaultBaseDirectory = Path.of(".").toAbsolutePath().normalize();
        String globExpression = "src\\**\\*.java";

        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        assertThat(resultPair.getLeft().toString()).endsWith(Paths.get("src").toString());
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("main/src/java/A.java"))).isTrue();
        assertThat(matcher.matches(Paths.get("main/src/kotlin/B.kt"))).isFalse();
    }

    // ---------- extra coverage ----------
    @Test
    void extractBaseAndPattern_relativeStartsWithWildcard_baseRemainsDefault() throws Exception {
        Path defaultBaseDirectory = Path.of("/tmp").toAbsolutePath().normalize();
        String globExpression = "**/*.log";

        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory);
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("tmp/test.log"))).isTrue();
        assertThat(matcher.matches(Paths.get("nested/test.log"))).isTrue();
    }

    @Test
    void extractBaseAndPattern_rootPathWithNoPattern_matchesAll() throws Exception {
        Path defaultBaseDirectory = Path.of("/").toAbsolutePath().normalize();
        String globExpression = "/";

        Pair<Path, PathMatcher> resultPair = ReflectiveMethodInvoker.invokePrivateStatic(
                FileMatchingUtils.class, "extractBaseAndPattern", defaultBaseDirectory, globExpression);

        assertThat(resultPair.getLeft()).isEqualTo(defaultBaseDirectory);
        PathMatcher matcher = resultPair.getRight();
        assertThat(matcher.matches(Paths.get("anything"))).isTrue();
    }
}
