package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.FileMatchingUtils.computeBaseToPattern;
import static io.github.lemon_ant.globpathfinder.FileMatchingUtils.partitionAbsoluteAndRelative;
import static io.github.lemon_ant.globpathfinder.StringUtils.processNormalizedStrings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * GlobPathFinder — service that traverses the file system and applies include/exclude rules.
 *
 * <p>Behavior overview (data comes from {@link PathQuery}):</p>
 * <ul>
 *   <li><b>Base directory</b>: {@code pathQuery.baseDir} is normalized to an absolute, normalized path before traversal.</li>
 *   <li><b>Include globs</b>: patterns are grouped by extracted base via {@code computeBaseToPattern}. For a base path,
 *       an empty matcher set means “match all under that base”.</li>
 *   <li><b>Extensions</b>: case-insensitive filter built from {@code allowedExtensions}. An empty set disables the filter.</li>
 *   <li><b>Excludes</b>: relative/absolute patterns compiled as {@code PathMatcher("glob:...")}.</li>
 *   <li><b>Files/directories</b>: {@code onlyFiles=true} keeps regular files only; otherwise both files and directories may appear.</li>
 *   <li><b>Depth/options</b>: {@code maxDepth} and {@code getVisitOptions()} are passed to {@link java.nio.file.Files#find}.</li>
 *   <li><b>Parallelism</b>: different base directories are scanned in parallel; downstream is {@code unordered()}.</li>
 *   <li><b>Uniqueness</b>: resulting paths are deduplicated; the stream yields unique entries ({@code distinct()}).</li>
 *   <li><b>Stream safety</b>: the inner stream from {@code Files.find(...)} is closed via {@code onClose} when the outer stream is closed.</li>
 * </ul>
 */
@Slf4j
@UtilityClass
@SuppressWarnings("PMD.CognitiveComplexity")
// TODO Simplify method
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

        // ===== Build processing pipeline BEFORE any stream is created =====

        // Extensions (case-insensitive); if empty, there will be NO extension step in the pipeline.
        Set<String> normalizedExtensions = processNormalizedStrings(
                pathQuery.getAllowedExtensions(), extension -> extension.toLowerCase(Locale.ROOT));
        boolean hasAllowedExtensions = !normalizedExtensions.isEmpty();

        // Excludes: split into absolute vs relative; if a set is empty, there will be NO respective step.
        Pair<List<String>, List<String>> absoluteAndRelativeExcludes =
                partitionAbsoluteAndRelative(pathQuery.getExcludeGlobs());

        // Compile exclude globs to PathMatcher (glob:...) and evaluate against ABSOLUTE paths.
        Set<PathMatcher> absoluteExcludeMatchers =
                processNormalizedStrings(absoluteAndRelativeExcludes.getLeft(), pattern -> FileSystems.getDefault()
                        .getPathMatcher("glob:" + pattern));

        // Compile exclude globs to PathMatcher (glob:...) and evaluate against RELATIVE paths.
        Set<PathMatcher> relativeExcludeMatchers =
                processNormalizedStrings(absoluteAndRelativeExcludes.getRight(), pattern -> FileSystems.getDefault()
                        .getPathMatcher("glob:" + pattern));

        boolean hasAbsoluteExcludes = !absoluteExcludeMatchers.isEmpty();
        boolean hasRelativeExcludes = !relativeExcludeMatchers.isEmpty();

        // File attribute filter is part of Files.find(...) — not a stream operator.
        BiPredicate<Path, BasicFileAttributes> regularFileFilter =
                pathQuery.isOnlyFiles() ? (path, attrs) -> attrs.isRegularFile() : (path, attrs) -> true;

        boolean isDebugEnabled = log.isDebugEnabled();

        // ---- Global pipeline (base-agnostic) built ONCE, without pass-through predicates ----
        // We compose steps only when they are needed. No "path -> true" fallbacks.
        Function<Stream<Path>, Stream<Path>> globalPipeline = Function.identity();

        // Debug peek on the raw discoveries
        if (isDebugEnabled) {
            globalPipeline = globalPipeline.andThen(pathStream -> pathStream.peek(path -> log.debug("Found {}", path)));
        }

        // Extensions filter
        if (hasAllowedExtensions) {
            globalPipeline = globalPipeline.andThen(pathStream -> pathStream.filter(path -> {
                // Compute extension lazily only when this step exists
                String extension = StringUtils.lowerCase(FilenameUtils.getExtension(path.toString()));
                return normalizedExtensions.contains(extension);
            }));
            if (isDebugEnabled) {
                globalPipeline = globalPipeline.andThen(
                        pathStream -> pathStream.peek(path -> log.debug("Passed extension filter {}", path)));
            }
        }

        // Absolute excludes
        if (hasAbsoluteExcludes) {
            globalPipeline = globalPipeline.andThen(pathStream ->
                    pathStream.filter(path -> absoluteExcludeMatchers.stream().noneMatch(m -> m.matches(path))));
            if (isDebugEnabled) {
                globalPipeline = globalPipeline.andThen(
                        pathStream -> pathStream.peek(path -> log.debug("Passed exclude absolute filter {}", path)));
            }
        }

        // ---- Per-base factory: adds relative phase only when really needed for that base ----
        // If a base has include matchers OR we have relative excludes at all — we need a relative phase.
        Function<Entry<Path, Set<PathMatcher>>, Function<Stream<Path>, Stream<Path>>> perBasePipelineFactory =
                entry -> {
                    Path basePath = entry.getKey();
                    Set<PathMatcher> includeMatchersForBase = entry.getValue();
                    boolean hasIncludesForBase = !includeMatchersForBase.isEmpty();
                    boolean needsRelativePhase = hasIncludesForBase || hasRelativeExcludes;

                    if (!needsRelativePhase) {
                        // Nothing base-specific to add
                        return Function.identity();
                    }

                    // 1) relativize
                    Function<Stream<Path>, Stream<Path>> perBaseDirPipeline =
                            pathStream -> pathStream.map(basePath::relativize);

                    // 2) include (only if present for this base)
                    if (hasIncludesForBase) {
                        perBaseDirPipeline = perBaseDirPipeline.andThen(pathStream -> pathStream.filter(
                                relPath -> FileMatchingUtils.isMatchedToPatterns(relPath, includeMatchersForBase)));
                        if (isDebugEnabled) {
                            perBaseDirPipeline = perBaseDirPipeline.andThen(
                                    pathStream -> pathStream.peek(path -> log.debug("Passed include filter {}", path)));
                        }
                    }

                    // 3) relative excludes (only if configured at all)
                    if (hasRelativeExcludes) {
                        perBaseDirPipeline = perBaseDirPipeline.andThen(pathStream -> pathStream.filter(
                                relPath -> relativeExcludeMatchers.stream().noneMatch(m -> m.matches(relPath))));
                        if (isDebugEnabled) {
                            perBaseDirPipeline = perBaseDirPipeline.andThen(pathStream ->
                                    pathStream.peek(path -> log.debug("Passed exclude relative filter {}", path)));
                        }
                    }

                    // 4) return to absolute, normalized
                    perBaseDirPipeline = perBaseDirPipeline.andThen(pathStream -> pathStream.map(relPath ->
                            basePath.resolve(relPath).toAbsolutePath().normalize()));

                    return perBaseDirPipeline;
                };

        // ===== Streams start here =====

        Stream<Entry<Path, Set<PathMatcher>>> baseDirs = baseToPatterns.entrySet().parallelStream();

        Function<Stream<Path>, Stream<Path>> finalGlobalPipeline = globalPipeline;
        return baseDirs.flatMap(entry -> {
                    Path basePath = entry.getKey();
                    try {
                        Stream<Path> foundPaths = Files.find(
                                basePath,
                                pathQuery.getMaxDepth(),
                                regularFileFilter,
                                pathQuery.getVisitOptions().toArray(new FileVisitOption[0]));

                        Stream<Path> safeFoundPaths = IoShieldingStream.wrapPathStream(foundPaths, basePath);

                        // Build per-base fragment (cheap, no IO), then apply:
                        Function<Stream<Path>, Stream<Path>> perBasePipeline = perBasePipelineFactory.apply(entry);
                        return perBasePipeline
                                .apply(finalGlobalPipeline.apply(safeFoundPaths))
                                .onClose(safeFoundPaths::close);
                    } catch (IOException e) {
                        // TODO Move to IoShieldingStream
                        log.warn("Failed to start scanning base '{}'. Skipping this base.", basePath, e);
                        return Stream.empty();
                    }
                })
                // Keep downstream parallel-friendly; order is irrelevant.
                .unordered()
                .distinct();
    }
}
