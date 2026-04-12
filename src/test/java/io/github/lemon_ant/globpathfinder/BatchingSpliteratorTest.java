package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchingSpliteratorTest {

    @Test
    void constructor_nullSource_throwsNullPointerException() {
        // When / Then
        assertThatThrownBy(() -> new BatchingSpliterator<>(null, 5)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_zeroBatchSize_throwsIllegalArgumentException() {
        // When / Then
        assertThatThrownBy(() -> new BatchingSpliterator<>(List.of("a").spliterator(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize must be positive");
    }

    @Test
    void constructor_negativeBatchSize_throwsIllegalArgumentException() {
        // When / Then
        assertThatThrownBy(() -> new BatchingSpliterator<>(List.of("a").spliterator(), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize must be positive");
    }

    @Test
    void tryAdvance_withElements_consumesOneElement() {
        // Given
        List<String> elements = List.of("x", "y", "z");
        BatchingSpliterator<String> spliterator = new BatchingSpliterator<>(elements.spliterator(), 10);
        List<String> consumed = new ArrayList<>();

        // When
        boolean advanced = spliterator.tryAdvance(consumed::add);

        // Then
        assertThat(advanced).isTrue();
        assertThat(consumed).containsExactly("x");
    }
}
