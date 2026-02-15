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
- Parallel traversal for multiple bases.
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

```java
PathQuery query = PathQuery.builder()
    .includeGlobs(Set.of("src/**"))
    .excludeGlobs(Set.of("**/test/**"))
    .allowedExtensions(Set.of("java"))
    .onlyFiles(true)        // default is true
    .followLinks(false)     // disable symlink following
    .failFastOnError(false) // shielded mode: errors are logged as WARN, traversal continues
    .build();

try (Stream<Path> paths = GlobPathFinder.findPaths(query)) {
    List<Path> javaFiles = paths.toList();
}
```

---

## ⚡ Performance and Logging

GlobPathFinder is built with efficiency in mind:

- Dynamic pipeline construction ensures that only the necessary filters and matchers are applied.
- No redundant operations — traversal and filtering remain lightweight even for large trees.
- Professional logging with SLF4J integration:
    - `trace` for deep traversal inspection
    - `debug` for emitted paths and filtering steps

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

## Wiki

Additional documentation is available in the [project Wiki](../../wiki).
If you run into questions about how glob patterns behave in Java NIO, the Wiki already contains a dedicated article with examples and explanations.

---

## 🤝 Contributing

Contributions are always welcome!
See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

- Fork the repo
- Create a feature branch
- Add tests for your changes
- Submit a PR 🚀

---

## 🛡️ Security

If you discover any security issue, please see [SECURITY.md](SECURITY.md).

---

## 📅 Roadmap

- [ ] v1.1 — Performance track: reproducible benchmarks + targeted optimizations
- [ ] v2.0 — Async API with reactive streams (non-blocking/backpressure-friendly)
- [ ] Resource streaming beyond local FS (classpath/JAR resources)
- [ ] Universal source adapters (e.g., HTTP/HTTPS and pluggable providers)

Longer-form future ideas are tracked in [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md).

---

## 📜 License

Licensed under the [Apache License 2.0](LICENSE).
