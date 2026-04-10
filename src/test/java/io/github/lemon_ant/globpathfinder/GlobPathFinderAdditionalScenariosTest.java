package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
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

    @Test
    void findPaths_singleBaseManyFiles_splitsAcrossThreads() throws IOException {
        // Given
        Path base = Files.createDirectories(tempDir.resolve("one-base"));
        for (int i = 0; i < 2_000; i++) {
            writeFile(base.resolve("dir-" + (i % 20)).resolve("File" + i + ".java"), "class C" + i + " {}");
        }

        PathQuery query = PathQuery.builder()
                .baseDir(base)
                .includeGlobs(Set.of("**/*.java"))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // When
        Set<String> workerThreads = new ConcurrentSkipListSet<>();
        long count;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            count = s.parallel()
                    .peek(path -> workerThreads.add(Thread.currentThread().getName()))
                    .count();
        }

        // Then
        assertThat(count).isEqualTo(2_000L);
        // Only assert multi-thread fan-out when the common pool actually has more than one worker.
        assumeTrue(
                ForkJoinPool.getCommonPoolParallelism() > 1,
                "Common pool parallelism is 1; parallel-thread assertion would be trivially false");
        assertThat(workerThreads.size())
                .as("Expected downstream processing to use multiple worker threads for one base directory.")
                .isGreaterThan(1);
    }

    @Test
    void findPaths_multiBaseManyFiles_usesMultipleThreads() throws IOException {
        // Given
        // Scenario: multiple absolute-include bases, one with many files.
        // The bridge should allow downstream parallel processing of all files.
        Path base1 = Files.createDirectories(tempDir.resolve("base1"));
        Path base2 = Files.createDirectories(tempDir.resolve("base2"));
        Path base3 = Files.createDirectories(tempDir.resolve("base3"));
        Path baseLarge = Files.createDirectories(tempDir.resolve("baseLarge"));

        writeFile(base1.resolve("A.java"), "class A {}");
        writeFile(base2.resolve("B.java"), "class B {}");
        writeFile(base3.resolve("C.java"), "class C {}");
        for (int i = 0; i < 2_000; i++) {
            writeFile(baseLarge.resolve("dir-" + (i % 20)).resolve("File" + i + ".java"), "class F" + i + " {}");
        }

        // Use absolute includes to create multiple base directories
        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(
                        absGlob(base1, "**/*.java"),
                        absGlob(base2, "**/*.java"),
                        absGlob(base3, "**/*.java"),
                        absGlob(baseLarge, "**/*.java")))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // When
        Set<String> workerThreads = new ConcurrentSkipListSet<>();
        long count;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            count = s.parallel()
                    .peek(path -> workerThreads.add(Thread.currentThread().getName()))
                    .count();
        }

        // Then
        assertThat(count).isGreaterThanOrEqualTo(2_000L);
        // Only assert multi-thread fan-out when the common pool has more than one worker.
        assumeTrue(
                ForkJoinPool.getCommonPoolParallelism() > 1,
                "Common pool parallelism is 1; parallel-thread assertion would be trivially false");
        assertThat(workerThreads.size())
                .as("Expected downstream processing to use multiple worker threads for multi-base directories.")
                .isGreaterThan(1);
    }

    @Test
    void findPaths_overlappingBases_deduplicatesResults() throws IOException {
        // Given
        // Scenario: parent dir and its child dir are both used as bases (via absolute includes).
        // Files under the child dir are discovered by BOTH scans; distinct() must remove duplicates.
        Path parent = Files.createDirectories(tempDir.resolve("parent"));
        Path child = Files.createDirectories(parent.resolve("child"));
        Path sibling = Files.createDirectories(parent.resolve("sibling"));

        // File in sibling (found only by parent scan, not by child scan)
        writeFile(sibling.resolve("Root.java"), "class Root {}");

        // Many files under child (found by BOTH parent and child scans → duplicates expected)
        int childFileCount = 500;
        for (int i = 0; i < childFileCount; i++) {
            writeFile(child.resolve("sub-" + (i % 10)).resolve("C" + i + ".java"), "class C" + i + " {}");
        }

        // Absolute includes covering both parent and child → two overlapping base directories
        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(absGlob(parent, "**/*.java"), absGlob(child, "**/*.java")))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // When
        List<Path> resultList;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            resultList = s.collect(Collectors.toList());
        }

        // Then
        // 1 file in sibling + 500 files in child = 501 unique files total (no duplicates)
        int expectedUniqueCount = 1 + childFileCount;
        assertThat(resultList)
                .as("distinct() should deduplicate files that appear under both parent and child base scans")
                .hasSize(expectedUniqueCount);

        // Sanity: converting to a Set should not shrink the list (i.e. no duplicates were present)
        Set<Path> resultSet =
                resultList.stream().map(p -> p.toAbsolutePath().normalize()).collect(Collectors.toSet());
        assertThat(resultSet).hasSize(expectedUniqueCount);
    }

    @Test
    void findPaths_absoluteExcludePattern_filtersTargetTree() throws IOException {
        // Given
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

        // When
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = toAbsoluteNormalizedSet(s);
        }

        // Then
        assertThat(result)
                .contains(src.toAbsolutePath().normalize())
                .doesNotContain(target.toAbsolutePath().normalize());
    }

    @Test
    void findPaths_cyclicSymlink_warnsAndCompletes() throws Exception {
        // Only run on POSIX
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // Attach ListAppender specifically to IoShieldingStream logger (not root)
        ListAppender<ILoggingEvent> appender = LogHelper.attachListAppender(IoTolerantPathStream.class);

        // Given
        Path loopDir = Files.createDirectories(tempDir.resolve("loop"));
        writeFile(loopDir.resolve("Loop.java"), "class Loop {}");
        Path backSymlink = loopDir.resolve("back");
        try {
            // backSymlink -> loopDir (creates a cycle)
            Files.createSymbolicLink(backSymlink, loopDir);
        } catch (Exception e) {
            // Symlinks might be forbidden in the environment; skip gracefully
            assumeTrue(false, "Symlink creation not permitted: " + e.getMessage());
        }

        String include = absGlob(loopDir, "**.java");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of(include))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .failFastOnError(false)
                .build();

        // When
        long count = GlobPathFinder.findPaths(query).count();

        // Then
        // We do NOT require any particular payload result; traversal may be cut short by the shield.
        // The only hard guarantee here is: no crash and a WARN is logged by IoShieldingStream.
        List<ILoggingEvent> warnEvents =
                appender.list.stream().filter(ev -> ev.getLevel() == Level.WARN).collect(Collectors.toList());

        assertThat(warnEvents)
                .as("Expected a WARN from IoShieldingStream about a filesystem loop")
                .anySatisfy(ev -> {
                    String message = ev.getFormattedMessage();
                    assertThat(message).contains("I/O during traversal of", "FileSystemLoopException");
                    assertThat(message).containsAnyOf("Stopping", "Skipping");
                    // Throwable presence and type hint (FileSystemLoopException)
                    assertThat(ev.getThrowableProxy()).isNotNull();
                    assertThat(ev.getThrowableProxy().getCause().getClassName()).contains("FileSystemLoopException");
                });
    }

    @Test
    void findPaths_followLinksEnabled_resolvesThroughSymlink() throws Exception {
        // Run only on POSIX
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // Given
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

        // When
        // Compare by real paths because Files.find returns paths via the symlink itself
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

        // Then
        assertThat(result).contains(testFile.toRealPath());
    }

    @Test
    void findPaths_includeAndExcludeSamePattern_returnsEmpty() throws IOException {
        // Given
        writeFile(tempDir.resolve("src/B.java"), "class B {}");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*.java"))
                .excludeGlobs(Set.of("**/*.java"))
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // When
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
                result.set(s.collect(Collectors.toUnmodifiableList()));
            }
        });

        // Then
        assertThat(result.get()).isEmpty();
    }

    @Test
    void findPaths_noMatchingIncludes_returnsEmpty() throws IOException {
        // Given
        writeFile(tempDir.resolve("src/C.java"), "class C {}");

        PathQuery query = PathQuery.builder()
                .baseDir(tempDir)
                .includeGlobs(Set.of("**/*.kt")) // no Kotlin files here
                .onlyFiles(true)
                .followLinks(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // When
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
                result.set(s.collect(Collectors.toUnmodifiableList()));
            }
        });

        // Then
        assertThat(result.get()).isEmpty();
    }

    @Test
    void findPaths_singleFileAbsoluteInclude_findsOneFile() throws IOException {
        // Given
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

        // When
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = toAbsoluteNormalizedSet(s);
        }

        // Then
        assertThat(result).containsExactly(single.toAbsolutePath().normalize());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void findPaths_windowsBackslashInclude_findsFile() throws IOException {
        // Given
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

        // When
        Set<Path> result;
        try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
            result = toAbsoluteNormalizedSet(s);
        }

        // Then
        assertThat(result).containsExactly(javaFile.toAbsolutePath().normalize());
    }

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
}
