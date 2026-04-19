<!--

    SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
    SPDX-License-Identifier: Apache-2.0

-->
# Changelog

All notable changes to GlobPathFinder are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-04-19

First official General Availability release. The public API is now stable.

### Added

- `GlobPathFinder.findPaths(PathQuery)` — single entry point for recursive file system traversal
  returning a lazy `Stream<Path>`.
- `PathQuery` builder — fully configurable query object supporting:
    - `baseDir` — starting directory for traversal (defaults to current working directory).
    - `includeGlobs` / `excludeGlobs` — Ant/Maven-style glob pattern sets for fine-grained filtering.
    - `allowedExtensions` — case-insensitive file extension filter.
    - `onlyFiles` — toggle to include or exclude directory entries (default: `true`).
    - `maxDepth` — depth limit for recursive traversal.
    - `followLinks` — symlink-following control (default: `false`).
    - `failFastOnError` — when `false`, I/O errors are logged as `WARN` and traversal continues
      (shielded mode); when `true`, the first error terminates the stream.
- `AntStylePathMatcher` — Ant/Maven-style glob matching engine:
    - `**` matches zero or more path segments.
    - `*` matches any characters within a single segment.
    - `?` matches a single character.
- Dynamic pipeline construction — only the filter stages required by the active query options are
  activated, keeping traversal overhead minimal for simple queries.
- SLF4J-based structured logging:
    - `TRACE` level for per-path discovery and per-filter events.
    - `DEBUG` level for query start and final emitted paths.
- `Automatic-Module-Name: io.github.lemon_ant.globpathfinder` for safe JPMS consumption.

### Compatibility

| JDK vendor  | JDK 11 | JDK 17 | JDK 21 | JDK 23 |
|-------------|--------|--------|--------|--------|
| Temurin     | ✅     | ✅     | ✅     | ✅     |
| Microsoft   | ✅     | ✅     | ✅     | —      |

The compiled bytecode targets Java 11 (`--release 11`).

[1.0.0]: https://github.com/lemon-ant/glob-path-finder/releases/tag/v1.0.0
