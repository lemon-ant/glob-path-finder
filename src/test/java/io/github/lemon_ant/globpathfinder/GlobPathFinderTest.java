package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlobPathFinderTest {

    @TempDir
    Path tmp;

    // --- helpers -------------------------------------------------------------

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

    private Set<String> collectToRelStringSet(Stream<Path> s, Path base) {
        // Compare results as paths relative to tmp for stability across OS/roots.
        try (s) {
            return s.map(path -> base.relativize(path).normalize().toString())
                    .map(path -> path.replace('\\', '/'))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    // --- tests ---------------------------------------------------------------

    @Test
    void findPaths_basicInclude_shouldReturnMatchingFiles() throws Exception {
        // given
        createFile("a/Main.java");
        createFile("a/Util.md");
        createFile("b/c/Nested.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp)
                .includeGlobs(Set.of("**/*.java"))
                .build();

        // when
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp);

        // then
        assertThat(result).containsExactlyInAnyOrder("a/Main.java", "b/c/Nested.java");
    }

    @Test
    void findPaths_extensionsFilter_shouldApplyCaseInsensitiveMatch() throws Exception {
        // given
        createFile("src/A.JAVA");
        createFile("src/B.java");
        createFile("src/C.txt");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("src"))
                .includeGlobs(Set.of("**/*"))
                .allowedExtensions(Set.of("java"))
                .build();

        // when
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("src"));

        // then
        assertThat(result).containsExactlyInAnyOrder("A.JAVA", "B.java");
    }

    @Test
    void findPaths_excludeGlobs_shouldExcludeMatchingPaths() throws Exception {
        // given
        createFile("src/gen/Generated.java");
        createFile("src/app/App.java");
        createFile("src/app/impl/Impl.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("src"))
                .includeGlobs(Set.of("**/*.java"))
                .excludeGlobs(Set.of("**/gen/**", "**/impl/**"))
                .build();

        // when
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("src"));

        // then
        assertThat(result).containsExactlyInAnyOrder("app/App.java");
    }

    @Test
    void findPaths_maxDepth_shouldLimitTraversal() throws Exception {
        // given
        createFile("src/L0.java");
        createFile("src/level1/L1.java");
        createFile("src/level1/level2/L2.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("src"))
                .includeGlobs(Set.of("**/*.java"))
                .maxDepth(1) // only src/* level
                .build();

        // when
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("src"));

        // then
        assertThat(result).containsExactlyInAnyOrder("L0.java");
    }

    @Test
    void findPaths_onlyFilesFalse_shouldAllowDirectoriesToo() throws Exception {
        // given
        createDir("d1/inner");
        createFile("d1/inner/X.txt");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp)
                .includeGlobs(Set.of("**/*")) // include everything
                .onlyFiles(false)
                .build();

        // when
        List<String> all =
                collectToRelStringSet(GlobPathFinder.findPaths(q), tmp).stream().toList();

        // then
        // Should contain both the directory and the file somewhere in the results.
        assertThat(all).anyMatch(p -> p.endsWith("d1/inner")).anyMatch(p -> p.endsWith("X.txt"));
    }

    @Test
    void findPaths_multipleIncludes_shouldDeduplicateResults() throws Exception {
        // given
        createFile("m/src/A.java");
        createFile("m/test/A.java");

        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("m"))
                .includeGlobs(Set.of("**/*.java", "src/**/*.java"))
                .build();

        // when
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp.resolve("m"));

        // then
        // No duplicates even if matched by both patterns.
        assertThat(result).containsExactlyInAnyOrder("src/A.java", "test/A.java");
    }

    @Test
    void findPaths_relativeAndAbsolutePatterns_shouldBothWork() throws Exception {
        // given
        Path absBase = createDir("abs");
        createFile("abs/One.java");
        createFile("abs/two/Two.java");

        String absPattern = absBase.toAbsolutePath().toString().replace('\\', '/') + "/**/*.java";

        PathQuery q = PathQuery.builder()
                .baseDir(tmp) // baseDir is tmp, but include has an absolute glob
                .includeGlobs(Set.of(absPattern, "**/two/*.java"))
                .build();

        // when
        Set<String> result = collectToRelStringSet(GlobPathFinder.findPaths(q), tmp);

        // then
        assertThat(result).containsExactlyInAnyOrder("abs/One.java", "abs/two/Two.java");
    }

    @Test
    void findPaths_streamMustBeClosed_shouldWorkWithTryWithResources() throws Exception {
        // given
        createFile("z/A.java");
        PathQuery q = PathQuery.builder()
                .baseDir(tmp.resolve("z"))
                .includeGlobs(Set.of("**/*.java"))
                .build();

        // when / then (no exceptions and results available)
        try (Stream<Path> s = GlobPathFinder.findPaths(q)) {
            assertThat(s.collect(Collectors.toSet())).hasSize(1);
        }
    }

    @Test
    void findPaths_returnsAbsolutePaths_shouldReturnRealAbsolutePaths() throws Exception {
        // given
        Path expectedFile = createFile("abscheck/F.java");
        Path baseDir = tmp.resolve("abscheck");

        PathQuery q = PathQuery.builder()
                .baseDir(baseDir)
                .includeGlobs(Set.of("**/*.java", "**/F.java")) // only one real file
                .allowedExtensions(Set.of("java"))
                .build();

        // when
        List<Path> actualPaths;
        try (Stream<Path> foundPaths = GlobPathFinder.findPaths(q)) {
            actualPaths = foundPaths.toList();
        }

        // then
        // Each returned path should equal the file's real absolute path.
        assertThat(actualPaths).containsExactly(expectedFile.toAbsolutePath());
    }
}
