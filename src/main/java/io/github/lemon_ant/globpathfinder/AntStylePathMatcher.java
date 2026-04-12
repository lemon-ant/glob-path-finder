package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.StringUtils.normalizeToUnixSeparators;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import lombok.NonNull;
import org.codehaus.plexus.util.SelectorUtils;

/**
 * A {@link PathMatcher} implementation that uses Ant/Maven-style glob semantics
 * via Plexus {@link SelectorUtils#matchPath(String, String, String, boolean)}.
 *
 * <p>The key difference from the JDK's built-in {@code glob:} matcher is that
 * {@code **} matches <b>zero or more</b> directories. For example, an Ant-style
 * double-star directory pattern for Java files matches both {@code Foo.java}
 * (zero directories) and {@code sub/Foo.java} (one directory), whereas the JDK
 * matcher requires at least one directory between {@code **} and the filename
 * segment.</p>
 *
 * @see SelectorUtils#matchPath(String, String, String, boolean)
 */
final class AntStylePathMatcher implements PathMatcher {

    private static final String UNIX_SEPARATOR = "/";

    private final String pattern;

    private AntStylePathMatcher(String pattern) {
        this.pattern = normalizeToUnixSeparators(pattern);
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
        String pathString = normalizeToUnixSeparators(path.toString());
        return SelectorUtils.matchPath(pattern, pathString, UNIX_SEPARATOR, true);
    }
}
