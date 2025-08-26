package io.github.lemon_ant.globpathfinder;

import static java.util.Optional.ofNullable;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * Immutable query describing how to search for paths.
 */
@Value
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class PathQuery {

    /**
     * Base directory to start from. Defaults to current working directory.
     */
    @NonNull
    Path baseDir;

    /**
     * Include glob patterns (required, at least one).
     */
    @NonNull
    @SuppressFBWarnings("EI_EXPOSE_REP")
    Set<String> includeGlobs;

    /**
     * Optional set of allowed file extensions (case-insensitive, without dots).
     */
    @NonNull
    @SuppressFBWarnings("EI_EXPOSE_REP")
    Set<String> allowedExtensions;

    /**
     * Optional set of exclude glob patterns.
     */
    @NonNull
    @SuppressFBWarnings("EI_EXPOSE_REP")
    Set<String> excludeGlobs;

    /**
     * Maximum depth to traverse. Defaults to unlimited.
     */
    int maxDepth;

    /**
     * Whether to match only regular files (true) or everything (false).
     */
    boolean onlyFiles;

    /**
     * Whether to follow symbolic links.
     */
    boolean followLinks;

    @Builder(toBuilder = true)
    private PathQuery(
            @Nullable Path baseDir,
            @Nullable Set<String> includeGlobs,
            @Nullable Set<String> allowedExtensions,
            @Nullable Set<String> excludeGlobs,
            @Nullable Integer maxDepth,
            @Nullable Boolean onlyFiles,
            @Nullable Boolean followLinks) {
        this.baseDir = ofNullable(baseDir).orElse(Path.of("."));
        this.includeGlobs = ofNullable(includeGlobs).map(Set::copyOf).orElse(Set.of());
        this.allowedExtensions = ofNullable(allowedExtensions).map(Set::copyOf).orElse(Set.of());
        this.excludeGlobs = ofNullable(excludeGlobs).map(Set::copyOf).orElse(Set.of());
        this.maxDepth = ofNullable(maxDepth).filter(depth -> depth >= 0).orElse(Integer.MAX_VALUE);
        this.onlyFiles = ofNullable(onlyFiles).orElse(true);
        this.followLinks = ofNullable(followLinks).orElse(true);
    }

    /**
     * Convert config to FileVisitOption set.
     */
    public Set<FileVisitOption> getVisitOptions() {
        return followLinks ? EnumSet.of(FileVisitOption.FOLLOW_LINKS) : Set.of();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PathQuery)) {
            return false;
        }

        PathQuery pathQuery = (PathQuery) o;
        return maxDepth == pathQuery.maxDepth
                && onlyFiles == pathQuery.onlyFiles
                && followLinks == pathQuery.followLinks
                && baseDir.equals(pathQuery.baseDir)
                && includeGlobs.equals(pathQuery.includeGlobs)
                && allowedExtensions.equals(pathQuery.allowedExtensions)
                && excludeGlobs.equals(pathQuery.excludeGlobs);
    }

    @Override
    public int hashCode() {
        int result = baseDir.hashCode();
        result = 31 * result + includeGlobs.hashCode();
        result = 31 * result + allowedExtensions.hashCode();
        result = 31 * result + excludeGlobs.hashCode();
        result = 31 * result + maxDepth;
        result = 31 * result + Boolean.hashCode(onlyFiles);
        result = 31 * result + Boolean.hashCode(followLinks);
        return result;
    }
}
