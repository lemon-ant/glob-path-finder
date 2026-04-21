<!--

    SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
    SPDX-License-Identifier: Apache-2.0

-->
# GlobPathFinder

[![Compatibility](https://github.com/lemon-ant/glob-path-finder/actions/workflows/publish-02-compat-test.yml/badge.svg)](https://github.com/lemon-ant/glob-path-finder/actions/workflows/publish-02-compat-test.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.lemon-ant/glob-path-finder.svg)](https://search.maven.org/artifact/io.github.lemon-ant/glob-path-finder)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Coverage](https://img.shields.io/codecov/c/github/lemon-ant/glob-path-finder)](https://codecov.io/gh/lemon-ant/glob-path-finder)

> A lightweight Java library for recursive file system traversal with flexible glob pattern filtering.

---

## ✨ Features

- Recursive search starting from any base directory.
- Include and exclude glob patterns.
- Extension filters (case-insensitive).
- Depth limit and symlink following control.
- Option to select only files or include directories.
- Stream-friendly results — the returned `Stream<Path>` supports parallel processing via `.parallel()` for efficient concurrent file handling.
- Unique normalized absolute paths as the result.
- **Convenient and flexible interface** — results are fully configurable via `PathQuery`.
- **Optimized performance** — dynamic pipeline construction ensures no unnecessary operations are executed.
- **Professional logging and trace/debug support** — designed for developers to analyze and troubleshoot traversal.

---

## 📦 Installation

Add to your **Maven** `pom.xml`:

```xml
<dependency>
  <groupId>io.github.lemon-ant</groupId>
  <artifactId>glob-path-finder</artifactId>
  <version>1.0.0</version>
</dependency>
```

Or with **Gradle**:

```groovy
implementation 'io.github.lemon-ant:glob-path-finder:1.0.0'
```

---

## 🚀 Quick Start

### Find all files under the current directory

```java
PathQuery query = PathQuery.builder().build();

try (Stream<Path> paths = GlobPathFinder.findPaths(query)) {
    paths.forEach(System.out::println);
}
```

### Find only Java files under `src` directory relative to the current directory, excluding tests

Use singular methods for a single pattern, or collection-based methods for multiple patterns at once:

```java
// Singular — one pattern per call
PathQuery query = PathQuery.builder()
    .includeGlob("src/**")
    .excludeGlob("**/test/**")
    .allowedExtension("java")
    .onlyFiles(true)        // default is true
    .followLinks(false)     // disable symlink following
    .failFastOnError(false) // shielded mode: errors are logged as WARN, traversal continues
    .build();

// Or collection-based — pass several patterns at once
PathQuery query = PathQuery.builder()
    .includeGlobs(Set.of("src/**", "docs/**"))
    .excludeGlobs(Set.of("**/test/**", "**/generated/**"))
    .allowedExtensions(Set.of("java", "kt"))
    .build();

try (Stream<Path> paths = GlobPathFinder.findPaths(query)) {
    List<Path> javaFiles = paths.toList();
}
```

> **Glob pattern semantics:** include and exclude patterns use **Ant/Maven-style** matching, not
> the JDK `glob:` syntax. Key differences:
> - `**` matches **zero or more** path segments (JDK `glob:` requires at least one).
> - Character classes (`[abc]`) and alternation groups (`{foo,bar}`) are **not supported**.

---

## ⚡ Performance and Logging

GlobPathFinder is built with efficiency in mind:

- Dynamic pipeline construction ensures that only the necessary filters and matchers are applied.
- No redundant operations — traversal and filtering remain lightweight even for large trees.
- Professional logging with SLF4J integration:
    - `trace` for raw path discoveries and per-filter pass logging
    - `debug` for the initial query start and final emitted paths

This makes the library not only fast, but also developer-friendly for debugging and analysis.

---

## ⭐ Ways to support this project

I’m an open-source developer who enjoys building useful and reliable tools for the community 🛠️.

Every bit of support is a strong encouragement 🌱. It shows me that these projects matter and gives me the motivation
to keep improving, writing better documentation, and adding new features.

Even a small contribution (like a cup of coffee ☕) lifts my spirits and keeps the projects moving forward 🙌.

If you like this project, please consider:

- ⭐ Star the repository — it helps visibility.
- ☕ [Buying me a coffee](https://buymeacoffee.com/antonlem) — even $5 keeps me coding with extra caffeine.
- 💖 [GitHub Sponsors](https://github.com/sponsors/AntonLem) — recurring sponsorship directly through GitHub to support ongoing development.

---

## 📖 Documentation

- [Javadoc (latest)](https://javadoc.io/doc/io.github.lemon-ant/glob-path-finder)

---

## 🤝 Contributing

Contributions are always welcome!
See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Fork the repository if you do not have write access; otherwise create a feature branch in this repository
- Add tests for your changes
- Submit a pull request 🚀

---

## 🛡️ Security

If you discover any security issue, please see [SECURITY.md](SECURITY.md).

---

## 📅 Roadmap

- [ ] Performance track: reproducible benchmarks + targeted optimizations
- [ ] Async API with reactive streams (non-blocking/backpressure-friendly)
- [ ] Resource streaming beyond local FS (classpath/JAR resources)
- [ ] Universal source adapters (e.g., HTTP/HTTPS and pluggable providers)

Longer-form future ideas are tracked in [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md).

---

## 🔬 JVM Compatibility

The CI pipeline verifies each release against the following JVM distributions and versions:

| JVM distribution              | 11 | 17 | 21 | 23 |
|-------------------------------|:--:|:--:|:--:|:--:|
| Eclipse Temurin               | ✅ | ✅ | ✅ | ✅ |
| Microsoft Build of OpenJDK    | ✅ | ✅ | ✅ | —  |

---

## 📜 License

Licensed under the [Apache License 2.0](LICENSE).
