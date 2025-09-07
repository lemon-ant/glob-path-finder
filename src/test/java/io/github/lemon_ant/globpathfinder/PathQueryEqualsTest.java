package io.github.lemon_ant.globpathfinder;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathQueryEqualsTest {

    private static Path resolveDifferentAbsoluteBase(Path referenceBase) {
        Path parent = referenceBase.getParent();
        if (parent != null && !parent.equals(referenceBase)) {
            return parent;
        }
        // Fallback: create a sibling-like different path
        return referenceBase.resolve("..").normalize().toAbsolutePath();
    }

    private static PathQuery buildQuery(
            Path baseDir,
            Set<String> includeGlobs,
            Set<String> allowedExtensions,
            Set<String> excludeGlobs,
            boolean onlyFiles,
            boolean followLinks,
            int maxDepth) {
        return PathQuery.builder()
                .baseDir(baseDir)
                .includeGlobs(includeGlobs)
                .allowedExtensions(allowedExtensions)
                .excludeGlobs(excludeGlobs)
                .onlyFiles(onlyFiles)
                .followLinks(followLinks)
                .maxDepth(maxDepth)
                .build();
    }

    @Test
    void equals_sameReference_returnsTrue() {
        Path base = Paths.get(".").toAbsolutePath().normalize();
        PathQuery query = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);

        assertThat(query.equals(query)).isTrue();
    }

    @Test
    void equals_null_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();
        PathQuery query = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);

        assertThat(query.equals(null)).isFalse();
    }

    @Test
    void equals_differentType_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();
        PathQuery query = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);

        assertThat(query.equals("not-a-PathQuery")).isFalse();
    }

    @Test
    void equals_identicalFields_returnsTrue_andHashCodesMatch() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);

        PathQuery right = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);

        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }

    @Test
    void equals_differentBaseDir_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();
        Path differentBase = resolveDifferentAbsoluteBase(base);

        PathQuery left = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right =
                buildQuery(differentBase, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void equals_differentIncludeGlobs_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.java"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right = buildQuery(
                base,
                Set.of("**/*.kt"), // different include
                Set.of("java"),
                Set.of("**/build/**"),
                true,
                true,
                42);

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void equals_differentAllowedExtensions_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.*"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right = buildQuery(
                base,
                Set.of("**/*.*"),
                Set.of(
                        "JAVA",
                        "txt"), // different extensions (case-insensitivity is internal behavior; equals compares sets)
                Set.of("**/build/**"),
                true,
                true,
                42);

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void equals_differentExcludeGlobs_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.*"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right = buildQuery(
                base,
                Set.of("**/*.*"),
                Set.of("java"),
                Set.of("**/out/**"), // different exclude
                true,
                true,
                42);

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void equals_differentOnlyFiles_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.*"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right = buildQuery(
                base,
                Set.of("**/*.*"),
                Set.of("java"),
                Set.of("**/build/**"),
                false, // different
                true,
                42);

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void equals_differentFollowLinks_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.*"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right = buildQuery(
                base,
                Set.of("**/*.*"),
                Set.of("java"),
                Set.of("**/build/**"),
                true,
                false, // different
                42);

        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void equals_differentMaxDepth_returnsFalse() {
        Path base = Paths.get(".").toAbsolutePath().normalize();

        PathQuery left = buildQuery(base, Set.of("**/*.*"), Set.of("java"), Set.of("**/build/**"), true, true, 42);
        PathQuery right = buildQuery(
                base, Set.of("**/*.*"), Set.of("java"), Set.of("**/build/**"), true, true, 7 // different
                );

        assertThat(left).isNotEqualTo(right);
    }
}
