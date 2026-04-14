package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
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

    @Test
    void tryAdvance_emptySource_returnsFalse() {
        // Given
        BatchingSpliterator<String> spliterator =
                new BatchingSpliterator<>(List.<String>of().spliterator(), 10);

        // When
        boolean advanced = spliterator.tryAdvance(value -> {});

        // Then
        assertThat(advanced).isFalse();
    }

    @Test
    void trySplit_emptySource_returnsNull() {
        // Given
        BatchingSpliterator<String> spliterator =
                new BatchingSpliterator<>(List.<String>of().spliterator(), 10);

        // When
        Spliterator<String> split = spliterator.trySplit();

        // Then
        assertThat(split).isNull();
    }

    @Test
    void trySplit_withElements_returnsNonNullBatch() {
        // Given
        List<String> elements = List.of("a", "b", "c", "d", "e");
        BatchingSpliterator<String> spliterator = new BatchingSpliterator<>(elements.spliterator(), 3);

        // When
        Spliterator<String> batch = spliterator.trySplit();

        // Then
        assertThat(batch).isNotNull();
        List<String> batchElements = new ArrayList<>();
        batch.forEachRemaining(batchElements::add);
        assertThat(batchElements).hasSize(3).containsExactly("a", "b", "c");
    }

    @Test
    void trySplit_batchSizeLargerThanSource_returnsAllElements() {
        // Given
        List<String> elements = List.of("a", "b");
        BatchingSpliterator<String> spliterator = new BatchingSpliterator<>(elements.spliterator(), 100);

        // When
        Spliterator<String> batch = spliterator.trySplit();

        // Then
        assertThat(batch).isNotNull();
        List<String> batchElements = new ArrayList<>();
        batch.forEachRemaining(batchElements::add);
        assertThat(batchElements).containsExactly("a", "b");
    }

    @Test
    void forEachRemaining_withElements_consumesAll() {
        // Given
        List<String> elements = List.of("p", "q", "r");
        BatchingSpliterator<String> spliterator = new BatchingSpliterator<>(elements.spliterator(), 10);
        List<String> consumed = new ArrayList<>();

        // When
        spliterator.forEachRemaining(consumed::add);

        // Then
        assertThat(consumed).containsExactly("p", "q", "r");
    }

    @Test
    void estimateSize_always_returnsMaxValue() {
        // Given
        BatchingSpliterator<String> spliterator =
                new BatchingSpliterator<>(List.of("a").spliterator(), 10);

        // When / Then
        assertThat(spliterator.estimateSize()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void characteristics_excludesSizedAndSubsized() {
        // Given
        Spliterator<String> sourceSpliterator = List.of("a", "b").spliterator();
        // List spliterator is SIZED | SUBSIZED | ORDERED | IMMUTABLE
        assertThat(sourceSpliterator.hasCharacteristics(Spliterator.SIZED)).isTrue();
        assertThat(sourceSpliterator.hasCharacteristics(Spliterator.SUBSIZED)).isTrue();

        BatchingSpliterator<String> spliterator = new BatchingSpliterator<>(sourceSpliterator, 10);

        // When
        int characteristics = spliterator.characteristics();

        // Then
        assertThat(characteristics & Spliterator.SIZED).isZero();
        assertThat(characteristics & Spliterator.SUBSIZED).isZero();
    }
}
