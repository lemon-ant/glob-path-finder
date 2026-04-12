package io.github.lemon_ant.globpathfinder;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class GlobPathFinderTest {

    @TempDir
    Path tempDir;

    @Test
    void findPaths_basicInclude_returnsMatchingFiles() throws Exception {
        // Given
        createFile("a/Main.java");
        createFile("a/Util.md");
        createFile("b/c/Nested.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*.java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir);

        // Then
        assertThat(result).containsExactlyInAnyOrder("a/Main.java", "b/c/Nested.java");
    }

    @Test
    void findPaths_excludeGlobs_excludesMatchingPaths() throws Exception {
        // Given
        createFile("src/gen/Generated.java");
        createFile("src/app/App.java");
        createFile("src/app/impl/Impl.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("src"))
                .includeGlobs(Set.of("**/*.java"))
                .excludeGlobs(Set.of("gen/**", "**/impl/**"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("app/App.java");
    }

    @Test
    void findPaths_extensionsFilter_appliesCaseInsensitiveFilter() throws Exception {
        // Given
        createFile("src/A.JAVA");
        createFile("src/B.java");
        createFile("src/C.txt");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("src"))
                .includeGlobs(Set.of("**"))
                .allowedExtensions(Set.of("java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("A.JAVA", "B.java");
    }

    @Test
    void findPaths_maxDepth_limitsTraversal() throws Exception {
        // Given
        createFile("src/L0.java");
        createFile("src/level1/L1.java");
        createFile("src/level1/level2/L2.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("src"))
                .includeGlobs(Set.of("**.java"))
                .maxDepth(1) // only src/* level
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("L0.java");
    }

    @Test
    void findPaths_multipleIncludes_deduplicatesResults() throws Exception {
        // Given
        createFile("m/src/A.java");
        createFile("m/test/A.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("m"))
                .includeGlobs(Set.of("**/*.java", "src/**/*.java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("m"));

        // Then
        // No duplicates even if matched by both patterns.
        assertThat(result).containsExactlyInAnyOrder("src/A.java", "test/A.java");
    }

    @Test
    void findPaths_onlyFilesFalse_includesDirectories() throws Exception {
        // Given
        createDir("d1/inner");
        createFile("d1/inner/X.txt");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*")) // include everything
                .onlyFiles(false)
                .build();

        // When
        List<String> all = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir).stream()
                .collect(toUnmodifiableList());

        // Then
        // Should contain both the directory and the file somewhere in the results.
        assertThat(all).anyMatch(path -> path.endsWith("d1/inner")).anyMatch(path -> path.endsWith("X.txt"));
    }

    @Test
    void findPaths_relativeAndAbsolutePatterns_bothMatch() throws Exception {
        // Given
        Path absBase = createDir("abs");
        createFile("abs/One.java");
        createFile("abs/two/Two.java");

        String absPattern = absBase.toAbsolutePath().toString().replace('\\', '/') + "**.java";

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir) // baseDir is tempDir, but include has an absolute glob
                .includeGlobs(Set.of(absPattern, "**/two/*.java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir);

        // Then
        assertThat(result).containsExactlyInAnyOrder("abs/One.java", "abs/two/Two.java");
    }

    @Test
    void findPaths_normalizedBase_returnsAbsolutePaths() throws Exception {
        // Given
        Path expectedFile = createFile("abscheck/F.java");
        Path baseDir = tempDir.resolve("abscheck");

        PathQuery query = PathQuery.builder()
                .baseDir(baseDir)
                .includeGlobs(Set.of("**/*.java", "F.java")) // only one real file
                .allowedExtensions(Set.of("java"))
                .build();

        // When
        List<Path> actualPaths;
        try (Stream<Path> foundPaths = GlobPathFinder.findPaths(query)) {
            actualPaths = foundPaths.collect(toUnmodifiableList());
        }

        // Then
        // Each returned path should equal the file's real absolute path.
        assertThat(actualPaths).containsExactly(expectedFile.toAbsolutePath());
    }

    @Test
    void findPaths_closedViaResources_completesNormally() throws Exception {
        // Given
        createFile("z/A.java");
        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*.java"))
                .build();

        // When / Then (no exceptions and results available)
        try (Stream<Path> pathStream = GlobPathFinder.findPaths(query)) {
            assertThat(pathStream.collect(Collectors.toSet())).hasSize(1);
        }
    }

    @Test
    void findPaths_nullQuery_throwsNullPointerException() {
        // When / Then
        assertThatThrownBy(() -> GlobPathFinder.findPaths(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void findPaths_defaultSettings_returnsAllFiles() throws Exception {
        // Given
        createFile("a/One.java");
        createFile("b/Two.txt");
        PathQuery query = PathQuery.builder().baseDir(tempDir).build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir);

        // Then
        assertThat(result).containsExactlyInAnyOrder("a/One.java", "b/Two.txt");
    }

    @Test
    void findPaths_matchAllIncludeWithRelativeExcludes_excludesMatches() throws Exception {
        // Given – empty includeGlobs (MATCH_ALL for the base) + a relative exclude
        createFile("src/Keep.java");
        createFile("src/gen/Skip.java");
        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("src"))
                .excludeGlobs(Set.of("gen/**"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("Keep.java");
    }

    @Test
    void findPaths_allowedExtensionsSet_excludesNoExtensionFile() throws Exception {
        // Given
        createFile("ext/Hello.java");
        createFile("ext/README");
        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("ext"))
                .allowedExtensions(Set.of("java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("ext"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("Hello.java");
    }

    @Test
    void findPaths_traceLoggingEnabled_returnsFilteredResults() throws Exception {
        // Given
        createFile("trc/sub/App.java");
        createFile("trc/gen/Gen.java");
        Logger logger = (Logger) LoggerFactory.getLogger(GlobPathFinder.class);
        Level original = logger.getLevel();
        logger.setLevel(Level.TRACE);
        String absExclude = tempDir.resolve("trc/gen")
                        .toAbsolutePath()
                        .normalize()
                        .toString()
                        .replace('\\', '/') + "/**";
        PathQuery query = PathQuery.builder()
                .baseDir(tempDir.resolve("trc"))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("gen/**", absExclude))
                .build();

        // When
        Set<String> result;
        try {
            result = collectToRelStringSet(GlobPathFinder.findPaths(query), tempDir.resolve("trc"));
        } finally {
            logger.setLevel(original);
        }

        // Then
        assertThat(result).containsExactlyInAnyOrder("sub/App.java");
    }

    private Set<String> collectToRelStringSet(Stream<Path> pathStream, Path base) {
        // Compare results as paths relative to the passed base for stability across OS/roots.
        try (pathStream) {
            return pathStream
                    .map(path -> base.relativize(path).normalize().toString())
                    .map(path -> path.replace('\\', '/'))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private Path createDir(String relativePath) throws IOException {
        Path directoryPath = tempDir.resolve(relativePath);
        Files.createDirectories(directoryPath);
        return directoryPath;
    }

    private Path createFile(String relativePath) throws IOException {
        Path filePath = tempDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        return Files.createFile(filePath);
    }
}
