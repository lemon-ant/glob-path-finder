package io.github.lemon_ant.globpathfinder;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
class StringUtils {

    /**
     * Replaces Windows path separators with Unix separators for consistent glob matching.
     *
     * @param value the raw path or glob string
     * @return the same value with backslashes replaced by forward slashes
     */
    @NonNull
    static String normalizeToUnixSeparators(@NonNull String value) {
        return value.replace('\\', '/');
    }

    /**
     * Trims each string in the collection, filters out blank entries, applies the given
     * processor to each remaining value, and collects the results into an unmodifiable set.
     *
     * @param strings   the input collection of strings; blank or null entries are discarded
     * @param processor the transformation to apply to each trimmed, non-blank string
     * @param <TProcessResult> the type of the processed values
     * @return an unmodifiable set of processed, deduplicated results
     */
    @NonNull
    static <TProcessResult> Set<TProcessResult> processNormalizedStrings(
            @NonNull Collection<String> strings, @NonNull Function<String, TProcessResult> processor) {
        return strings.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(processor)
                .collect(toUnmodifiableSet());
    }
}
