<!--

    SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
    SPDX-License-Identifier: Apache-2.0

-->
# Contributing to GlobPathFinder

Thank you for your interest in contributing! 🚀  
Contributions of all kinds are welcome: reporting issues, suggesting features, improving documentation, or submitting code.

---

## 🐛 Reporting issues

- Use the [issue tracker](../../issues) to report bugs or request features.
- Please describe the problem clearly and provide steps to reproduce if possible.
- Include details about your environment (OS, JDK version).

---

## 🛠️ Development setup

1. If you do not have write access, fork the repository first, then clone your fork locally. If you do have write access, clone the repository locally.
2. Make sure you have **Java 11+** installed. **Maven 3.3.9+** is required; **Maven 3.9+** is recommended.
3. Run tests to verify everything is working:
   ```bash
   mvn clean verify
   ```
4. Create a new branch for your changes:
   ```bash
   git checkout -b feature/my-new-feature
   ```

---

## ✅ Pull requests

- Keep PRs focused — one feature/fix per PR.
- Include tests for new functionality or bug fixes.
- Follow the existing code style and conventions (JUnit 5, AssertJ, Java 11).
- Ensure that `mvn clean verify` passes before submitting.

---

## 📖 Documentation

- Update `README.md` if your change adds or modifies functionality.
- Javadoc should be added/updated for public methods and classes.

---

## 🙌 Code of Conduct

By participating in this project, you agree to uphold the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## 💡 Tips

- Write clear commit messages (imperative mood).
- Small, consistent contributions are better than one huge PR.
