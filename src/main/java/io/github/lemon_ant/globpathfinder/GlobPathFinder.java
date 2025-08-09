package io.github.lemon_ant.globpathfinder;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
public class GlobPathFinder {
    private static final PathMatcher MATCH_ALL = path -> true;

    @NonNull
    public static Stream<Path> findPaths(@NonNull PathQuery query) {
        Path normalizedBaseDir = normalizeBaseDir(query.getBaseDir());
        Map<Path, Set<PathMatcher>> baseToPatterns = computeBaseToPattern(normalizedBaseDir, query.getIncludeGlobs());
        Set<String> normalizedExtensions =
                processNormalizesStrings(query.getAllowedExtensions(), extension -> extension.toLowerCase(Locale.ROOT));
        Predicate<Path> extensionFilter = normalizedExtensions.isEmpty()
                ? path -> true
                : path -> normalizedExtensions.contains(
                        StringUtils.lowerCase(FilenameUtils.getExtension(path.toString())));
        Set<PathMatcher> excludeMatchers = processNormalizesStrings(
                query.getExcludeGlobs(), pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern));
        Predicate<Path> excludeFilter = excludeMatchers.isEmpty()
                ? path -> true
                : path -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(path));
        BiPredicate<Path, BasicFileAttributes> regularFileFilter =
                query.isOnlyFiles() ? (path, attrs) -> attrs.isRegularFile() : (path, attrs) -> true;

        Stream<Entry<Path, Set<PathMatcher>>> baseDirs = baseToPatterns.entrySet().parallelStream();

        return baseDirs.flatMap(globalEntry -> {
                    Path basePath = globalEntry.getKey();
                    Set<PathMatcher> matchers = globalEntry.getValue();

                    try {
                        // Open inner stream; do NOT try-with-resources (would close too early).
                        Stream<Path> found = Files.find(
                                basePath,
                                query.getMaxDepth(),
                                regularFileFilter,
                                query.visitOptions().toArray(new FileVisitOption[0]));

                        // TODO add Trace
                        return found.filter(extensionFilter)
                                .filter(rel -> isMatchedToPatterns(rel, matchers))
                                .filter(excludeFilter)
                                .onClose(found::close); // ensure resource cleanup when the OUTER stream is closed

                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                // Keep downstream parallel; help stateful ops
                .unordered()
                .distinct();
    }

    private static boolean isMatchedToPatterns(Path pathToMatch, Set<PathMatcher> matchers) {
        return isCollectionEmpty(matchers) || matchers.stream().anyMatch(matcher -> matcher.matches(pathToMatch));
    }

    @NonNull
    private static Path normalizeBaseDir(@Nullable Path baseDir) {
        Path normalizedBaseDir;
        Path cwd = Path.of(".");
        if (isNull(baseDir)) {
            normalizedBaseDir = cwd.toAbsolutePath();
        } else if (baseDir.isAbsolute()) {
            normalizedBaseDir = baseDir.normalize();
        } else {
            normalizedBaseDir = cwd.relativize(baseDir).toAbsolutePath();
        }
        return normalizedBaseDir;
    }

    @NonNull
    private static <TProcessResult> Set<TProcessResult> processNormalizesStrings(
            Set<String> strings, Function<String, TProcessResult> processor) {
        return isCollectionEmpty(strings)
                ? Set.of()
                : strings.stream()
                        .map(StringUtils::trimToNull)
                        .filter(Objects::nonNull)
                        .map(processor)
                        .collect(Collectors.toUnmodifiableSet());
    }

    @NonNull
    private static Map<Path, Set<PathMatcher>> computeBaseToPattern(Path baseDir, Set<String> includeGlobs) {
        return includeGlobs.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .map(glob -> extractBaseAndPattern(baseDir, glob))
                .collect(Collectors.groupingBy(
                        Pair::getKey,
                        Collectors.collectingAndThen(
                                Collectors.mapping(Pair::getValue, Collectors.toUnmodifiableSet()),
                                set -> set.contains(MATCH_ALL) ? null : set)));
    }

    private static boolean isCollectionEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    @NonNull
    private static Pair<Path, PathMatcher> extractBaseAndPattern(Path defaultAbsoluteBase, String glob) {
        String normalizedGlob = glob.replace('\\', '/');
        String[] pathSegments = StringUtils.split(normalizedGlob, '/');

        StringBuilder baseBuilder = new StringBuilder();

        if (!normalizedGlob.isEmpty() && normalizedGlob.charAt(0) == '/') {
            // Absolute path started from root slash
            baseBuilder.append(File.separatorChar);
        }

        // Find fixed start path
        int segmentIdx = 0;
        for (; segmentIdx < pathSegments.length; segmentIdx++) {
            if (isWildcardSegment(pathSegments[segmentIdx])) {
                break;
            }
            if (segmentIdx > 0) {
                baseBuilder.append(File.separatorChar);
            }
            baseBuilder.append(pathSegments[segmentIdx]);
        }

        String staticRoot = baseBuilder.toString();
        Path absoluteBasePath;
        if (staticRoot.isEmpty()) {
            absoluteBasePath = defaultAbsoluteBase;
        } else {
            Path staticRootPath = Path.of(staticRoot).normalize();
            if (staticRootPath.isAbsolute()) {
                absoluteBasePath = staticRootPath;
            } else {
                absoluteBasePath = defaultAbsoluteBase.resolve(staticRootPath).toAbsolutePath();
            }
        }
        String pattern =
                composePattern(segmentIdx, pathSegments, normalizedGlob.charAt(normalizedGlob.length() - 1) == '/');

        return Pair.of(
                absoluteBasePath,
                ofNullable(pattern)
                        .map(ptr -> FileSystems.getDefault().getPathMatcher("glob:" + ptr))
                        .orElse(MATCH_ALL));
    }

    @Nullable
    private static String composePattern(int startSegment, String[] segments, boolean addTrailSlash) {
        if (startSegment == segments.length) {
            // The pattern is empty
            return null;
        }
        StringBuilder patternBuilder = new StringBuilder();
        for (int j = startSegment; j < segments.length; j++) {
            if (!patternBuilder.isEmpty()) {
                patternBuilder.append('/');
            }
            patternBuilder.append(segments[j]);
        }
        if (addTrailSlash) {
            patternBuilder.append('/');
        }
        return patternBuilder.toString();
    }

    private static boolean isWildcardSegment(String segment) {
        return StringUtils.containsAny(segment, "*?[{]}");
    }
}
