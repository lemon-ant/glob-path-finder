package io.github.lemon_ant.globpathfinder;

import static io.github.lemon_ant.globpathfinder.CollectionUtils.isCollectionEmpty;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
class StringUtils {

    @NonNull
    static <TProcessResult> Set<TProcessResult> processNormalizedStrings(
            @Nullable Set<String> strings, @NonNull Function<String, TProcessResult> processor) {
        return isCollectionEmpty(strings)
                ? Set.of()
                : strings.stream()
                        .map(org.apache.commons.lang3.StringUtils::trimToNull)
                        .filter(Objects::nonNull)
                        .map(processor)
                        .collect(Collectors.toUnmodifiableSet());
    }
}
