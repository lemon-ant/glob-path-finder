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

@UtilityClass
public class GlobPathFinder {

    @NonNull
    public static Stream<Path> findPaths(@NonNull PathQuery pathQuery) {
        Path normalizedBaseDir = pathQuery.getBaseDir().toAbsolutePath().normalize();
        Map<Path, Set<PathMatcher>> baseToPatterns =
                computeBaseToPattern(normalizedBaseDir, pathQuery.getIncludeGlobs());
        Set<String> normalizedExtensions = processNormalizedStrings(
                pathQuery.getAllowedExtensions(), extension -> extension.toLowerCase(Locale.ROOT));
        Predicate<Path> extensionFilter = normalizedExtensions.isEmpty()
                ? path -> true
                : path -> normalizedExtensions.contains(
                        StringUtils.lowerCase(FilenameUtils.getExtension(path.toString())));
        Set<PathMatcher> excludeMatchers = processNormalizedStrings(
                pathQuery.getExcludeGlobs(), pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern));
        Predicate<Path> excludeFilter = excludeMatchers.isEmpty()
                ? path -> true
                : path -> excludeMatchers.stream().noneMatch(matcher -> matcher.matches(path));
        BiPredicate<Path, BasicFileAttributes> regularFileFilter =
                pathQuery.isOnlyFiles() ? (path, attrs) -> attrs.isRegularFile() : (path, attrs) -> true;

        Stream<Entry<Path, Set<PathMatcher>>> baseDirs = baseToPatterns.entrySet().parallelStream();

        return baseDirs.flatMap(globalEntry -> {
                    Path basePath = globalEntry.getKey();
                    Set<PathMatcher> pathMatchers = globalEntry.getValue();

                    try {
                        // Open inner stream; do NOT try-with-resources (would close too early).
                        Stream<Path> foundPaths = Files.find(
                                basePath,
                                pathQuery.getMaxDepth(),
                                regularFileFilter,
                                pathQuery.getVisitOptions().toArray(new FileVisitOption[0]));

                        return foundPaths
                                .filter(extensionFilter)
                                .filter(path -> FileMatchingUtils.isMatchedToPatterns(path, pathMatchers))
                                .filter(excludeFilter)
                                .onClose(foundPaths::close); // ensure resource cleanup when the OUTER stream is closed

                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                // Keep downstream parallel; help stateful ops
                .unordered()
                .distinct();
    }
}
