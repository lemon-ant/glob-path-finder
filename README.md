# GlobPathFinder

[![Build](https://github.com/lemon-ant/glob-path-finder/actions/workflows/verify.yml/badge.svg)](https://github.com/lemon-ant/glob-path-finder/actions/workflows/verify.yml)
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
    .onlyFiles(true) // You can omit it, it's true by default
    .followLinks(false)
    .build();

try (Stream<Path> paths = GlobPathFinder.findPaths(query)) {
    List<Path> javaFiles = paths.toList();
}
```

---

## 🌟 Support

I’m an open-source developer who enjoys building useful and reliable tools for the community 🛠️.

Every bit of support is a strong encouragement 🌱. It shows me that these projects matter and gives me the motivation
to keep improving, writing better documentation, and adding new features.

Even a small contribution (like a cup of coffee ☕) lifts my spirits and keeps the projects moving forward 🙌.

If you like this project, please consider:

- ⭐ starring the repo — it helps visibility.
- ☕ [Buying me a coffee](https://buymeacoffee.com/antonlem) — even $5 keeps me coding with extra caffeine.
- 💖 [GitHub Sponsors](https://github.com/sponsors/antonlem).

---

## 📖 Documentation

- [Javadoc (latest)](https://javadoc.io/doc/io.github.lemon-ant/glob-path-finder)

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

- [ ] v1.1 — Performance tests and possible improvements
- [ ] v2.0 — Async API with reactive streams

---

## 📜 License

Licensed under the [Apache License 2.0](LICENSE).
