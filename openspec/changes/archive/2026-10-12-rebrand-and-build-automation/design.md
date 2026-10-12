## Context

`vmjms` is a single-module Maven library with no runtime dependency beyond the
Jakarta JMS API. It has no server component, so "distribution" means exactly
one thing: a jar that downstream projects can resolve with Maven.

## Decisions

### Coordinates and packages

`groupId` becomes `it.baccan`, `artifactId` stays `vmjms`, and the base package
becomes `it.baccan.vmjms`, mirroring the groupId. Directories are moved with
`git mv` so history follows the files, and references are rewritten everywhere,
including archived OpenSpec documents, so that the namespace is uniform across
the repository.

### Unique default Broker ID

Alternatives considered:

1. `UUID.randomUUID()` — unique, but discards the readable hex-timestamp prefix
   that the message-ID requirement builds on.
2. Timestamp + static `AtomicLong` counter — keeps the readable prefix, is
   collision-free within the JVM, and the registry is itself per-JVM, so
   JVM-local uniqueness is exactly the scope that matters.

Decision: option 2. The counter is only applied to the generated (anonymous) ID;
an explicitly supplied Broker ID is still used verbatim, which is what sharing
depends on.

### Release pipeline

Two workflows, kept separate so that a push never publishes anything:

- `build.yml` — on push and pull request, `mvn verify` on a JDK matrix
  (17 as the baseline the POM targets, 21 as the current LTS), with the Maven
  repository cached.
- `release.yml` — on `v*` tags (and manual dispatch), builds main, sources and
  javadoc jars and then publishes them.

Publication target: **GitHub Packages**. It authenticates with the built-in
`GITHUB_TOKEN`, so releases work with no externally provisioned secret, and it
is a normal Maven repository for consumers. Maven Central would additionally
require a registered namespace, a published GPG key and portal credentials; the
POM is prepared for it (license, SCM, developers, source/javadoc jars, a
`release` profile for signing) but the workflow does not depend on it.

The jars are also attached to the GitHub Release so the artifacts remain
downloadable without any repository configuration.

### Brand assets

Assets live in `docs/` and are authored as SVG. The logo is a memory module
(pins top and bottom) enclosing three FIFO message bars draining into an arrow:
in-memory, queue, point-to-point. The social preview must be raster for GitHub,
so the 1280x640 SVG is rendered to PNG once and committed alongside its source.

## Risks / Trade-offs

- The coordinates are fixed before the first release: the version is still
  `1.0.0-SNAPSHOT`, so no published artifact is affected by settling them.
- GitHub Packages requires consumers to authenticate even for public packages.
  Mitigated by attaching the jars to the Release and by keeping the POM ready
  for a later Maven Central publication.
