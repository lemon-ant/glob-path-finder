# Future ideas and backlog

This document is a lightweight backlog for product ideas and architectural directions.
It is intentionally not a strict task list yet — items can be refined later into issues/milestones.

## How to use this file

- Capture ideas early before they are forgotten.
- Keep entries short: intent, expected value, and rough implementation direction.
- Move mature items into GitHub issues and release plans.

## Candidate directions

### 1) Resource streaming support (non-local FS)

**Goal:** extend `GlobPathFinder` so it can stream not only resources from the local filesystem,
but also resources loaded through class/resource loaders.

Potential targets:

- Classpath resources from compiled artifacts.
- Resources inside JAR files.
- Other packaged resource containers where listing/streaming is possible.

Expected result:

- A unified API feeling similar to current filesystem traversal.
- Reuse as much of existing filtering logic as possible (glob includes/excludes, extension filters, etc.).

### 2) More universal source adapters

**Goal:** evolve from a filesystem-first utility into a more universal path/resource discovery engine.

Potential adapters:

- HTTP/HTTPS source streaming (where applicable).
- Other pluggable "source providers" for custom storage backends.

Expected result:

- Extensible architecture: one traversal/filtering pipeline, multiple source implementations.
- Clear separation between source enumeration and filtering/matching stages.

### 3) v1.1 performance track: benchmarks + targeted optimizations

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

### 4) v2.0 async/reactive API

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

## Notes for future decomposition

When converting these ideas into executable tasks, define:

- API surface (minimal/non-breaking first).
- Backward compatibility strategy.
- Performance expectations and benchmarks.
- Error handling model per source type.
- Testing matrix for each provider/adapter.
