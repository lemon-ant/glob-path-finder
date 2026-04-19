/*
 * SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathQueryTest {

    @Test
    void builder_defaults_setsExpectedDefaults() {
        // When
        PathQuery pathQuery = PathQuery.builder().build();

        // Then
        assertThat(pathQuery.getBaseDir()).isEqualTo(Path.of("."));
        assertThat(pathQuery.getIncludeGlobs()).isEmpty();
        assertThat(pathQuery.getAllowedExtensions()).isEmpty();
        assertThat(pathQuery.getExcludeGlobs()).isEmpty();
        assertThat(pathQuery.getMaxDepth()).isEqualTo(Integer.MAX_VALUE);
        assertThat(pathQuery.isOnlyFiles()).isTrue();
        assertThat(pathQuery.isFollowLinks()).isTrue();
        assertThat(pathQuery.isFailFastOnError()).isTrue();
    }

    @Test
    void collections_defensivelyCopied_doesNotReflectExternalMutation() {
        // Given
        Set<String> includes = new HashSet<>(Set.of("**/*.java", "**/*.md"));
        Set<String> extensions = new HashSet<>(Set.of("java", "md"));
        Set<String> excludes = new HashSet<>(Set.of("**/generated/**"));

        PathQuery pathQuery = PathQuery.builder()
                .includeGlobs(includes)
                .allowedExtensions(extensions)
                .excludeGlobs(excludes)
                .build();

        // When
        includes.add("**/*.xml");
        extensions.add("xml");
        excludes.add("**/tmp/**");

        // Then
        assertThat(pathQuery.getIncludeGlobs()).containsExactlyInAnyOrder("**/*.java", "**/*.md");
        assertThat(pathQuery.getAllowedExtensions()).containsExactlyInAnyOrder("java", "md");
        assertThat(pathQuery.getExcludeGlobs()).containsExactlyInAnyOrder("**/generated/**");
    }

    @Test
    void equalsHashCode_identicalPairs_contractSatisfied() {
        // Given
        PathQuery firstQuery = PathQuery.builder()
                .baseDir(Path.of("."))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .maxDepth(7)
                .onlyFiles(true)
                .followLinks(false)
                .build();

        PathQuery identicalQuery = PathQuery.builder()
                .baseDir(Path.of("."))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .maxDepth(7)
                .onlyFiles(true)
                .followLinks(false)
                .build();

        PathQuery differentQuery = PathQuery.builder()
                .baseDir(Path.of("src"))
                .includeGlobs(Set.of("**/*"))
                .build();

        // When / Then
        assertThat(firstQuery).isEqualTo(identicalQuery);
        assertThat(firstQuery.hashCode()).isEqualTo(identicalQuery.hashCode());
        assertThat(firstQuery).isNotEqualTo(differentQuery);
    }

    @Test
    void getVisitOptions_followLinksFalse_returnsEmpty() {
        // Given
        PathQuery pathQuery = PathQuery.builder().followLinks(false).build();

        // When
        Set<FileVisitOption> visitOptions = pathQuery.getVisitOptions();

        // Then
        assertThat(visitOptions).isEmpty();
    }

    @Test
    void getVisitOptions_followLinksTrue_containsFollowLinks() {
        // Given
        PathQuery pathQuery = PathQuery.builder().followLinks(true).build();

        // When
        Set<FileVisitOption> visitOptions = pathQuery.getVisitOptions();

        // Then
        assertThat(visitOptions).isEqualTo(EnumSet.of(FileVisitOption.FOLLOW_LINKS));
    }

    @Test
    void getters_unmodifiableSets_throwOnMutation() {
        // Given
        PathQuery pathQuery = PathQuery.builder()
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .build();

        // When / Then
        assertThatThrownBy(() -> pathQuery.getIncludeGlobs().add("**/*.md"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(pathQuery.getAllowedExtensions()).isNotNull();
        assertThatThrownBy(() -> pathQuery.getAllowedExtensions().add("md"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(pathQuery.getExcludeGlobs()).isNotNull();
        assertThatThrownBy(() -> pathQuery.getExcludeGlobs().add("**/tmp/**"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionalFields_nullInput_defaultsToEmpty() {
        // Given
        PathQuery pathQuery = PathQuery.builder()
                .baseDir(null)
                .includeGlobs(null)
                .allowedExtensions(null)
                .excludeGlobs(null)
                .build();

        // Then
        assertThat(pathQuery.getBaseDir()).isEqualTo(Path.of("."));
        assertThat(pathQuery.getIncludeGlobs()).isEmpty();
        assertThat(pathQuery.getAllowedExtensions()).isEmpty();
        assertThat(pathQuery.getExcludeGlobs()).isEmpty();
    }

    @Test
    void maxDepth_negativeValue_treatedAsUnlimited() {
        // When
        PathQuery pathQuery = PathQuery.builder().maxDepth(-1).build();

        // Then
        assertThat(pathQuery.getMaxDepth()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void toBuilder_tweakedField_preservesOtherFields() {
        // Given
        PathQuery base = PathQuery.builder()
                .baseDir(Path.of("src"))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/generated/**"))
                .maxDepth(5)
                .onlyFiles(true)
                .followLinks(false)
                .build();

        // When
        PathQuery tweaked = base.toBuilder().maxDepth(10).build();

        // Then
        assertThat(tweaked.getBaseDir()).isEqualTo(Path.of("src"));
        assertThat(tweaked.getIncludeGlobs()).containsExactlyInAnyOrder("**/*.java");
        assertThat(tweaked.getAllowedExtensions()).containsExactlyInAnyOrder("java");
        assertThat(tweaked.getExcludeGlobs()).containsExactlyInAnyOrder("**/generated/**");
        assertThat(tweaked.getMaxDepth()).isEqualTo(10);
        assertThat(tweaked.isOnlyFiles()).isTrue();
        assertThat(tweaked.isFollowLinks()).isFalse();
    }
}
