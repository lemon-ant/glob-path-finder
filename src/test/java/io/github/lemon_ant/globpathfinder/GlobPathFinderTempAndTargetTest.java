package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Simple, portable test that uses TWO parallel ABSOLUTE include globs:
 *  - one under the JUnit-provided temp directory,
 *  - one under the project's "target" directory.
 * Both are guaranteed (or conditionally assumed) to be writable in typical Maven builds.
 * We confirm both absolute includes work simultaneously.
 */
class GlobPathFinderTempAndTargetTest {

    @Test
    void findPaths_twoParallelAbsoluteIncludes_tempAndTarget_areHonored(@TempDir Path tempRoot) throws IOException {
        // ----- Arrange -----
        // Temp side: create an absolute base with a Java file and a tmp file to be excluded
        Path tempJavaBaseDirectory = Files.createDirectories(tempRoot.resolve("src/main/java"));
        Path tempJavaFile = Files.writeString(tempJavaBaseDirectory.resolve("A.java"), "class A {}");
        Path tempIgnoredTmpFile = Files.writeString(tempJavaBaseDirectory.resolve("ignored.tmp"), "tmp");

        // Target side: prepare a unique sandbox under the project's "target" directory
        Path projectDirectory =
                Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        assumeTrue(
                Files.isWritable(projectDirectory), "Project directory is not writable, cannot create target sandbox.");

        Path targetDirectory = Files.createDirectories(projectDirectory.resolve("target"));
        Path targetSandboxDirectory =
                Files.createDirectories(targetDirectory.resolve("gpf-temp-target-" + UUID.randomUUID()));
        Path targetDocsDirectory = Files.createDirectories(targetSandboxDirectory.resolve("docs"));
        Path targetMarkdownFile = Files.writeString(targetDocsDirectory.resolve("readme.md"), "# readme");

        // Build absolute include globs (normalize separators to forward slashes for globbing)
        String absoluteTempInclude =
                tempJavaBaseDirectory.toAbsolutePath().toString().replace('\\', '/') + "/**/*.java";
        String absoluteTargetInclude =
                targetSandboxDirectory.toAbsolutePath().toString().replace('\\', '/') + "/**/*.md";

        Set<String> includeGlobs = Set.of(
                absoluteTempInclude, // absolute include #1 (temp)
                absoluteTargetInclude // absolute include #2 (target)
                );
        Set<String> excludeGlobs = Set.of("**/*.tmp"); // exclude tmp files globally

        PathQuery query = PathQuery.builder()
                .baseDir(tempRoot) // baseDir is irrelevant for absolute includes, but required by API
                .includeGlobs(includeGlobs)
                .excludeGlobs(excludeGlobs)
                .allowedExtensions(Set.of("java", "md")) // ensure extensions filtering cooperates with includes
                .onlyFiles(true)
                .maxDepth(Integer.MAX_VALUE)
                .build();

        // ----- Act -----
        List<Path> actualPaths;
        try (Stream<Path> foundPaths = GlobPathFinder.findPaths(query)) {
            actualPaths = foundPaths.toList();
        } finally {
            // Best-effort cleanup: remove only what we created under target
            try (Stream<Path> walk = Files.walk(targetSandboxDirectory)) {
                walk.sorted((a, b) -> Integer.compare(b.getNameCount(), a.getNameCount()))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            } catch (IOException ignored) {
                /* ignore */
            }
        }

        // ----- Assert -----
        assertThat(actualPaths)
                .containsExactlyInAnyOrder(tempJavaFile.toAbsolutePath(), targetMarkdownFile.toAbsolutePath())
                .doesNotContain(tempIgnoredTmpFile.toAbsolutePath());
    }
}
