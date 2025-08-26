package io.github.lemon_ant.globpathfinder;

import static java.util.Optional.ofNullable;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
class FileMatchingUtils {
    private static final PathMatcher MATCH_ALL = FileSystems.getDefault().getPathMatcher("glob:**");

    static boolean isMatchedToPatterns(@NonNull Path pathToMatch, @NonNull Set<PathMatcher> pathMatchers) {
        return pathMatchers.isEmpty() || pathMatchers.stream().anyMatch(matcher -> matcher.matches(pathToMatch));
    }

    @NonNull
    static Map<Path, Set<PathMatcher>> computeBaseToPattern(@NonNull Path baseDir, @NonNull Set<String> includeGlobs) {
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

    @NonNull
    private static Pair<Path, PathMatcher> extractBaseAndPattern(Path defaultAbsoluteBase, String globPattern) {
        String normalizedGlob = globPattern.replace('\\', '/');
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
                ofNullable(pattern)
                        .map(ptr -> FileSystems.getDefault().getPathMatcher("glob:" + ptr))
                        .orElse(MATCH_ALL));
    }

    @Nullable
    private static String composePattern(int startSegment, String[] segments, boolean addTrailSlash) {
        if (startSegment == segments.length) {
            return null;
        }
        StringBuilder patternBuilder = new StringBuilder();
        for (int j = startSegment; j < segments.length; j++) {
            if (patternBuilder.length() > 0) {
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
