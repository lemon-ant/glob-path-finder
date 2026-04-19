/*
 * SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.lemon_ant.globpathfinder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/**
 * Immutable holder for a left and a right element, used internally to pair related values.
 *
 * @param <L> the type of the left element
 * @param <R> the type of the right element
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class Pair<L, R> {

    @NonNull
    L left;

    @NonNull
    R right;

    /**
     * Creates a new {@code Pair} from the given left and right elements.
     *
     * @param left  the left element
     * @param right the right element
     * @param <L>   the type of the left element
     * @param <R>   the type of the right element
     * @return a new {@code Pair} containing the given elements
     */
    @NonNull
    static <L, R> Pair<L, R> of(@NonNull L left, @NonNull R right) {
        return new Pair<>(left, right);
    }

    /**
     * Returns the left element (alias for {@link #getLeft()} for use as a {@code Map.Entry}-style key).
     *
     * @return the left element
     */
    @NonNull
    L getKey() {
        return left;
    }

    /**
     * Returns the right element (alias for {@link #getRight()} for use as a {@code Map.Entry}-style value).
     *
     * @return the right element
     */
    @NonNull
    R getValue() {
        return right;
    }
}
