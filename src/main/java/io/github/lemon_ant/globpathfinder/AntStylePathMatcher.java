package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.StringUtils.normalizeToUnixSeparators;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import lombok.NonNull;

/**
 * A {@link PathMatcher} implementation that uses Ant/Maven-style glob semantics.
 *
 * <p>Supported patterns: {@code *} for zero or more characters within a single path
 * segment, {@code ?} for exactly one character within a segment, and {@code **} as a
 * complete segment for zero or more directories. Paths are normalized to use {@code /}
 * as the separator before matching.</p>
 *
 * <p>This is <em>not</em> full JDK {@code glob:} syntax. Character classes like
 * {@code [abc]}, alternation groups like {@code {foo,bar}}, and JDK-style escaping are
 * not supported and must not be assumed to work.</p>
 *
 * <p>One important behavioral difference from the JDK's built-in {@code glob:}
 * matcher is that {@code **} matches <b>zero or more</b> directories. For example,
 * {@code **}{@code /*.java} matches both {@code Foo.java} (zero directories) and
 * {@code sub/Foo.java} (one directory), whereas the JDK matcher requires at least one
 * directory between {@code **} and the filename segment.</p>
 */
final class AntStylePathMatcher implements PathMatcher {

    private static final String DOUBLE_STAR = "**";
    private static final char WILDCARD_ANY = '*';
    private static final char WILDCARD_ONE = '?';

    @NonNull
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
    public boolean matches(@NonNull Path path) {
        String pathString = normalizeToUnixSeparators(path.toString());
        return matchPath(pattern, pathString);
    }

    /**
     * Splits a slash-delimited path or pattern string into segments, discarding empty
     * tokens caused by a leading, trailing, or doubled {@code /}.
     *
     * @param s the path or pattern string
     * @return the non-empty segments
     */
    @NonNull
    private static String[] splitSegments(String s) {
        return Arrays.stream(s.split("/", -1)).filter(part -> !part.isEmpty()).toArray(String[]::new);
    }

    /**
     * Matches a pre-split pattern against a pre-split path starting at the given
     * positions. A {@code **} segment matches zero or more consecutive path segments.
     *
     * @param patParts  the pattern segments
     * @param piStart   starting index in {@code patParts}
     * @param pathParts the path segments
     * @param siStart   starting index in {@code pathParts}
     * @return {@code true} if the remaining pattern fully matches the remaining path
     */
    private static boolean matchSegments(String[] patParts, int piStart, String[] pathParts, int siStart) {
        byte[][] memoizedResults = new byte[patParts.length + 1][pathParts.length + 1];
        return matchSegments(patParts, piStart, pathParts, siStart, memoizedResults);
    }

    /**
     * Matches pattern and path segments starting at the supplied indexes.
     *
     * <p>This uses memoization on {@code (patternIndex, segmentIndex)} so repeated
     * states reached through multiple {@code **} expansions are evaluated only once.
     * That avoids the exponential backtracking behavior of the naive recursive
     * implementation while preserving the existing Ant-style matching semantics.</p>
     *
     * @param patParts         the pattern segments
     * @param piStart          the starting pattern segment index
     * @param pathParts        the path segments
     * @param siStart          the starting path segment index
     * @param memoizedResults  cached results indexed by pattern and path positions
     * @return {@code true} if the remaining pattern matches the remaining path
     */
    private static boolean matchSegments(
            String[] patParts,
            int piStart,
            String[] pathParts,
            int siStart,
            byte[][] memoizedResults) {
        byte cachedResult = memoizedResults[piStart][siStart];
        if (cachedResult != 0) {
            return cachedResult == 2;
        }

        int pi = piStart;
        int si = siStart;
        boolean matches = false;
        while (pi < patParts.length) {
            if (DOUBLE_STAR.equals(patParts[pi])) {
                matches = matchFromDoubleStar(patParts, pi, pathParts, si, memoizedResults);
                memoizedResults[piStart][siStart] = (byte) (matches ? 2 : 1);
                return matches;
            }
            if (si >= pathParts.length || !matchSegment(patParts[pi], pathParts[si])) {
                memoizedResults[piStart][siStart] = 1;
                return false;
            }
            pi++;
            si++;
        }

        matches = si == pathParts.length;
        memoizedResults[piStart][siStart] = (byte) (matches ? 2 : 1);
        return matches;
    }

    /**
     * Handles a {@code **} segment by trying to match the remaining pattern against
     * every possible suffix of the remaining path (zero or more segments skipped).
     *
     * @param patParts         the pattern segments
     * @param pi               index of the {@code **} segment in {@code patParts}
     * @param pathParts        the path segments
     * @param si               current position in {@code pathParts}
     * @param memoizedResults  cached results indexed by pattern and path positions
     * @return {@code true} if any skip count leads to a full match
     */
    private static boolean matchFromDoubleStar(
            String[] patParts,
            int pi,
            String[] pathParts,
            int si,
            byte[][] memoizedResults) {
        for (int skip = 0; skip <= pathParts.length - si; skip++) {
            if (matchSegments(patParts, pi + 1, pathParts, si + skip, memoizedResults)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches a single pattern segment (which may contain {@code *} and {@code ?})
     * against a single path segment using backtracking.
     *
     * @param pattern the segment pattern
     * @param text    the segment text to match against
     * @return {@code true} if the pattern matches the text
     */
    private static boolean matchSegment(String pattern, String text) {
        return matchChars(pattern, 0, text, 0);
    }

    /**
     * Character-level wildcard match with backtracking.
     * {@code *} matches zero or more characters; {@code ?} matches exactly one character.
     * Consecutive {@code *} characters are collapsed to a single wildcard.
     *
     * @param pattern  the pattern string
     * @param piStart  starting index in {@code pattern}
     * @param text     the text to match
     * @param tiStart  starting index in {@code text}
     * @return {@code true} if the remaining pattern matches the remaining text
     */
    private static boolean matchChars(String pattern, int piStart, String text, int tiStart) {
        int pi = piStart;
        int ti = tiStart;
        while (pi < pattern.length()) {
            char pc = pattern.charAt(pi);
            if (pc == WILDCARD_ANY) {
                return matchAfterStar(pattern, pi, text, ti);
            } else if (pc == WILDCARD_ONE) {
                if (ti >= text.length()) {
                    return false;
                }
                pi++;
                ti++;
            } else {
                if (ti >= text.length() || pc != text.charAt(ti)) {
                    return false;
                }
                pi++;
                ti++;
            }
        }
        return ti == text.length();
    }

    /**
     * Handles a {@code *} wildcard by advancing past consecutive stars, then trying to
     * anchor the rest of the pattern at every position from {@code ti} onward.
     *
     * @param pattern the pattern string
     * @param starPos index of the first {@code *} in {@code pattern}
     * @param text    the text to match
     * @param ti      current position in {@code text}
     * @return {@code true} if the rest of the pattern matches any suffix of the text
     */
    private static boolean matchAfterStar(String pattern, int starPos, String text, int ti) {
        int pi = starPos + 1;
        // Skip consecutive stars — a single * already matches zero or more characters
        while (pi < pattern.length() && pattern.charAt(pi) == WILDCARD_ANY) {
            pi++;
        }
        if (pi == pattern.length()) {
            // Trailing * matches the rest of the text unconditionally
            return true;
        }
        for (int j = ti; j <= text.length(); j++) {
            if (matchChars(pattern, pi, text, j)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchPath(String pattern, String path) {
        return matchSegments(splitSegments(pattern), 0, splitSegments(path), 0);
    }
}
