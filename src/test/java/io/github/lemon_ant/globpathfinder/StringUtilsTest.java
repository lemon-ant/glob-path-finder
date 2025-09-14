package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class StringUtilsTest {

    @Test
    void processNormalizedStrings_whenProcessorNormalizesToSameToken_distinctIsPreserved() {
        // given
        List<String> inputStrings = List.of("gamma", "GAMMA", " gamma ");
        Function<String, String> normalizeToLowerCase = s -> s.trim().toLowerCase();

        // when
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, normalizeToLowerCase);

        // then
        assertThat(result).containsExactly("gamma");
    }

    @Test
    void processNormalizedStrings_withBlanksAndDuplicates_returnsTrimmedProcessedUniqueUnmodifiableSet() {
        // given: mixed strings (leading/trailing spaces, blanks, duplicates)
        List<String> inputStrings = List.of(" alpha ", "  ", "", "\t", "beta", "alpha");
        Function<String, String> toUpperCaseProcessor = String::toUpperCase;

        // when
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, toUpperCaseProcessor);

        // then
        assertThat(result)
                .as("Result should contain processed, trimmed, unique values")
                .containsExactlyInAnyOrder("ALPHA", "BETA");
        assertThatThrownBy(() -> result.add("GAMMA"))
                .as("Result must be unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void processNormalizedStrings_withEmptyInput_returnsEmptyUnmodifiableSet() {
        // given
        List<String> inputStrings = List.of();

        // when
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, s -> s);

        // then
        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add("anything")).isInstanceOf(UnsupportedOperationException.class);
    }
}
