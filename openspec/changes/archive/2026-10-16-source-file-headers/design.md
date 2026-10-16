# Design: A common header on every source file

## Context

Twenty-seven Java files, twenty-three of which start straight at
`package it.baccan.vmjms;`. The other four are the `package-info.java` files,
whose first line is the `/**` of their package javadoc - a comment, but not a
header, and the one place where putting a header in the wrong spot silently
destroys something.

The project already states everything the header needs, in three different
places: `LICENSE` says MIT and `Copyright (c) 2026 Matteo Baccan`, and the POM
carries `<url>`, `<developers>` and `<scm>` pointing at
`https://github.com/matteobaccan/vmjms`. None of those travel with a file. The
header is not new information; it is the same information where the reader is.

## Goals / Non-Goals

**Goals**

- One header, identical everywhere, that survives a file leaving the repository.
- A build that refuses a file without it, locally and in CI, before review.
- One command that applies it, so that the rule is cheaper to obey than to skip.

**Non-Goals**

- Legal completeness in each file: the full MIT text stays in `LICENSE`.
- Headers on anything but Java sources.
- A year that follows the clock.

## Decisions

### The header is four lines, not twenty

The header states the product and its repository, the copyright, and the licence
as an SPDX identifier:

```java
/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
```

The alternative is the full MIT text in every file, which is what many projects
do. It was rejected for a practical reason rather than a stylistic one: twenty
lines of boilerplate repeated twenty-seven times is 540 lines that no one reads
and that drift the first time one copy is edited, and a drifted licence text is
worse than a referenced one. `SPDX-License-Identifier` is the machine-readable
form that licence scanners, GitHub and package managers already understand, and
MIT is satisfied by the `LICENSE` file that MIT itself requires to travel with
the distribution - which the sources jar and the repository both carry.

The URL earns its line separately from the copyright: the copyright says who
owns the code, the URL says where the living version of it is. A file pasted
into a ticket two years from now is useful only with the second one.

### The year is fixed at 2026, deliberately

A header template that interpolates the current year has a failure mode that is
easy to miss and annoying to meet: on the first build of January the check
compares a file written last year against a template that now says this year,
and the build fails on every file at once, in the middle of whatever the
maintainer was actually doing.

The year in the header is the year of the copyright, not the year of the build,
and `LICENSE` already says 2026. Keeping it fixed also keeps the check an exact
text comparison, which is the only kind that catches a *changed* header rather
than just a missing one.

### Enforcement belongs to the build, not to a CI step

A `grep` in the workflow would be shorter to write, and it would teach the rule
at the worst possible moment: after the push, from a red check, to someone who
has already moved on. The check runs in `verify`, so the same `mvn verify` that
runs the tests locally and in CI is the one that enforces the header. CI needs
no new step at all, because the existing workflow already runs `verify` on JDK
17 and 21 - which also means the check is exercised twice, on both JDKs, for
free.

`license-maven-plugin` (`com.mycila`) is the tool: its `check` goal binds to
`verify` and names the offending files, and its `format` goal writes the header
into the files that lack it, which is the command the requirement asks for. The
alternative considered was Checkstyle with a `RegexpHeader` rule; it was
rejected because it brings a whole Checkstyle configuration into the build for
one rule, and because it can only check, not apply.

### The header goes above the package javadoc, and that is the risky case

In a `package-info.java` the order matters:

```java
/*
 * ... the header ...
 */

/**
 * The documentation of the package.
 */
package it.baccan.vmjms;
```

A block comment (`/*`) is not a javadoc comment (`/**`), so the header cannot be
mistaken for the package documentation - but only in this order. Put the header
second, or write it with two asterisks, and javadoc takes the first comment it
finds as the package documentation: the published documentation of the package
becomes a copyright notice, and the real text is dropped. This is why the
requirement states the comment style and the position, and why the verification
of this change looks at the generated javadoc rather than trusting the build to
be green - the failure here produces a wrong document, not an error.

### Tests carry the header too

Scoping the rule to `src/main/java` would be defensible: the tests are not
published. It was rejected because the tests are the part of the repository a
contributor reads first and copies from, and a template without a header
produces files without headers. One rule for the whole tree is also one rule to
remember.

## Risks / Trade-offs

- **The first application touches every file.** One commit will modify twenty-
  three sources without changing a line of behaviour, which makes
  `git log -p` noisier for a day and `git blame` one step deeper on the first
  line of each file. Mitigated by doing it in a single commit that touches
  nothing else, so it is easy to skip when reading history.
- **A build that fails over a comment.** Someone adding a file in a hurry meets
  a red build for a missing comment, which is irritating exactly when they are
  in a hurry. Accepted: that is the moment the rule is cheapest to satisfy, and
  the `format` goal makes it one command.
- **One more plugin in the build.** It runs in `verify`, so it also runs in the
  release workflow, where a failure would stop a release over a comment. That is
  the intended behaviour - a release with unattributed sources is the thing this
  change exists to prevent - but it is worth knowing before the next tag.
- **The SPDX identifier is a reference, not the licence text.** A reader who
  wants the terms needs the `LICENSE` file. Both the repository and the sources
  jar include it, and the header gives the URL where it lives.
