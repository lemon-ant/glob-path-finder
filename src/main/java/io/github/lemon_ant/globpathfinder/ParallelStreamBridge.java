package io.github.lemon_ant.globpathfinder;

import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;

/**
 * Bridges a stream with a potentially unsplittable source spliterator into a pull-based splittable stream.
 *
 * <p>The bridge does <b>not</b> materialize elements into a collection. Instead, sibling spliterators
 * created via {@link Spliterator#trySplit()} pull elements from a shared iterator under a small lock.
 * This allows downstream parallel processing per element while keeping memory usage constant.</p>
 *
 * <p><b>Characteristics:</b> The bridge propagates only characteristics that remain valid after
 * transitioning to a shared-pull spliterator: {@link Spliterator#NONNULL} and {@link Spliterator#DISTINCT}
 * are carried over when present in the source. Encounter order ({@link Spliterator#ORDERED}) and sort
 * order ({@link Spliterator#SORTED}) are <em>not</em> preserved because parallel fan-out does not
 * guarantee delivery order.</p>
 */
@UtilityClass
class ParallelStreamBridge {

    private static final int DEFAULT_MAX_SPLITS = 1024;

    /** Characteristics safe to propagate: order-independent and element-value-preserving. */
    private static final int SAFE_CHARACTERISTICS = Spliterator.NONNULL | Spliterator.DISTINCT;

    static <T> Stream<T> parallelize(Stream<T> sourceStream) {
        Objects.requireNonNull(sourceStream, "sourceStream");
        Spliterator<T> sourceSpliterator = sourceStream.spliterator();
        int characteristics = sourceSpliterator.characteristics() & SAFE_CHARACTERISTICS;
        Iterator<T> sourceIterator = Spliterators.iterator(sourceSpliterator);
        SharedIteratorState<T> state = new SharedIteratorState<>(sourceIterator, DEFAULT_MAX_SPLITS, characteristics);
        Spliterator<T> spliterator = new SharedPullSpliterator<>(state);
        return StreamSupport.stream(spliterator, true).onClose(sourceStream::close);
    }

    private static final class SharedIteratorState<T> {
        private final ReentrantLock lock = new ReentrantLock();
        private final Iterator<T> iterator;
        private final AtomicInteger remainingSplitBudget;
        private final int characteristics;

        private SharedIteratorState(Iterator<T> iterator, int maxSplits, int characteristics) {
            this.iterator = iterator;
            this.remainingSplitBudget = new AtomicInteger(maxSplits);
            this.characteristics = characteristics;
        }
    }

    private static final class SharedPullSpliterator<T> implements Spliterator<T> {
        private final SharedIteratorState<T> state;

        private SharedPullSpliterator(SharedIteratorState<T> state) {
            this.state = state;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action, "action");
            T nextItem;
            state.lock.lock();
            try {
                if (!state.iterator.hasNext()) {
                    return false;
                }
                nextItem = state.iterator.next();
            } finally {
                state.lock.unlock();
            }
            // Execute downstream action outside the lock, otherwise expensive user work is serialized.
            action.accept(nextItem);
            return true;
        }

        @Override
        public Spliterator<T> trySplit() {
            int budget = state.remainingSplitBudget.getAndDecrement();
            if (budget <= 0) {
                return null;
            }
            return new SharedPullSpliterator<>(state);
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return state.characteristics;
        }
    }
}
