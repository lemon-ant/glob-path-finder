package io.github.lemon_ant.globpathfinder;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlobPathFinderTest {

    @TempDir
    Path tmp;

    @Test
    void findPaths_basicInclude_returnsMatchingFiles() throws Exception {
        // Given
        createFile("a/Main.java");
        createFile("a/Util.md");
        createFile("b/c/Nested.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp)
                .includeGlobs(Set.of("**/*.java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp);

        // Then
        assertThat(result).containsExactlyInAnyOrder("a/Main.java", "b/c/Nested.java");
    }

    @Test
    void findPaths_excludeGlobs_excludesMatchingPaths() throws Exception {
        // Given
        createFile("src/gen/Generated.java");
        createFile("src/app/App.java");
        createFile("src/app/impl/Impl.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("src"))
                .includeGlobs(Set.of("**/*.java"))
                .excludeGlobs(Set.of("gen/**", "**/impl/**"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("app/App.java");
    }

    @Test
    void findPaths_extensionsFilter_appliesCaseInsensitiveFilter() throws Exception {
        // Given
        createFile("src/A.JAVA");
        createFile("src/B.java");
        createFile("src/C.txt");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("src"))
                .includeGlobs(Set.of("**"))
                .allowedExtensions(Set.of("java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("A.JAVA", "B.java");
    }

    @Test
    void findPaths_maxDepth_limitsTraversal() throws Exception {
        // Given
        createFile("src/L0.java");
        createFile("src/level1/L1.java");
        createFile("src/level1/level2/L2.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("src"))
                .includeGlobs(Set.of("**.java"))
                .maxDepth(1) // only src/* level
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("src"));

        // Then
        assertThat(result).containsExactlyInAnyOrder("L0.java");
    }

    @Test
    void findPaths_multipleIncludes_deduplicatesResults() throws Exception {
        // Given
        createFile("m/src/A.java");
        createFile("m/test/A.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("m"))
                .includeGlobs(Set.of("**/*.java", "src/**/*.java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("m"));

        // Then
        // No duplicates even if matched by both patterns.
        assertThat(result).containsExactlyInAnyOrder("src/A.java", "test/A.java");
    }

    @Test
    void findPaths_onlyFilesFalse_includesDirectories() throws Exception {
        // Given
        createDir("d1/inner");
        createFile("d1/inner/X.txt");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp)
                .includeGlobs(Set.of("**/*")) // include everything
                .onlyFiles(false)
                .build();

        // When
        List<String> all =
                collectToRelStringSet(GlobPathFinder.findPaths(q), tmp).stream().collect(toUnmodifiableList());

        // Then
        // Should contain both the directory and the file somewhere in the results.
        assertThat(all).anyMatch(p -> p.endsWith("d1/inner")).anyMatch(p -> p.endsWith("X.txt"));
    }

    @Test
    void findPaths_relativeAndAbsolutePatterns_bothMatch() throws Exception {
        // Given
        Path absBase = createDir("abs");
        createFile("abs/One.java");
        createFile("abs/two/Two.java");

        String absPattern = absBase.toAbsolutePath().toString().replace('\\', '/') + "**.java";

        PathQuery q = PathQuery.builder()
                .baseDir(tmp) // baseDir is tmp, but include has an absolute glob
                .includeGlobs(Set.of(absPattern, "**/two/*.java"))
                .build();

        // When
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp);

        // Then
        assertThat(result).containsExactlyInAnyOrder("abs/One.java", "abs/two/Two.java");
    }

    @Test
    void findPaths_normalizedBase_returnsAbsolutePaths() throws Exception {
        // Given
        Path expectedFile = createFile("abscheck/F.java");
        Path baseDir = tmp.resolve("abscheck");

        PathQuery q = PathQuery.builder()
                .baseDir(baseDir)
                .includeGlobs(Set.of("**/*.java", "F.java")) // only one real file
                .allowedExtensions(Set.of("java"))
                .build();

        // When
        List<Path> actualPaths;
        try (Stream<Path> foundPaths = GlobPathFinder.findPaths(q)) {
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
        PathQuery q = PathQuery.builder()
                .baseDir(tmp)
                .includeGlobs(Set.of("**/*.java"))
                .build();

        // When / Then (no exceptions and results available)
        try (Stream<Path> s = GlobPathFinder.findPaths(q)) {
            assertThat(s.collect(Collectors.toSet())).hasSize(1);
        }
    }

    private Set<String> collectToRelStringSet(Stream<Path> s, Path base) {
        // Compare results as paths relative to tmp for stability across OS/roots.
        try (s) {
            return s.map(path -> base.relativize(path).normalize().toString())
                    .map(path -> path.replace('\\', '/'))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private Path createDir(String rel) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p);
        return p;
    }

    private Path createFile(String rel) throws IOException {
        Path p = tmp.resolve(rel);
        Files.createDirectories(p.getParent());
        return Files.createFile(p);
    }
}
