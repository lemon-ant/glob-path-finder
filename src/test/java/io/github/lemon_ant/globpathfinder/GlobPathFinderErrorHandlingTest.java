package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
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
import org.slf4j.LoggerFactory;

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

    // Helper: attach in-memory appender to the class logger used by GlobPathFinder
    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobPathFinder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    // --- Test 1: base path does not exist ---

    @Test
    void findPaths_baseDoesNotExist_shouldWarnAndReturnEmpty() {
        // given
        Path nonExistingBase = tempDir.resolve("does-not-exist");
        ListAppender<ILoggingEvent> appender = attachListAppender();

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
        // Only run on POSIX where we can chmod 000
        assumeTrue(
                Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX attributes not supported; skipping test.");

        // given: create structure:
        // base/
        //   ok/file1.txt
        //   denied/ (chmod 000) with file2.txt inside (inaccessible)
        Path base = tempDir.resolve("base");
        Path okDir = base.resolve("ok");
        Path deniedDir = base.resolve("denied");

        Files.createDirectories(okDir);
        Files.createDirectories(deniedDir);

        Path visibleFile = okDir.resolve("file1.txt");
        Files.writeString(visibleFile, "hello");

        Path hiddenFile = deniedDir.resolve("file2.txt");
        Files.writeString(hiddenFile, "secret");

        // Make deniedDir unreadable (000)
        Set<PosixFilePermission> noPerms = Set.of();
        Files.setPosixFilePermissions(deniedDir, noPerms);

        // Attach log appender to capture WARN from iteration time
        ListAppender<ILoggingEvent> appender = attachListAppender();

        PathQuery query = PathQuery.builder()
                .baseDir(base)
                .includeGlobs(Set.of("**/*.txt"))
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

        // then: we still get the visible file, and we did not throw
        assertThat(result.get())
                .containsExactlyInAnyOrder(visibleFile.toAbsolutePath().normalize());

        // and: a WARN about I/O during traversal should be present (iteration-time)
        Condition<ILoggingEvent> warnDuringTraversal = new Condition<>(
                e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().toLowerCase(Locale.ROOT).contains("i/o")
                        && e.getFormattedMessage().toLowerCase(Locale.ROOT).contains("traversal"),
                "WARN mentioning I/O during traversal");
        assertThat(appender.list).anySatisfy(le -> assertThat(le).is(warnDuringTraversal));

        // Cleanup: restore perms so TempDir can delete the tree
        Files.setPosixFilePermissions(
                deniedDir,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
    }
}
