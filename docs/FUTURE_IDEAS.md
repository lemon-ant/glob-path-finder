<!--
SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
SPDX-License-Identifier: Apache-2.0
-->

# Future ideas and backlog

This document is a lightweight backlog for product ideas and architectural directions.
It is intentionally not a strict task list yet — items can be refined later into issues/milestones.

## How to use this file

- Capture ideas early before they are forgotten.
- Keep entries short: intent, expected value, and rough implementation direction.
- Move mature items into GitHub issues and release plans.

## Candidate directions

### 1) v1.1 performance track: benchmarks + targeted optimizations

**What this means:** not "optimize blindly", but introduce measurable performance baselines and
then improve hot spots that are proven by profiling and benchmark runs.

Focus areas:

- Reproducible benchmark scenarios (small/medium/large trees, deep nesting, many globs).
- Cost analysis for include/exclude matching and path normalization.
- Memory pressure checks for large result sets.
- Parallel traversal tuning and heuristics for thread usage.

Expected result:

- Performance regression visibility between releases.
- Practical optimization roadmap with measurable gain (throughput/latency/memory).

### 2) v2.0 async/reactive API

**What this means:** introduce a non-blocking style API for consuming traversal results as a stream
with backpressure-friendly behavior, while keeping the current synchronous API available.

Possible directions:

- Reactive Streams-compatible publisher (e.g., `Flow.Publisher<Path>`).
- Async callback/subscriber-based consumption for incremental processing.
- Configurable scheduler/executor model for traversal and emission.

Expected result:

- Better integration with reactive applications and pipelines.
- Ability to process results as they appear, without waiting for full traversal completion.
- Clear migration story: sync API remains stable, async API is additive.

### 3) Resource streaming support (non-local FS)

**Goal:** extend `GlobPathFinder` so it can stream not only resources from the local filesystem,
but also resources loaded through class/resource loaders.

Potential targets:

- Classpath resources from compiled artifacts.
- Resources inside JAR files.
- Other packaged resource containers where listing/streaming is possible.

Expected result:

- A unified API feeling similar to current filesystem traversal.
- Reuse as much of existing filtering logic as possible (glob includes/excludes, extension filters, etc.).

### 4) More universal source adapters

**Goal:** evolve from a filesystem-first utility into a more universal path/resource discovery engine.

Potential adapters:

- HTTP/HTTPS source streaming (where applicable).
- Other pluggable "source providers" for custom storage backends.

Expected result:

- Extensible architecture: one traversal/filtering pipeline, multiple source implementations.
- Clear separation between source enumeration and filtering/matching stages.

### 5) Regex-based include/exclude filtering

**Goal:** complement the existing glob pattern matching with full regular expression support so
callers can express more precise inclusion, exclusion, and file-extension filtering rules.

Scope:

- Allow `include` and `exclude` filter entries to be specified as compiled `java.util.regex.Pattern`
  (or as raw regex strings with an explicit mode flag) alongside the existing glob syntax.
- Provide dedicated file-extension filters driven by regex so callers can match groups of
  extensions (for example the regex pattern `\.(java|kt|groovy)$`, or the Java string literal
  `"\\.(java|kt|groovy)$"` when represented in source code) without enumerating each extension
  separately.
- Keep backward compatibility: glob-only users see no API change; regex is strictly additive.

Expected result:

- Richer, more expressive filtering for advanced use cases that glob patterns cannot cover.
- A clear, documented precedence rule when both glob and regex filters are present.
- Full test coverage including negative (exclude) regex patterns and extension-group filters.

## Notes for future decomposition

When converting these ideas into executable tasks, define:

- API surface (minimal/non-breaking first).
- Backward compatibility strategy.
- Performance expectations and benchmarks.
- Error handling model per source type.
- Testing matrix for each provider/adapter.
