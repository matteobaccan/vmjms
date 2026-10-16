# Change: A common header on every source file, enforced by the build

## Why

The sources carry no provenance. Open any of the 27 Java files and the first
line is `package it.baccan.vmjms;`: nothing says who wrote the code, under which
licence it may be used, or where it lives. The information exists, but only in
places a reader of the code does not have in front of them - the `LICENSE` file
at the repository root, and the `<developers>`, `<url>` and `<scm>` entries of
the POM.

That gap is not theoretical, because the sources travel. Since 1.0.0 every
release publishes `vmjms-<version>-sources.jar` alongside the main jar, and an
IDE unpacks it silently the first time someone steps into `InMemoryBroker`. What
they read is an anonymous file. The same happens to a file pasted into a ticket,
copied into another project, or quoted in an answer: it loses every link back to
the project the moment it leaves the repository, and MIT - which requires the
copyright notice to travel with the code - is left relying on a file nobody
copied along.

A header fixes it only if it is actually everywhere, and a rule that lives in a
contributor's memory is not everywhere: the twenty-fourth file added in a hurry
is the one without it. So the build has to be the one that checks.

## What Changes

- Every Java source file in the repository, main and test alike, starts with the
  same header: the product and the address of its repository, the copyright of
  Matteo Baccan, and the licence as an SPDX identifier.
- The header is applied to the 23 files that have none, and to the four
  `package-info.java` files it is added above their package javadoc, where it
  cannot be mistaken for documentation.
- The build checks the header during `verify` and fails when a file is missing
  it or carries a changed one, so the rule is enforced locally and in CI, before
  a pull request is opened rather than after.
- The same tooling can write the header into new files, so obeying the rule
  costs one command rather than a copy and paste.
- The `project-distribution` capability gains the requirement, next to the ones
  that already state the licence and the published packaging.

## Impact

- Affected specs: `project-distribution`
- Affected code: all `src/main/java` and `src/test/java` sources, `pom.xml`, and
  one header template file
- No behaviour of the provider changes: not one line of the compiled output
  moves, and the published jar keeps the same classes. What changes is what a
  reader of the sources is told, and what the build refuses.

## Non-Goals

- The full MIT text in every file. It stays in `LICENSE`, where MIT requires it,
  and the SPDX identifier points at it.
- A header on anything other than Java sources: Markdown, XML, YAML, the logo
  and the POM keep what they have.
- A copyright year that follows the clock. The year is fixed, for the reason
  given in the design.
- Checking the authorship of individual contributions. Git already records who
  wrote what; the header states who owns the project.
