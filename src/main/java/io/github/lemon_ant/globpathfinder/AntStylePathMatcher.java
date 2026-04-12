package io.github.lemon_ant.globpathfinder;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import lombok.NonNull;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * A {@link PathMatcher} implementation that uses Ant/Maven-style glob semantics
 * via Plexus {@link SelectorUtils#matchPath(String, String, String, boolean)}.
 *
 * <p>The key difference from the JDK's built-in {@code glob:} matcher is that
 * {@code **} matches <b>zero or more</b> directories. For example, the pattern
 * {@code ** / *.java} matches both {@code Foo.java} (zero directories) and
 * {@code sub/Foo.java} (one directory), whereas the JDK matcher requires at
 * least one directory between {@code **} and the filename segment.</p>
 *
 * @see SelectorUtils#matchPath(String, String, String, boolean)
 */
final class AntStylePathMatcher implements PathMatcher {

    private static final String UNIX_SEPARATOR = "/";

    private final String pattern;

    /**
     * Creates an Ant-style path matcher for the given glob pattern.
     *
     * @param pattern the glob pattern using Ant/Maven conventions (e.g. {@code ** / *.java})
     */
    AntStylePathMatcher(@NonNull String pattern) {
        this.pattern = normalizeToUnix(pattern);
    }

    /**
     * Creates a new {@link PathMatcher} that uses Ant/Maven-style glob semantics.
     *
     * @param pattern the glob pattern using Ant/Maven conventions
     * @return a compiled {@link PathMatcher}
     */
    @NonNull
    static PathMatcher compile(@NonNull String pattern) {
        return new AntStylePathMatcher(pattern);
    }

    @Override
    public boolean matches(Path path) {
        String pathString = normalizeToUnix(path.toString());
        return SelectorUtils.matchPath(pattern, pathString, UNIX_SEPARATOR, true);
    }

    /**
     * Normalizes backslashes to forward slashes for consistent cross-platform matching.
     */
    @NonNull
    private static String normalizeToUnix(String value) {
        return value.replace('\\', '/');
    }
}
