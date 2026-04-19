<!--

    SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
    SPDX-License-Identifier: Apache-2.0

-->
# AGENTS.md

This file defines repository-wide conventions for coding agents working in this repository.

## Scope

- Applies to the whole repository unless a more specific convention document says otherwise.
- This document contains both repository-wide and test-specific conventions.

## Convention maintenance

- During every task, look for stable conventions that become clear from review feedback or repeated user guidance.
- Keep `AGENTS.md` and `.github/copilot-instructions.md` aligned.
  - `AGENTS.md` defines both repository-wide and test-specific rules.
  - `.github/copilot-instructions.md` must contain the complete operative rule set from this file so Copilot can follow it without relying on cross-file traversal.
- If a rule is missing, unclear, or no longer accurate in the current docs, update all affected instruction files in the same task.
- If a documented rule is ambiguous, clarify the document rather than relying on unwritten expectations for future sessions.
- Review comments and user requests may be mistaken; for disputed framework/plugin/tool behavior, verify against official documentation before changing code.
- If a requested change conflicts with official documentation or established framework/plugin behavior, do not apply it blindly.
  - Explain the conflict clearly in review feedback.
  - Provide the documentation-aligned alternative and prefer that variant.

## General code conventions

- Prefer the smallest complete change that solves the reviewed problem.
- Keep changes surgical and avoid unrelated cleanup.
- Licensing policy is mandatory for all tracked files in this repository.
  - Every tracked text/source/config/documentation file must include SPDX metadata.
  - Required file-level SPDX lines:
    - `SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>`
    - `SPDX-License-Identifier: Apache-2.0`
  - Allowed omission: repository license body file (`LICENSE`) may keep only the canonical Apache-2.0 legal text without SPDX header.
  - Do not introduce additional third-party license headers unless a file is truly imported from a differently licensed upstream source.
- Avoid cosmetic-only churn in production files (for example adding/removing separator blank lines) when there is no behavioral or readability gain tied to the task.
- Reuse existing project and library utilities before introducing custom helpers.
- Prefer explicit Java types over `var`.
- Prefer normal imports over repeated fully qualified class names.
- Prefer Lombok for routine boilerplate such as getters, setters, constructors, and `toString` / `equals` / `hashCode` when it matches the surrounding style.
- For DTO/model/state-holder classes that primarily carry data (in production and tests), prefer immutable Lombok shapes (for example `@Value`) unless mutability is required by the use case.
- When a simple data-carrier class only needs a narrower constructor than Lombok's default, keep `@Value` and add the constructor visibility override instead of decomposing `@Value` into separate Lombok annotations.
- When an annotation argument only repeats the library or framework default behavior, omit it instead of spelling it out explicitly.
- Use the minimal necessary access level for production classes, constructors, and methods.
  - Prefer package-private over `public` when access outside the package is not required.
  - Prefer `private` for nested classes, constructors, and helpers when they are only used by the enclosing type.
  - For nested helper/data-carrier types created only by the enclosing type, keep their constructors `private`; tests are not a reason to widen constructor visibility.
- Keep production models and value objects focused on state plus simple accessors or validation.
  - Move non-trivial business, filtering, parsing, and transformation logic into dedicated service or processing classes.
- Explicitly annotate field and non-private method nullability with `@NonNull` / `@Nullable` where applicable; private method parameters may stay implicit when the intent is already obvious.
- Reference-returning private methods must declare explicit `@NonNull` or `@Nullable` return annotations.
  - Private method parameters must not use Lombok `@NonNull`; it adds redundant runtime null checks for private helpers.
  - Use `@Nullable` on a private parameter only when that private helper intentionally accepts `null`; otherwise leave private parameters unannotated.
  - Place method-level nullability annotations on their own line above the method declaration instead of inline in the signature.
- Prefer Stream API when it makes the control flow clearer and more concise than imperative loops.
- If a boolean helper is always consumed through negation at its call sites, invert the helper logic and rename it so callers stay positive and direct.
- Prefer `get` only for conventional object-model/DTO getters; for computed values, searches, conditional lookups, or transformations, prefer a more specific verb such as `find`, `resolve`, `collect`, `compute`, or `merge`.
- For non-get behavior methods, start the method name with a clear verb.
  - Prefer explicit verb-led names such as `find`, `resolve`, `collect`, `compute`, `merge`, `parse`, `format`, or `render`.
  - Avoid ambiguous prefixes such as `toXxx` when a clearer verb-based name fits the method behavior.
- Prefer static imports for frequently used assertion/helper methods when repeated type-qualified calls add noise.
- Do not use `protected` fields; keep fields `private` and expose only the narrow protected accessor methods that subclasses actually need.
- Prefer the shorter `src*` naming family (`srcFile`, `srcPath`, `srcCode`, `srcDiff`) for source-related variables and parameters.
- Prefer clear, fully descriptive variable names; avoid non-obvious abbreviations unless the abbreviation is an established term (for example `URL`, `URI`, `ID`) or an established repository abbreviation such as the `src*` naming family.
- Never shorten or abbreviate a variable name when a more descriptive name exists; if the type is `FileProcessingResult`, the variable must be `fileProcessingResult`, not `result`.
- Every non-private production method and constructor must have concise JavaDoc that states the purpose, documents parameters, and documents the return value when applicable.
  - Exception: do not add JavaDoc to standard `Object` overrides such as `equals`, `hashCode`, and `toString`.
- Annotate every non-private method's reference-type parameters and non-primitive return type with explicit nullability using `lombok.NonNull`, `edu.umd.cs.findbugs.annotations.Nullable`, or the accepted legacy `javax.annotation` nullability annotations already present in the codebase.
- Do not change standard `Object` method signatures when overriding them.
  - Do not add nullability annotations to `Object` overrides just to satisfy local conventions.
  - Preserve standard contracts exactly, especially `equals(Object)`.
- Use `@UtilityClass` for classes that contain only static utility methods and should never be instantiated; this applies to both production code and test utilities.
- Repository-wide convention: do not introduce Java records in production code or shared test infrastructure; use classes with Lombok instead where appropriate.
  - Java fixtures under `src/test/resources/test-cases/**` may still use records when a scenario explicitly tests record handling.
- If a utility is shared across processing phases (for example translator and sorter), place it in a neutral package instead of under a phase-specific package.
- Wrap collections once when returning or handing off a collection that was built mutably in the current method, including private/package-private helpers, so the receiver cannot mutate the handed-off instance.
- Do not add a second unmodifiable wrapper or defensive copy when the collection already stays immutable upstream or never leaves the local method scope.
- Non-obvious build/configuration workarounds (for example temporary dependency overrides for transitive vulnerabilities) must include a nearby comment that explains why the workaround exists, which upstream component requires it, and when it can be removed.
- When a piece of code intentionally keeps a non-obvious, previously reverted, or easy-to-"simplify" behavior because of an external constraint, leave a nearby comment that explains why it exists, what constraint it preserves, and why it should not be changed casually.
- When debugging uncovers a non-obvious runtime/framework edge case (for example parser/evaluator recursion traps), document the guard/workaround with a nearby code comment so future refactors do not remove it accidentally.

## Test conventions

This repository uses a small set of conventions for unit and integration tests.

The goals are:

- Make tests readable at a glance (predictable structure).
- Make failures actionable (clear names and error messages).
- Keep maintenance low (shared helpers, minimal duplication).
- Avoid “false regressions” caused by broken fixtures (fixtures must compile).

If review feedback or repeated task work reveals a stable testing rule that is missing here or not clearly stated, update this document in the same task.
Keep `AGENTS.md` and `.github/copilot-instructions.md` aligned:

- `AGENTS.md` is the canonical source of repository-wide and test-specific rules.
- `.github/copilot-instructions.md` must mirror the complete operative rule set from this file so Copilot can follow it without relying on cross-file traversal.
- When a test rule changes here, update the mirrored guidance in `.github/copilot-instructions.md` in the same task.

### Tooling and libraries

- **JUnit 5** is the test runner.
- **AssertJ** is the assertion library.
  - Do not use `org.junit.jupiter.api.Assertions.*` in new/updated tests.
- Prefer ordinary imports over repeated fully qualified names in tests.
- Prefer using production pipeline building blocks (parsers, converters, compilers, factories) instead of test-only reimplementations.
- When an annotation argument in test code only repeats the library or framework default behavior, omit it instead of spelling it out explicitly.
- When test code overrides standard `Object` methods, preserve the standard signature exactly.
  - Do not add nullability annotations to `Object` overrides in tests.
  - In particular, keep `equals(Object)` unchanged.
- Test code and test resources follow the same repository licensing policy.
  - Every tracked test file must include SPDX metadata.
  - Required lines:
    - `SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>`
    - `SPDX-License-Identifier: Apache-2.0`

### Code reuse and deduplication

When writing or refactoring tests, always look for overlapping code fragments and reuse existing helpers. Duplicating the same snippet across multiple tests is discouraged.

Rules:

- Before copying code into another test, search for an existing shared test utility and reuse it.
- If similar fragments appear in more than one test (or are likely to be reused), extract them into a shared test utility class.
- Re-run this reuse analysis regularly: when adding new tests, when refactoring tests, and during cleanup passes.
- When a test in another package must create a production type with package-private construction, prefer a dedicated test creator/helper in the target package instead of widening the production constructor visibility.

### Naming

#### Test methods

- JUnit test method names must follow **exactly 3 segments**:

  `subject_condition_expectedResult`

Scope note:

- This naming rule applies only to executable test methods (for example methods annotated with `@Test`, `@ParameterizedTest`, and other JUnit test-invocation annotations).
- It does **not** apply to lifecycle and helper methods such as `@BeforeEach`, `@BeforeAll`, `@AfterEach`, `@AfterAll`, or private utility methods; name those with normal Java conventions (for example `setUp`, `tearDown`, `createSampleFile`).

Examples:

- `compileConfig_validYaml_produceSingleRootGroup`
- `resolveGroups_nestedMatch_winOverParentGroup`
- `helpCommand_rootHelpRequested_printUsageInformation`

Guidelines:

- `subject` names what is being tested. It usually mirrors the production method, command, or feature name.
- `condition` states only the relevant precondition/input shape.
- `expectedResult` states the observable outcome.
- Do not use filler words such as `should`, `when`, `then`, `must`, or similar “BDD glue” inside the method name.
- Avoid vague words (`works`, `ok`, `smoke1`). Prefer intent-revealing words.
- Keep the condition minimal but specific.

#### Test classes

- Prefer `<ProductionClassName>Test` for unit tests.
- Prefer `<FeatureOrScenarioName>Test` for integration tests that cover a pipeline.
- If you need multiple scenarios, prefer `@Nested` classes instead of splitting into many test classes.

### Structure

#### Given / When / Then blocks

- Each test body must be split into contiguous blocks using comments:

  - `// Given`
  - `// When`
  - `// Then`

Rules:

- Do not insert empty lines **inside** a block.
- Insert **exactly one** empty line **between blocks**.
  - In particular, there must be a blank line **before** `// When` and a blank line **before** `// Then` (and before combined blocks such as `// When / Then`).
- Do not insert an empty line at the very beginning of the method body before `// Given`.
- Keep each block contiguous and focused.

Notes:

- In some tests, it is valid to **merge blocks** when it improves readability.
  - Common case: very small tests may use `// Given / When` together if separating them would add noise.
  - Another common case: non-exception assertion tests may use `// When / Then` together when the action and assertion fit naturally in one block (for example `assertThat(value).isEqualTo(...)`).
  - This is allowed as long as the combined block stays contiguous and clear.

#### Exception tests

When a test verifies that a method throws an exception, **always** split the action and the assertion into separate `// When` and `// Then` blocks.

- `// When` block: capture the thrown exception using `catchThrowable(...)` from AssertJ.
- `// Then` block: assert on the captured throwable using `assertThat(thrown)`.

Use `catchThrowableOfType(ExceptionType.class, ...)` instead of `catchThrowable(...)` only when the specific exception type needs to be accessed directly (for example, calling type-specific methods on it) in the `// Then` block.

**Do not** use `// When / Then` with `assertThatThrownBy(...)`.

Recommended skeleton:

```java
@Test
void resolveGroups_nestedMatch_winOverParentGroup() {
    // Given
    ...

    // When
    ...

    // Then
    ...
}
```

#### Parameterized tests

- Use parameterized tests when it reduces repetition and improves readability.
- The method naming rule (3 segments) still applies.

### Fixtures and resources

#### Where fixtures live

- Store fixtures under:

  `src/test/resources/test-cases/**`

- Use explicit scenario folder names (avoid generic `example/`).
- Prefer resource fixtures under `src/test/resources/test-cases/**` over large inline YAML/Java strings embedded directly in test classes.
- Do not keep non-trivial multi-line textual fixtures (for example YAML, JSON, XML, Java source, or long expected-output snippets) inline in test code.
- Do not write large fixture content as inline string literals and then persist it to temp files during test setup.
  - Store original/expected/config fixture files under `src/test/resources/test-cases/**` and copy/read them in tests.
- For formatter-focused fixtures under `src/test/resources/test-cases/**`, keep `input/` resources valid for the parser/compiler but intentionally not already formatted like `expected/`.
  - The scenario should require a real formatter rewrite instead of passing because `input/` already matches `expected/`.
  - Store them under `src/test/resources/test-cases/**` and load them via shared helpers such as `TestCaseResourceUtils`.
  - Only tiny one-off snippets that stay obviously readable inline are acceptable.

#### valid/ vs invalid/

- Use folder naming to communicate intent:
  - `valid/` — must compile and be compilable by the build gate.
  - `invalid/` — may intentionally not compile (only for negative tests).

#### Fixtures must compile (build-time gate)

- All `valid/**/*.java` fixtures must compile as part of the build.
- Prefer fixtures that are self-contained and depend only on the JDK.
- Prefer single-file fixtures. If multiple files are required, keep them in the same scenario folder.
- When a fixture verifies ordering inside one logical group, prefer including multiple declarations of the same kind and cover secondary ordering rules (for example visibility and alphabetical order) where the language allows it.

#### Reading resources

- Prefer classpath-based access (`ClassLoader.getResourceAsStream`) over filesystem paths.
- Use shared helpers (e.g., `TestCaseResourceUtils`) to read resources.
- If a regression test verifies the built-in default configuration, load the real embedded `default-config.yml` through the production default-loading path instead of duplicating it in test fixtures or inline YAML.

Recommended API shape:

- Keep resource identifiers as typed values where feasible (`URL`), not raw strings.
- Keep resource paths absolute (start with `/`).
- If you need to resolve a file under a directory, resolve via a dedicated helper, not via deprecated URL constructors.

### Shared test setup and one-time initialization (avoid repeated work)

If multiple tests in the same test class use the same expensive or repetitive setup (e.g., parsing, compilation, model construction, large object graphs, heavy calculations, or any other non-trivial preparation), initialize it **once** at the test-class level instead of re-creating it in every test.

Preferred options (in order):

- Use `private static final` constants for immutable, shareable objects created once.
- Use `private final` fields when per-instance initialization is sufficient and the object is safe to share across tests in the class.
- Use `@BeforeAll` to perform one-time initialization that cannot be expressed as a simple field initializer.
    - If `@BeforeAll` must be non-static, use `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`.

Important rules:

- Do **not** share mutable objects across tests if the code under test may modify them.
    - In that case, keep a single immutable “base” representation and create a fresh copy per test, or initialize the mutable object in `@BeforeEach`.
- Avoid duplicating the same setup snippet across multiple tests in the same class.
    - If repeated setup appears, refactor it into a shared field initializer or a dedicated setup method.
- Keep the `// Given` section focused on *test-specific* inputs; common setup belongs to fields / `@BeforeAll` / `@BeforeEach`.

### Constants grouping (readability)

Keep a small amount of shared test state as regular fields at the top of the class when that remains easy to scan.

- Good candidates to keep at the top:
  - `@TempDir` fields
  - one or two obvious shared constants that help the first test read naturally

If a test class contains many shared constants (URLs, resource paths, source fragments, pre-parsed models, etc.) and they start cluttering the top of the file, group them into a nested class instead of stacking a long constant block before the tests:

```java
private static final class Constants {
    private static final URL FIXTURE_URL = ...;
}
```

Rules:

- Prefer a nested `Constants` class once the constant list is long enough that it pushes test methods noticeably down the file or makes the start of the class hard to scan.
- Keep the `Constants` nested class at the end of the test class to keep the beginning focused on test methods.
- Do not move everything into `Constants` mechanically; keep only the cluttering shared constants there, while ordinary test fields such as `@TempDir` stay near the top.

### Assertions and test utilities

#### Do not test test utilities

- Do not add dedicated unit tests whose only purpose is to test test-only utility classes or helper methods.
- Validate test utilities indirectly through the real unit/integration tests that use them.
- If a test utility becomes complex enough to deserve direct behavioral tests, that is a signal to move the logic into production code or to simplify the helper.

#### Placement of test utilities

- Place **private static** helper methods and **test-only utility** code at the **end of the test class**, after all test methods (and after nested `Constants`, if present).
- The top of the test class should stay focused on test scenarios, not helper implementation details.


#### Assertions

- Use assertions only for validating the **test contract** and expected results.
- Prefer AssertJ (`assertThat(...)`).

#### Test utilities must throw exceptions (not assert)

Test utilities exist to remove duplication and improve readability. They are not a place for test verdicts.

Rules:

- Test utility methods must not call `fail(...)` or use assertions for control flow.
- If a test utility cannot proceed (missing resource, ambiguous match, invalid input), it must throw a descriptive runtime exception:
  - `IllegalArgumentException` for invalid inputs.
  - `IllegalStateException` for unexpected setup/state (e.g., “expected exactly one match, got 0/2”).
  - `UncheckedIOException` for I/O problems.

This keeps the failure type meaningful:

- Contract mismatch → assertion in the test (`assertThat(...)`).
- Broken setup/fixture/helper precondition → exception from helper.

### Internal test utility classes and nullability

- For internal test-only utility classes, prefer Lombok for null checks.
  - Use `@NonNull` on non-private parameters instead of `Objects.requireNonNull(...)`.
- Use `@UtilityClass` for pure utility classes.
- For test DTO/model/state-holder classes that mainly carry data, prefer immutable Lombok shapes (for example `@Value`) unless mutation is required.
- Private helper method parameters must not use Lombok `@NonNull`; it adds runtime checks that are unnecessary for private helpers.
  - Use `@Nullable` on private parameters only when that helper intentionally accepts `null`; otherwise keep private parameters unannotated.
- If a private helper returns a reference type, annotate the return contract explicitly with `@NonNull` or `@Nullable`.

### Optional in tests: assert vs require helpers

When writing helpers that locate elements in parsed/compiled test models, choose helper semantics intentionally:

- If a helper returns `Optional<T>`, the optional itself is part of test logic.
  - Prefer `findXxx(...)` naming and return `Optional<T>`.
- If missing/ambiguous data means the fixture/setup is broken (not a product behavior), prefer a strict helper that either returns a value or throws.
  - Prefer `requireXxx(...)` naming and return `T`.
  - Throw `IllegalStateException` with a clear message when no match (or ambiguous match) is found.

Decision rule:

- If presence/absence is the **product expectation being tested**, keep `Optional` and assert in `// Then`.
- If absence indicates **bad fixture/setup**, use `requireXxx(...)` so the failure is immediate and explicit.

### Temporary files and formatting

- Tests must not write into `src/test/resources`.
- Use `@TempDir` (JUnit 5) or write into `target/`.
- Avoid inserting empty lines between closely related constant declarations.
  - Use a blank line only to separate semantic groups.
- Keep `// Given` immediately after the opening brace.
- In test utility classes, group fields/constants by meaning; do not insert a blank line after every field “just because”.

### Code style in tests

- Prefer fully descriptive variable names (avoid `i`, `tmp`, `m`, etc.).
- Never shorten or abbreviate a variable name when a more descriptive name exists; if the type is `FileProcessingResult`, the variable must be `fileProcessingResult`, not `result`.
- Prefer Stream API when it makes the flow clearer (filter → map → collect).
- Keep helpers small and single-purpose.
