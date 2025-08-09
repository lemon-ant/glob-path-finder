package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathQueryTest {

    @Test
    void builder_defaults_shouldSetExpectedDefaults() {
        // given
        Set<String> includes = Set.of("**/*.java");

        // when
        PathQuery q = PathQuery.builder().baseDir(null).includeGlobs(includes).build();

        // then
        assertThat(q.getBaseDir()).isNull();
        assertThat(q.getIncludeGlobs()).containsExactlyInAnyOrderElementsOf(includes);
        assertThat(q.getAllowedExtensions()).isNull();
        assertThat(q.getExcludeGlobs()).isNull();
        assertThat(q.getMaxDepth()).isEqualTo(Integer.MAX_VALUE);
        assertThat(q.isOnlyFiles()).isTrue();
        assertThat(q.isFollowLinks()).isTrue();
    }

    @Test
    void visitOptions_followLinksTrue_shouldContainFollowLinks() {
        // given
        PathQuery q = PathQuery.builder()
                .includeGlobs(Set.of("**/*"))
                .followLinks(true)
                .build();

        // when
        Set<FileVisitOption> opts = q.visitOptions();

        // then
        assertThat(opts).isEqualTo(EnumSet.of(FileVisitOption.FOLLOW_LINKS));
    }

    @Test
    void visitOptions_followLinksFalse_shouldBeEmpty() {
        // given
        PathQuery q = PathQuery.builder()
                .includeGlobs(Set.of("**/*"))
                .followLinks(false)
                .build();

        // when
        Set<FileVisitOption> opts = q.visitOptions();

        // then
        assertThat(opts).isEmpty();
    }

    @Test
    void collections_areDefensivelyCopied_shouldNotReflectExternalMutation() {
        // given
        Set<String> includes = new HashSet<>(Set.of("**/*.java", "**/*.md"));
        Set<String> exts = new HashSet<>(Set.of("java", "md"));
        Set<String> excludes = new HashSet<>(Set.of("**/generated/**"));

        PathQuery q = PathQuery.builder()
                .baseDir(Path.of("."))
                .includeGlobs(includes)
                .allowedExtensions(exts)
                .excludeGlobs(excludes)
                .build();

        // when
        includes.add("**/*.xml");
        exts.add("xml");
        excludes.add("**/tmp/**");

        // then
        assertThat(q.getIncludeGlobs()).containsExactlyInAnyOrder("**/*.java", "**/*.md");
        assertThat(q.getAllowedExtensions()).containsExactlyInAnyOrder("java", "md");
        assertThat(q.getExcludeGlobs()).containsExactlyInAnyOrder("**/generated/**");
    }

    @Test
    void getters_returnUnmodifiableSets_shouldThrowOnMutation() {
        // given
        PathQuery q = PathQuery.builder()
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .build();

        // then
        assertThatThrownBy(() -> q.getIncludeGlobs().add("**/*.md")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(q.getAllowedExtensions()).isNotNull();
        assertThatThrownBy(() -> q.getAllowedExtensions().add("md")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(q.getExcludeGlobs()).isNotNull();
        assertThatThrownBy(() -> q.getExcludeGlobs().add("**/tmp/**"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionalSets_canBeNull_shouldRemainNull() {
        // given
        PathQuery q = PathQuery.builder()
                .includeGlobs(Set.of("**/*"))
                .allowedExtensions(null)
                .excludeGlobs(null)
                .build();

        // then
        assertThat(q.getAllowedExtensions()).isNull();
        assertThat(q.getExcludeGlobs()).isNull();
    }

    @Test
    void toBuilder_shouldPreserveAndAllowTweaks() {
        // given
        PathQuery base = PathQuery.builder()
                .baseDir(Path.of("src"))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/generated/**"))
                .maxDepth(5)
                .onlyFiles(true)
                .followLinks(false)
                .build();

        // when
        PathQuery tweaked = base.toBuilder().maxDepth(10).build();

        // then
        assertThat(tweaked.getBaseDir()).isEqualTo(Path.of("src"));
        assertThat(tweaked.getIncludeGlobs()).containsExactlyInAnyOrder("**/*.java");
        assertThat(tweaked.getAllowedExtensions()).containsExactlyInAnyOrder("java");
        assertThat(tweaked.getExcludeGlobs()).containsExactlyInAnyOrder("**/generated/**");
        assertThat(tweaked.getMaxDepth()).isEqualTo(10);
        assertThat(tweaked.isOnlyFiles()).isTrue();
        assertThat(tweaked.isFollowLinks()).isFalse();
    }

    @Test
    void equalsHashCode_contract_basicShouldWork() {
        // given
        PathQuery a1 = PathQuery.builder()
                .baseDir(Path.of("."))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .maxDepth(7)
                .onlyFiles(true)
                .followLinks(false)
                .build();

        PathQuery a2 = PathQuery.builder()
                .baseDir(Path.of("."))
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .maxDepth(7)
                .onlyFiles(true)
                .followLinks(false)
                .build();

        PathQuery b = PathQuery.builder()
                .baseDir(Path.of("src"))
                .includeGlobs(Set.of("**/*"))
                .build();

        // then
        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1).isNotEqualTo(b);
    }

    @Test
    void builder_nullIncludeGlobs_shouldFailFastWithNpe() {
        // given/when/then
        assertThatNullPointerException().isThrownBy(() -> PathQuery.builder()
                .baseDir(Path.of("."))
                .includeGlobs(null) // Lombok will pass null to the ctor; ctor has @NonNull + Set.copyOf
                .build());
    }
}
