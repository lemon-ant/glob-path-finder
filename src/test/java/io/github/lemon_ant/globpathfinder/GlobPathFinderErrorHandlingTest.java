/*
 * SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration-style tests that exercise GlobPathFinder against real filesystem conditions:
 * - non-existing base directory (open-time failure)
 * - unreadable subdirectory (iteration-time UncheckedIOException)
 * <p>
 * We attach an in-memory log appender to verify WARN logs are emitted by the public API.
 */
class GlobPathFinderErrorHandlingTest {

    @TempDir
    Path tempDir;

    @Test
    void findPaths_nonExistentBase_failFastDisabled_throwsIllegalArgumentException() {
        // Given
        Path nonExistingBase = tempDir.resolve("does-not-exist");
        PathQuery query = PathQuery.builder()
                .baseDir(nonExistingBase)
                .includeGlobs(Set.of("**/*.txt"))
                .onlyFiles(true)
                .maxDepth(Integer.MAX_VALUE)
                .followLinks(true)
                .failFastOnError(false)
                .build();

        // When
        Throwable thrown = catchThrowable(() -> {
            try (Stream<Path> pathStream = GlobPathFinder.findPaths(query)) {
                pathStream.collect(Collectors.toList());
            }
        });

        // Then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base directory does not exist: ")
                .hasMessageContaining(
                        nonExistingBase.toAbsolutePath().normalize().toString());
    }

    @Test
    void findPaths_unreadableSubdirectory_warnsAndSkipsLockedPaths() throws IOException {
        // Run only on POSIX where chmod(000) is available
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // Given
        Path base = tempDir.resolve("base");
        Path okDir = base.resolve("ok");
        Path deniedDir = base.resolve("denied");
        Files.createDirectories(okDir);
        Files.createDirectories(deniedDir);

        Path visibleFile = okDir.resolve("file1.txt");
        Files.writeString(visibleFile, "hello");

        Path hiddenFile = deniedDir.resolve("file2.txt");
        Files.writeString(hiddenFile, "secret");

        // Make denied/ unreadable (000)
        Files.setPosixFilePermissions(deniedDir, Set.of());

        // Attach in-memory appender to capture WARN
        ListAppender<ILoggingEvent> appender = LogHelper.attachListAppender(IoTolerantPathStream.class);

        PathQuery query = PathQuery.builder()
                .baseDir(base)
                .includeGlobs(Set.of("**/*.txt"))
                .onlyFiles(true)
                .maxDepth(Integer.MAX_VALUE)
                .followLinks(true)
                .failFastOnError(false)
                .build();

        // When
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> pathStream = GlobPathFinder.findPaths(query)) {
                result.set(pathStream.collect(Collectors.toUnmodifiableList()));
            }
        });

        // Then
        Condition<ILoggingEvent> warnDuringTraversal = new Condition<>(
                event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().toLowerCase(Locale.ROOT).contains("i/o")
                        && event.getFormattedMessage().toLowerCase(Locale.ROOT).contains("traversal"),
                "WARN mentioning I/O during traversal");
        assertThat(appender.list).anySatisfy(logEvent -> assertThat(logEvent).is(warnDuringTraversal));

        // And nothing from denied/ leaked into the result
        Path deniedAbs = deniedDir.toAbsolutePath().normalize();
        assertThat(result.get())
                .noneMatch(path -> path.toAbsolutePath().normalize().startsWith(deniedAbs));

        // Cleanup: restore permissions so TempDir can delete the tree
        Files.setPosixFilePermissions(
                deniedDir,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
    }

    @Test
    void findPaths_nonExistentBase_failFastEnabled_throwsIllegalArgumentException() {
        // Given
        Path nonExistingBase = tempDir.resolve("does-not-exist");
        PathQuery query = PathQuery.builder()
                .baseDir(nonExistingBase)
                .includeGlobs(Set.of("**/*.txt"))
                .failFastOnError(true)
                .build();

        // When
        Throwable thrown = catchThrowable(() -> {
            try (Stream<Path> pathStream = GlobPathFinder.findPaths(query)) {
                pathStream.collect(Collectors.toList());
            }
        });

        // Then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base directory does not exist: ")
                .hasMessageContaining(
                        nonExistingBase.toAbsolutePath().normalize().toString());
    }

    @Test
    void findPaths_baseDirIsFile_throwsIllegalArgumentException() throws IOException {
        // Given
        Path file = tempDir.resolve("somefile.txt");
        Files.writeString(file, "content");
        PathQuery query = PathQuery.builder().baseDir(file).build();

        // When
        Throwable thrown = catchThrowable(() -> {
            try (Stream<Path> pathStream = GlobPathFinder.findPaths(query)) {
                pathStream.collect(Collectors.toList());
            }
        });

        // Then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base path is not a directory: ")
                .hasMessageContaining(file.toAbsolutePath().normalize().toString());
    }

    @Test
    void findPaths_failSafeEnabled_existingBase_traversesShieldedStream() throws IOException {
        // Given
        Path base = tempDir.resolve("safe");
        Files.createDirectories(base.resolve("sub"));
        Files.writeString(base.resolve("sub/Hello.java"), "class Hello {}");
        PathQuery query = PathQuery.builder()
                .baseDir(base)
                .includeGlobs(Set.of("**/*.java"))
                .failFastOnError(false)
                .build();

        // When
        List<Path> result;
        try (Stream<Path> pathStream = GlobPathFinder.findPaths(query)) {
            result = pathStream.collect(Collectors.toUnmodifiableList());
        }

        // Then
        assertThat(result).hasSize(1);
    }
}
