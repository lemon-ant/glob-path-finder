package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.FileMatchingUtils.computeBaseToPattern;
import static io.github.lemon_ant.globpathfinder.StringUtils.processNormalizedStrings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * GlobPathFinder — service that traverses the file system and applies include/exclude rules.
 *
 * <p>Behavior overview (data comes from {@link PathQuery}):</p>
 * <ul>
 *   <li><b>Base directory</b>: {@code pathQuery.baseDir} is normalized to an absolute, normalized path before
 *       traversal.</li>
 *   <li><b>Include globs</b>: patterns are grouped by extracted base via {@code computeBaseToPattern}. For a base path,
 *       an empty matcher set means “match all under that base”.</li>
 *   <li><b>Extensions</b>: case-insensitive filter built from {@code allowedExtensions}. An empty set disables the
 *       filter.</li>
 *   <li><b>Excludes</b>: patterns compiled as {@code PathMatcher("glob:...")} and evaluated against absolute
 *       paths.</li>
 *   <li><b>Files/directories</b>: {@code onlyFiles=true} keeps regular files only; otherwise both files and directories
 *       may appear.</li>
 *   <li><b>Depth/options</b>: {@code maxDepth} and {@code getVisitOptions()} are passed to
 *       {@link java.nio.file.Files#find}.</li>
 *   <li><b>Parallelism</b>: different base directories are scanned in parallel; downstream is {@code unordered()}.</li>
 *   <li><b>Uniqueness</b>: resulting paths are deduplicated; the stream yields unique entries
 *       (uses {@code distinct()}).</li>
 *   <li><b>Stream safety</b>: the inner stream from {@code Files.find(...)} is closed via {@code onClose} when the
 *       outer stream is closed.</li>
 * </ul>
 *
 * <p>Notes for callers:</p>
 * <ul>
 *   <li>The returned stream is a resource; close it when finished (try-with-resources on the outer stream).</li>
 *   <li>IO errors are wrapped into {@link java.io.UncheckedIOException}.</li>
 * </ul>
 */
@UtilityClass
public class GlobPathFinder {

    /**
     * Find paths according to the provided {@link PathQuery}.
     *
     * @param pathQuery configuration (base, include/exclude, extensions, depth, onlyFiles)
     * @return a stream of absolute, normalized and <b>unique</b> paths that satisfy the filters;
     *         the caller is responsible for closing the returned stream
     * @throws UncheckedIOException on IO errors during traversal
     */
    @NonNull
    public static Stream<Path> findPaths(@NonNull PathQuery pathQuery) {
        // Normalize base to absolute before traversal.
        Path normalizedBaseDir = pathQuery.getBaseDir().toAbsolutePath().normalize();

        // Group include globs by extracted base; empty matcher set for a base denotes MATCH_ALL under that base.
        Map<Path, Set<PathMatcher>> baseToPatterns =
                computeBaseToPattern(normalizedBaseDir, pathQuery.getIncludeGlobs());

        // Build case-insensitive extension filter; empty set disables this filter.
        Set<String> normalizedExtensions = processNormalizedStrings(
                pathQuery.getAllowedExtensions(), extension -> extension.toLowerCase(Locale.ROOT));

        Predicate<Path> extensionFilter = normalizedExtensions.isEmpty()
                ? path -> true
                : path -> normalizedExtensions.contains(
                        StringUtils.lowerCase(FilenameUtils.getExtension(path.toString())));

        // Compile exclude globs to PathMatcher (glob:...) and evaluate against ABSOLUTE paths.
        Set<PathMatcher> excludeMatchers = processNormalizedStrings(
                pathQuery.getExcludeGlobs(), pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern));

        Predicate<Path> excludeFilter = excludeMatchers.isEmpty()
                ? path -> true
                : path -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(path));

        // Only regular files when onlyFiles=true; otherwise accept any file type.
        BiPredicate<Path, BasicFileAttributes> regularFileFilter =
                pathQuery.isOnlyFiles() ? (path, attrs) -> attrs.isRegularFile() : (path, attrs) -> true;

        // Scan each grouped base in parallel.
        Stream<Entry<Path, Set<PathMatcher>>> baseDirs = baseToPatterns.entrySet().parallelStream();

        return baseDirs.flatMap(globalEntry -> {
                    Path basePath = globalEntry.getKey();
                    Set<PathMatcher> pathMatchers = globalEntry.getValue();

                    try {
                        // Open inner stream; DO NOT use try-with-resources here (it would close too early).
                        Stream<Path> foundPaths = Files.find(
                                basePath,
                                pathQuery.getMaxDepth(),
                                regularFileFilter,
                                pathQuery.getVisitOptions().toArray(new FileVisitOption[0]));

                        // Apply filters in this order: extensions → include matchers → excludes.
                        return foundPaths
                                .filter(extensionFilter)
                                .filter(path -> FileMatchingUtils.isMatchedToPatterns(path, pathMatchers))
                                .filter(excludeFilter)
                                // Ensure resource cleanup when the OUTER stream is closed.
                                .onClose(foundPaths::close);

                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                // Downstream stays parallel-friendly; order does not matter for the result.
                .unordered()
                .distinct();
    }
}
