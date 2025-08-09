package io.github.lemon_ant.globpathfinder;

import static java.util.Optional.ofNullable;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

/**
 * Immutable query describing how to search for paths.
 */
@Getter
@Builder(toBuilder = true)
@Value
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class PathQuery {

    /**
     * Base directory to start from. Defaults to current working directory.
     */
    @Nullable
    Path baseDir;

    /**
     * Include glob patterns (required, at least one).
     */
    @NonNull
    Set<String> includeGlobs;

    /**
     * Optional set of allowed file extensions (case-insensitive, without dots).
     */
    @Nullable
    @SuppressFBWarnings("EI_EXPOSE_REP")
    Set<String> allowedExtensions;

    /**
     * Optional set of exclude glob patterns.
     */
    @Nullable
    @SuppressFBWarnings("EI_EXPOSE_REP")
    Set<String> excludeGlobs;

    /**
     * Maximum depth to traverse. Defaults to unlimited.
     */
    @SuppressWarnings("PMD.UnusedAssignment")
    @Builder.Default
    int maxDepth = Integer.MAX_VALUE;

    /**
     * Whether to match only regular files (true) or everything (false).
     */
    @SuppressWarnings("PMD.UnusedAssignment")
    @Builder.Default
    boolean onlyFiles = true;

    /**
     * Whether to follow symbolic links.
     */
    @SuppressWarnings("PMD.UnusedAssignment")
    @Builder.Default
    boolean followLinks = true;

    PathQuery(
            @Nullable Path baseDir,
            @NonNull Set<String> includeGlobs,
            @Nullable Set<String> allowedExtensions,
            @Nullable Set<String> excludeGlobs,
            int maxDepth,
            boolean onlyFiles,
            boolean followLinks) {
        this.baseDir = baseDir;
        this.includeGlobs = Set.copyOf(includeGlobs);
        this.allowedExtensions = ofNullable(allowedExtensions).map(Set::copyOf).orElse(null);
        this.excludeGlobs = ofNullable(excludeGlobs).map(Set::copyOf).orElse(null);
        this.maxDepth = maxDepth;
        this.onlyFiles = onlyFiles;
        this.followLinks = followLinks;
    }

    /**
     * Convert config to FileVisitOption set.
     */
    public Set<FileVisitOption> visitOptions() {
        return followLinks ? EnumSet.of(FileVisitOption.FOLLOW_LINKS) : Collections.emptySet();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PathQuery pathQuery)) return false;
        return maxDepth == pathQuery.maxDepth
                && onlyFiles == pathQuery.onlyFiles
                && followLinks == pathQuery.followLinks
                && Objects.equals(baseDir, pathQuery.baseDir)
                && Objects.equals(includeGlobs, pathQuery.includeGlobs)
                && Objects.equals(allowedExtensions, pathQuery.allowedExtensions)
                && Objects.equals(excludeGlobs, pathQuery.excludeGlobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseDir, includeGlobs, allowedExtensions, excludeGlobs, maxDepth, onlyFiles, followLinks);
    }
}
