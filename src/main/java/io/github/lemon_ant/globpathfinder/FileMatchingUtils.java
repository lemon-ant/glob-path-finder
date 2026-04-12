package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.StringUtils.normalizeToUnixSeparators;
import static java.util.Optional.ofNullable;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
class FileMatchingUtils {
    private static final PathMatcher MATCH_ALL = AntStylePathMatcher.compile("**");
    private static final Pattern WINDOWS_DRIVE_PATTERN = Pattern.compile("^[a-zA-Z]:[/\\\\].*");

    /**
     * Groups the given include glob patterns by their extracted static base directory, compiling
     * each pattern tail into a {@link PathMatcher}. An empty matcher set for a base signals
     * "match all" under that base (MATCH_ALL sentinel). If the resulting map is empty (all
     * patterns were blank), returns a single entry mapping {@code baseDir} to an empty set.
     *
     * @param baseDir      the normalized absolute base directory to resolve relative globs against
     * @param includeGlobs the raw include glob patterns; blank entries are ignored
     * @return a map from extracted base {@link Path} to the set of compiled {@link PathMatcher}s
     */
    @NonNull
    static Map<Path, Set<PathMatcher>> computeBaseToIncludeMatchers(
            @NonNull Path baseDir, @NonNull Set<String> includeGlobs) {
        Map<Path, Set<PathMatcher>> result = includeGlobs.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .map(glob -> extractBaseAndPattern(baseDir, glob))
                .collect(Collectors.groupingBy(
                        Pair::getKey,
                        Collectors.collectingAndThen(
                                Collectors.mapping(Pair::getValue, Collectors.toUnmodifiableSet()),
                                set -> set.contains(MATCH_ALL) ? Set.of() : set)));
        if (!result.isEmpty()) {
            return result;
        }
        return Map.of(baseDir, Set.of());
    }

    /**
     * Returns {@code true} if the given path matches at least one of the provided matchers,
     * or if the matcher set is empty (empty set means "match all").
     *
     * @param pathToMatch  the path to test
     * @param pathMatchers the set of matchers to check against; an empty set always returns {@code true}
     * @return {@code true} if the path matches any matcher or the set is empty
     */
    static boolean isMatchedToPatterns(@NonNull Path pathToMatch, @NonNull Set<PathMatcher> pathMatchers) {
        return pathMatchers.isEmpty() || pathMatchers.stream().anyMatch(matcher -> matcher.matches(pathToMatch));
    }

    /**
     * Partitions the given glob patterns into two lists: absolute patterns (starting with {@code /}
     * or a Windows drive letter such as {@code C:\}) and relative patterns (everything else).
     *
     * @param patterns the raw glob patterns to partition
     * @return a {@link Pair} where the left list contains absolute patterns and the right list
     *         contains relative patterns
     */
    @NonNull
    static Pair<List<String>, List<String>> partitionAbsoluteAndRelative(@NonNull Collection<String> patterns) {
        List<String> absolute = new ArrayList<>();
        List<String> relative = new ArrayList<>();

        for (String pattern : patterns) {
            if (isAbsoluteGlob(pattern)) {
                absolute.add(pattern);
            } else {
                relative.add(pattern);
            }
        }
        return Pair.of(absolute, relative);
    }

    @Nullable
    private static String composePattern(int startSegment, String[] segments, boolean addTrailSlash) {
        if (startSegment == segments.length) {
            return null;
        }
        StringBuilder patternBuilder = new StringBuilder();
        for (int segmentIndex = startSegment; segmentIndex < segments.length; segmentIndex++) {
            if (patternBuilder.length() > 0) {
                patternBuilder.append('/');
            }
            patternBuilder.append(segments[segmentIndex]);
        }
        if (addTrailSlash) {
            patternBuilder.append('/');
        }
        return patternBuilder.toString();
    }

    @NonNull
    private static Pair<Path, PathMatcher> extractBaseAndPattern(Path defaultAbsoluteBase, String globPattern) {
        String normalizedGlob = normalizeToUnixSeparators(globPattern);
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
        Path extractedBasePath;
        if (staticRoot.isEmpty()) {
            extractedBasePath = defaultAbsoluteBase;
        } else {
            Path staticRootPath = Path.of(staticRoot).normalize();
            if (staticRootPath.isAbsolute()) {
                extractedBasePath = staticRootPath;
            } else {
                extractedBasePath = defaultAbsoluteBase.resolve(staticRootPath).toAbsolutePath();
            }
        }

        String pattern =
                composePattern(segmentIdx, pathSegments, normalizedGlob.charAt(normalizedGlob.length() - 1) == '/');

        return Pair.of(
                extractedBasePath,
                ofNullable(pattern).map(AntStylePathMatcher::compile).orElse(MATCH_ALL));
    }

    private static boolean isAbsoluteGlob(String globPattern) {
        // Normalize separators
        String normalized = normalizeToUnixSeparators(globPattern);

        // Unix-like absolute OR Windows drive letter absolute (e.g., C:/, D:\)
        return normalized.startsWith("/")
                || WINDOWS_DRIVE_PATTERN.matcher(globPattern).matches();
    }

    private static boolean isWildcardSegment(String segment) {
        return StringUtils.containsAny(segment, "*?[{]}");
    }
}
