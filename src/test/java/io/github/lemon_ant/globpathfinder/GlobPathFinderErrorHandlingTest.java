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

    // --- Test 1: base path does not exist ---
    @Test
    void findPaths_baseDoesNotExist_shouldWarnAndReturnEmpty() {
        // given
        Path nonExistingBase = tempDir.resolve("does-not-exist");
        ListAppender<ILoggingEvent> appender = LogHelper.attachListAppender(GlobPathFinder.class);

        PathQuery query = PathQuery.builder()
                .baseDir(nonExistingBase)
                .includeGlobs(Set.of("**/*.txt")) // anything; base doesn't exist anyway
                .onlyFiles(true)
                .maxDepth(Integer.MAX_VALUE)
                .followLinks(true)
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

        // and WARN should be present about failing to start scanning this base
        Condition<ILoggingEvent> warnForBase = new Condition<>(
                e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().toLowerCase(Locale.ROOT).contains("failed to start scanning base")
                        && e.getFormattedMessage().contains(nonExistingBase.toString()),
                "WARN mentioning failed to start scanning this base");
        assertThat(appender.list).anySatisfy(le -> assertThat(le).is(warnForBase));
    }

    // --- Test 2: unreadable subdirectory causes iteration-time failures (POSIX-only) ---
    @Test
    void findPaths_unreadableSubdirectory_shouldWarnAndStillReturnOtherFiles_posixOnly() throws IOException {
        // Run only on POSIX where chmod(000) is available
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // Arrange: base/ok/file1.txt + base/denied (000) + base/denied/file2.txt
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
        ListAppender<ILoggingEvent> appender = LogHelper.attachListAppender(IoShieldingStream.class);

        PathQuery query = PathQuery.builder()
                .baseDir(base)
                .includeGlobs(Set.of("**/*.txt"))
                .onlyFiles(true)
                .maxDepth(Integer.MAX_VALUE)
                .followLinks(true)
                .build();

        // Act: iterate the stream (shield should suppress late I/O errors)
        AtomicReference<List<Path>> result = new AtomicReference<>();
        assertThatNoException().isThrownBy(() -> {
            try (Stream<Path> s = GlobPathFinder.findPaths(query)) {
                result.set(s.collect(Collectors.toUnmodifiableList()));
            }
        });

        // Assert: WARN about I/O during traversal should be present (iteration-time)
        Condition<ILoggingEvent> warnDuringTraversal = new Condition<>(
                e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().toLowerCase(Locale.ROOT).contains("i/o")
                        && e.getFormattedMessage().toLowerCase(Locale.ROOT).contains("traversal"),
                "WARN mentioning I/O during traversal");
        assertThat(appender.list).anySatisfy(le -> assertThat(le).is(warnDuringTraversal));

        // And nothing from denied/ leaked into the result
        Path deniedAbs = deniedDir.toAbsolutePath().normalize();
        assertThat(result.get()).noneMatch(p -> p.toAbsolutePath().normalize().startsWith(deniedAbs));

        // Cleanup: restore permissions so TempDir can delete the tree
        Files.setPosixFilePermissions(
                deniedDir,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
    }
}
