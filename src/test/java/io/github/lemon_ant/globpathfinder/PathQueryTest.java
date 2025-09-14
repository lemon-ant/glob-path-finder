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
    void builder_defaults_shouldSetExpectedDefaults() {
        // when
        PathQuery pathQuery = PathQuery.builder().build();

        // then
        assertThat(pathQuery.getBaseDir()).isEqualTo(Path.of("."));
        assertThat(pathQuery.getIncludeGlobs()).isEmpty();
        assertThat(pathQuery.getAllowedExtensions()).isEmpty();
        assertThat(pathQuery.getExcludeGlobs()).isEmpty();
        assertThat(pathQuery.getMaxDepth()).isEqualTo(Integer.MAX_VALUE);
        assertThat(pathQuery.isOnlyFiles()).isTrue();
        assertThat(pathQuery.isFollowLinks()).isTrue();
    }

    @Test
    void collections_areDefensivelyCopied_shouldNotReflectExternalMutation() {
        // given
        Set<String> includes = new HashSet<>(Set.of("**/*.java", "**/*.md"));
        Set<String> exts = new HashSet<>(Set.of("java", "md"));
        Set<String> excludes = new HashSet<>(Set.of("**/generated/**"));

        PathQuery pathQuery = PathQuery.builder()
                .includeGlobs(includes)
                .allowedExtensions(exts)
                .excludeGlobs(excludes)
                .build();

        // when
        includes.add("**/*.xml");
        exts.add("xml");
        excludes.add("**/tmp/**");

        // then
        assertThat(pathQuery.getIncludeGlobs()).containsExactlyInAnyOrder("**/*.java", "**/*.md");
        assertThat(pathQuery.getAllowedExtensions()).containsExactlyInAnyOrder("java", "md");
        assertThat(pathQuery.getExcludeGlobs()).containsExactlyInAnyOrder("**/generated/**");
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
    void getVisitOptions_followLinksFalse_shouldBeEmpty() {
        // given
        PathQuery pathQuery = PathQuery.builder().followLinks(false).build();

        // when
        Set<FileVisitOption> opts = pathQuery.getVisitOptions();

        // then
        assertThat(opts).isEmpty();
    }

    @Test
    void getVisitOptions_followLinksTrue_shouldContainFollowLinks() {
        // given
        PathQuery pathQuery = PathQuery.builder().followLinks(true).build();

        // when
        Set<FileVisitOption> opts = pathQuery.getVisitOptions();

        // then
        assertThat(opts).isEqualTo(EnumSet.of(FileVisitOption.FOLLOW_LINKS));
    }

    @Test
    void getters_returnUnmodifiableSets_shouldThrowOnMutation() {
        // given
        PathQuery pathQuery = PathQuery.builder()
                .includeGlobs(Set.of("**/*.java"))
                .allowedExtensions(Set.of("java"))
                .excludeGlobs(Set.of("**/gen/**"))
                .build();

        // then
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
    void optionalFields_canBeNull_returnEmptySets() {
        // given
        PathQuery pathQuery = PathQuery.builder()
                .baseDir(null)
                .includeGlobs(null)
                .allowedExtensions(null)
                .excludeGlobs(null)
                .build();

        // then
        assertThat(pathQuery.getBaseDir()).isEqualTo(Path.of("."));
        assertThat(pathQuery.getIncludeGlobs()).isEmpty();
        assertThat(pathQuery.getAllowedExtensions()).isEmpty();
        assertThat(pathQuery.getExcludeGlobs()).isEmpty();
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
}
