<!--

    SPDX-FileCopyrightText: 2026 Anton Lem <antonlem78@gmail.com>
    SPDX-License-Identifier: Apache-2.0

-->
# Release Preparation Checklist

This document lists every step required to cut a new release of **Glob Path Finder**.
Follow the steps in order. Do not skip any item — each one was added because it was
either forgotten in a previous release or is easy to overlook.

---

## 1. Decide the next version number

Follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html):

| Change type | Rule |
|---|---|
| Bug fix only | Increment **patch**: `1.1.0 → 1.1.1` |
| Backward-compatible new feature | Increment **minor**: `1.1.0 → 1.2.0` |
| Breaking API change | Increment **major**: `1.1.0 → 2.0.0` |

Throughout this document `X.Y.Z` refers to the chosen new version (without the `v` prefix)
and `vX.Y.Z` refers to the Git tag (with the prefix).

---

## 2. Update `pom.xml`

Two places in `pom.xml` **must** be updated.

### 2a. Project version

```xml
<version>X.Y.Z</version>
```

Change the `<version>` element at the top of the file from the previous value to `X.Y.Z`.

### 2b. SCM tag ⚠️ (frequently forgotten)

```xml
<scm>
    ...
    <tag>vX.Y.Z</tag>   <!-- must be the concrete Git tag, NOT "HEAD" -->
    ...
</scm>
```

The `<tag>` element inside `<scm>` **must** be set to the concrete tag name (e.g. `v1.2.0`).
It defaults to `HEAD` in development, which is wrong for a published release.
Maven Central and tooling use this field to link the artifact back to its exact source commit.

---

## 3. Update `CHANGELOG.md`

Add a new top-level section **above** the previous release, following the existing format:

```markdown
## [X.Y.Z] — YYYY-MM-DD

### Added
- …

### Changed
- …

### Fixed
- …
```

Also add the comparison link at the bottom of the file:

```markdown
[X.Y.Z]: https://github.com/lemon-ant/glob-path-finder/compare/vPREV...vX.Y.Z
```

Use today's date as the release date. Do not leave it as a placeholder.

---

## 4. Update version references in `README.md`

`README.md` contains installation snippets that pin a concrete version.
Search for the previous version string and replace every occurrence with `X.Y.Z`:

- Maven dependency snippet (`<version>…</version>`)
- Gradle dependency snippet (if present)

---

## 5. Run the full build locally

```bash
mvn -B -ntp verify
```

All tests, SpotBugs checks, license header checks, and Spotless formatting checks must pass.
Fix any failures before proceeding.

---

## 6. Commit all changes

Stage and commit **only** the release-related changes in a single commit:

```bash
git add pom.xml CHANGELOG.md README.md
git commit -m "chore: prepare release vX.Y.Z"
```

Push the commit to `main` (or open a PR if branch protection requires a review).

---

## 7. Create and push the Git tag

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

The tag name **must** start with `v` (e.g. `v1.2.0`). The publish workflow checks for this
prefix (`refs/tags/v*`) and will be skipped if the tag does not match.

---

## 8. Create the GitHub Release

1. Go to **Releases → Draft a new release** on GitHub.
2. Select the tag `vX.Y.Z` you just pushed.
3. Set the release title to `vX.Y.Z — <short description>`.
4. Paste the `CHANGELOG.md` section for this version as the release body.
5. Ensure **Set as the latest release** is checked (unless this is a pre-release).
6. Click **Publish release**.

Publishing the release triggers the **04-Publish Release** workflow, which builds, signs,
and uploads the artifact to Maven Central.

---

## 9. Verify CI workflows

After publishing the release, confirm that all three workflows succeed:

| Workflow | Expected result |
|---|---|
| `01-Build Artifacts` | ✅ Green |
| `02-Compat Test` | ✅ Green |
| `04-Publish Release` | ✅ Green — artifact uploaded to Maven Central |

If any workflow fails, investigate the logs before announcing the release.

---

## 10. Verify Maven Central publication

Wait up to 30 minutes, then confirm the artifact is searchable:

```
https://search.maven.org/artifact/io.github.lemon-ant/glob-path-finder/X.Y.Z/jar
```

---

## 11. Post-release: reset `pom.xml` SCM tag to `HEAD`

After the release is confirmed, set `<tag>` back to `HEAD` in `pom.xml` for the
next development cycle:

```xml
<scm>
    ...
    <tag>HEAD</tag>
    ...
</scm>
```

Commit this as a follow-up:

```bash
git add pom.xml
git commit -m "chore: back to development after vX.Y.Z"
git push origin main
```

---

## Quick reference — files to touch per release

| File | What changes |
|---|---|
| `pom.xml` | `<version>` → `X.Y.Z`; `<scm><tag>` → `vX.Y.Z` |
| `CHANGELOG.md` | New `## [X.Y.Z] — YYYY-MM-DD` section + bottom link |
| `README.md` | Version in Maven and Gradle snippets → `X.Y.Z` |
| Git | New annotated tag `vX.Y.Z` pushed to `origin` |
| GitHub | Release published from tag `vX.Y.Z` |
