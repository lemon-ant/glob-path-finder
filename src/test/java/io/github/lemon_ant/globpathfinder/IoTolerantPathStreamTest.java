/*
 * SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class IoTolerantPathStreamTest {

    private static final Path BASE = Path.of("/test/base");

    @Test
    void wrap_nullSourceStream_throwsNullPointerException() {
        // When / Then
        assertThatThrownBy(() -> IoTolerantPathStream.wrap(null, BASE)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void wrap_nullBasePath_throwsNullPointerException() {
        // When / Then
        assertThatThrownBy(() -> IoTolerantPathStream.wrap(Stream.empty(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void wrap_normalElements_passesThroughUnchanged() {
        // Given
        List<Path> elements = List.of(Path.of("a/b"), Path.of("c/d"));

        // When
        List<Path> result;
        try (Stream<Path> wrapped = IoTolerantPathStream.wrap(elements.stream(), BASE)) {
            result = wrapped.collect(Collectors.toUnmodifiableList());
        }

        // Then
        assertThat(result).containsExactlyInAnyOrder(Path.of("a/b"), Path.of("c/d"));
    }

    @Test
    void tryAdvance_ioExceptionDuringAdvance_swallowsAndReturnsFalse() {
        // Given
        Spliterator<Path> throwingSpliterator = new Spliterator<>() {
            @Override
            public boolean tryAdvance(Consumer<? super Path> action) {
                throw new UncheckedIOException(new IOException("simulated I/O error"));
            }

            @Override
            public Spliterator<Path> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return 0;
            }
        };
        Stream<Path> sourceStream = StreamSupport.stream(throwingSpliterator, false);
        Stream<Path> wrapped = IoTolerantPathStream.wrap(sourceStream, BASE);
        List<Path> collected = new ArrayList<>();

        // When
        boolean advanced = wrapped.spliterator().tryAdvance(collected::add);

        // Then
        assertThat(advanced).isFalse();
        assertThat(collected).isEmpty();
    }

    @Test
    void forEachRemaining_ioExceptionDuringIteration_swallowsAndStops() {
        // Given
        Spliterator<Path> throwingSpliterator = new Spliterator<>() {
            @Override
            public boolean tryAdvance(Consumer<? super Path> action) {
                return false;
            }

            @Override
            public void forEachRemaining(Consumer<? super Path> action) {
                throw new UncheckedIOException(new IOException("simulated I/O error"));
            }

            @Override
            public Spliterator<Path> trySplit() {
                return null;
            }

            @Override
            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            @Override
            public int characteristics() {
                return 0;
            }
        };
        Stream<Path> sourceStream = StreamSupport.stream(throwingSpliterator, false);
        Stream<Path> wrapped = IoTolerantPathStream.wrap(sourceStream, BASE);
        List<Path> collected = new ArrayList<>();

        // When / Then – no exception propagated
        wrapped.spliterator().forEachRemaining(collected::add);
        assertThat(collected).isEmpty();
    }

    @Test
    void trySplit_sourceCanSplit_returnsWrappedSplit() {
        // Given
        List<Path> elements = List.of(Path.of("a"), Path.of("b"), Path.of("c"), Path.of("d"));
        Stream<Path> sourceStream = elements.stream();
        Stream<Path> wrapped = IoTolerantPathStream.wrap(sourceStream, BASE);

        // When
        Spliterator<Path> split = wrapped.spliterator().trySplit();

        // Then
        assertThat(split).isNotNull();
    }

    @Test
    void trySplit_sourceCannotSplit_returnsNull() {
        // Given – unknown-size iterator spliterator does not support splitting
        Stream<Path> sourceStream =
                StreamSupport.stream(Spliterators.spliteratorUnknownSize(Collections.<Path>emptyIterator(), 0), false);
        Stream<Path> wrapped = IoTolerantPathStream.wrap(sourceStream, BASE);

        // When
        Spliterator<Path> split = wrapped.spliterator().trySplit();

        // Then
        assertThat(split).isNull();
    }
}
