package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StringUtilsTest {

    @Test
    void normalizeToUnixSeparators_backslashes_replacedWithForwardSlash() {
        // When / Then
        assertThat(StringUtils.normalizeToUnixSeparators("src\\main\\java\\Foo.java"))
                .isEqualTo("src/main/java/Foo.java");
    }

    @Test
    void normalizeToUnixSeparators_alreadyUnixSeparators_returnedUnchanged() {
        // When / Then
        assertThat(StringUtils.normalizeToUnixSeparators("src/main/java/Foo.java"))
                .isEqualTo("src/main/java/Foo.java");
    }

    @ParameterizedTest
    @CsvSource({"C:\\Users\\foo, C:/Users/foo", "D:\\\\share, D://share"})
    void normalizeToUnixSeparators_windowsPaths_allBackslashesReplaced(String input, String expected) {
        // When / Then
        assertThat(StringUtils.normalizeToUnixSeparators(input)).isEqualTo(expected);
    }

    @Test
    void processNormalizedStrings_processorNormalizesToSameToken_preservesDistinct() {
        // Given
        List<String> inputStrings = List.of("gamma", "GAMMA", " gamma ");
        Function<String, String> normalizeToLowerCase = value -> value.trim().toLowerCase();

        // When
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, normalizeToLowerCase);

        // Then
        assertThat(result).containsExactly("gamma");
    }

    @Test
    void processNormalizedStrings_blanksAndDuplicates_returnsTrimmedUniqueSet() {
        // Given
        List<String> inputStrings = List.of(" alpha ", "  ", "", "\t", "beta", "alpha");
        Function<String, String> toUpperCaseProcessor = String::toUpperCase;

        // When
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, toUpperCaseProcessor);

        // Then
        assertThat(result)
                .as("Result should contain processed, trimmed, unique values")
                .containsExactlyInAnyOrder("ALPHA", "BETA");
        assertThatThrownBy(() -> result.add("GAMMA"))
                .as("Result must be unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void processNormalizedStrings_emptyInput_returnsEmptySet() {
        // Given
        List<String> inputStrings = List.of();

        // When
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, value -> value);

        // Then
        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.add("anything")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void processNormalizedStrings_nullEntries_discardedWithoutNpe() {
        // Given
        List<String> inputStrings = new java.util.ArrayList<>();
        inputStrings.add("alpha");
        inputStrings.add(null);
        inputStrings.add("beta");

        // When
        Set<String> result = StringUtils.processNormalizedStrings(inputStrings, value -> value);

        // Then
        assertThat(result).containsExactlyInAnyOrder("alpha", "beta");
    }
}
