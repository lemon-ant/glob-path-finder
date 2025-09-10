package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Extra end-to-end scenarios distilled from older j-harmonizer tests.
 * Focus: absolute excludes, "include == exclude" emptiness, no-match includes,
 * single-file absolute include, Windows backslashes, symlink traversal (including cycle).
 */
@Slf4j
class GlobPathFinderAdditionalScenariosTest {

    @TempDir
    Path tempDir;

    // -------------------- helpers --------------------

    private static String absGlob(Path path, String tailGlob) {
        // Build a portable absolute glob string using forward slashes
        String prefix = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (!tailGlob.startsWith("/")) tailGlob = "/" + tailGlob;
        return prefix + tailGlob;
    }

    private static Set<Path> toAbsoluteNormalizedSet(Stream<Path> stream) {
        return stream.map(p -> p.toAbsolutePath().normalize()).collect(Collectors.toSet());
    }

    private static Path writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    // -------------------- tests ----------------------

    @Test
    void absoluteExclude_filtersOutAbsoluteTargetTree() throws IOException {
        // given
        Path src = writeFile(tempDir.resolve("src/A.java"), "class A {}");
        Path target = writeFile(tempDir.resolve("target/T.java"), "class T {}");

        String include = absGlob(tempDir, "/**/*.java");
        String absoluteExclude = absGlob(tempDir.resolve("target"), "/**");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir) // base is irrelevant for absolute includes, but harmless
                .includeGlobs(Set.of(include))
                .excludeGlobs(Set.of(absoluteExclude))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // when
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = toAbsoluteNormalizedSet(s);
        }

        // then: src file present, target file excluded
        assertThat(result)
                .contains(src.toAbsolutePath().normalize())
                .doesNotContain(target.toAbsolutePath().normalize());
    }

    @Test
    void includeAndExcludeSamePattern_yieldsEmpty() throws IOException {
        // given
        writeFile(tempDir.resolve("src/B.java"), "class B {}");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*.java"))
                .excludeGlobs(Set.of("**/*.java"))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // when
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
                result.set(s.collect(Collectors.toUnmodifiableList()));
            }
        });

        // then
        assertThat(result.get()).isEmpty();
    }

    @Test
    void includeWithNoMatches_returnsEmpty() throws IOException {
        // given
        writeFile(tempDir.resolve("src/C.java"), "class C {}");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*.kt")) // no Kotlin files here
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // when
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
                result.set(s.collect(Collectors.toUnmodifiableList()));
            }
        });

        // then
        assertThat(result.get()).isEmpty();
    }

    @Test
    void singleFileAbsoluteInclude_findsThatFileOnly() throws IOException {
        // given
        Path single = writeFile(tempDir.resolve("single/App.java"), "class App {}");

        // absolute include without wildcards
        String include = single.toAbsolutePath().normalize().toString().replace('\\', '/');

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(include))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // when
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = toAbsoluteNormalizedSet(s);
        }

        // then
        assertThat(result).containsExactly(single.toAbsolutePath().normalize());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsBackslashInclude_works() throws IOException {
        // given
        Path dir = Files.createDirectories(tempDir.resolve("win"));
        Path javaFile = writeFile(dir.resolve("WinTest.java"), "class WinTest {}");

        // include with backslashes (Windows-style)
        String include = dir.toAbsolutePath().normalize() + "\\*.java";

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(include))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // when
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = toAbsoluteNormalizedSet(s);
        }

        // then
        assertThat(result).containsExactly(javaFile.toAbsolutePath().normalize());
    }

    @Test
    void followLinks_true_findsThroughSymlink_posixOnly() throws Exception {
        // Run only on POSIX
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // Arrange: link -> real/src ; expect to find real/src/Main.java
        Path realPath = Files.createDirectories(tempDir.resolve("real/src"));
        Path testFile = writeFile(realPath.resolve("Main.java"), "class Main {}");
        Path linkPath = tempDir.resolve("link");
        try {
            Files.createSymbolicLink(linkPath, realPath);
        } catch (Exception e) {
            assumeTrue(false, "Symlink creation not permitted: " + e.getMessage());
        }

        String include = absGlob(linkPath, "**.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(include))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // Act: compare by real paths because Files.find returns paths via the symlink itself
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = s.map(p -> {
                        try {
                            return p.toRealPath();
                        } catch (IOException e) {
                            return p.toAbsolutePath().normalize();
                        }
                    })
                    .collect(Collectors.toSet());
        }

        // Assert
        assertThat(result).contains(testFile.toRealPath());
    }

    @Test
    void cyclicSymlink_doesNotLoopOrCrash_posixOnly() throws Exception {
        // Only run on POSIX
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // given
        Path loopDir = Files.createDirectories(tempDir.resolve("loop"));
        Path javaFile = writeFile(loopDir.resolve("Loop.java"), "class Loop {}");
        Path back = loopDir.resolve("back");
        try {
            // back -> loopDir (creates a cycle)
            Files.createSymbolicLink(back, loopDir);
        } catch (Exception e) {
            assumeTrue(false, "Symlink creation not permitted: " + e.getMessage());
        }

        String include = absGlob(loopDir, "**.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(include))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // when
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
                result.set(s.collect(Collectors.toUnmodifiableList()));
            }
        });

        // then: at minimum, the direct file should be present; duplicates are eliminated by the pipeline
        assertThat(result.get()).contains(javaFile.toAbsolutePath().normalize());
    }
}
