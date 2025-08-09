package io.github.lemon_ant.globpathfinder;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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

    // TODO Create builder: deepness, onlyFiles, FOLLOW_LINKS
    @NonNull
    public static Stream<Path> findPaths(
            @Nullable Path baseDir,
            @NonNull Set<String> includeGlobs,
            @Nullable Set<String> allowedExtensions,
            @Nullable Set<String> excludeGlobs) {
        Path normalizedBaseDir =
                ofNullable(baseDir).orElse(Path.of(".")).normalize().toAbsolutePath();
        Map<Path, Set<PathMatcher>> baseToPatterns = computeBaseToPattern(normalizedBaseDir, includeGlobs);
        Set<String> normalizedExtensions = processNormalizesStrings(allowedExtensions, identity());
        Set<PathMatcher> excludeMatchers = processNormalizesStrings(
                excludeGlobs, pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern));

        Predicate<Path> extensionFilter = normalizedExtensions.isEmpty()
                ? (path -> true)
                : (path -> FilenameUtils.isExtension(path.toString(), allowedExtensions));
        Predicate<Path> excludeFilter = excludeMatchers.isEmpty()
                ? (path) -> true
                : path -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(path));

        Stream<Entry<Path, Set<PathMatcher>>> entryStream = baseToPatterns.entrySet().parallelStream();
        return entryStream.flatMap(globalEntry -> {
            try {
                Path basePath = globalEntry.getKey();
                return Files.find(
                                basePath,
                                Integer.MAX_VALUE,
                                (path, basicFileAttributes) -> !basicFileAttributes.isRegularFile(),
                                FileVisitOption.FOLLOW_LINKS)
                        .parallel()
                        .map(basePath::relativize)
                        .filter(extensionFilter)
                        .filter(currentPath -> {
                            Set<PathMatcher> matchers = globalEntry.getValue();
                            return isCollectionEmpty(matchers)
                                    || matchers.stream().anyMatch(matcher -> matcher.matches(currentPath));
                        })
                        .filter(excludeFilter)
                        .distinct() /*
                                    .onClose(entryStream::close)*/;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @NonNull
    private static <TProcessResult> Set<TProcessResult> processNormalizesStrings(
            Set<String> allowedExtensions, Function<String, TProcessResult> processor) {
        return isCollectionEmpty(allowedExtensions)
                ? Set.of()
                : allowedExtensions.stream()
                        .map(StringUtils::trimToNull)
                        .map(processor)
                        .filter(Objects::nonNull)
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
                                set -> set.contains(null) ? null : set)));
    }

    private static boolean isCollectionEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    @NonNull
    private static Pair<Path, PathMatcher> extractBaseAndPattern(Path defaultBase, String glob) {
        String normalized = glob.replace('\\', '/');
        String[] pathSegments = StringUtils.split(normalized, '/');

        StringBuilder baseBuilder = new StringBuilder();

        if (!normalized.isEmpty() && normalized.charAt(0) == '/') {
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

        String base = baseBuilder.toString();
        Path basePath = base.isEmpty() ? defaultBase : Path.of(base).normalize().toAbsolutePath();
        String pattern = composePattern(segmentIdx, pathSegments, normalized.charAt(normalized.length() - 1) == '/');

        return Pair.of(
                basePath,
                ofNullable(pattern)
                        .map(ptr -> FileSystems.getDefault().getPathMatcher("glob:" + ptr))
                        .orElse(null));
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
