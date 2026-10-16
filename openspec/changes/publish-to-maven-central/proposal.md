# Change: Publish the release to Maven Central

## Why

Release 1.0.0 exists, is complete and is resolvable by almost nobody. It sits in
GitHub Packages, which requires every consumer to authenticate even for a public
artifact: a developer who wants to try the provider has to create a personal
access token with the `read:packages` scope, add a `<server>` entry to their
`~/.m2/settings.xml` and declare a custom `<repositories>` block, before a
single line of their code can import `jakarta.jms`. For a library whose whole
pitch is "swap one `ConnectionFactory` and your tests stop needing Docker",
three steps of credential plumbing is a higher price than the problem it solves.

The invisibility goes further than the download. `it.baccan:vmjms` does not
appear on Maven Central, so it does not appear anywhere that reads Maven
Central: not in search, not in an IDE's dependency completion, not to Renovate
or Dependabot scanning a consumer's tree for updates, not in the places a
developer looks when they go hunting for an in-memory JMS provider. A library
nobody can find and nobody can resolve without a token is, in practice, not
published.

Maven Central is also the only target that makes the release workflow mean what
it says. The publication of 1.0.0 ended with `Skipping already-deployed
it.baccan:vmjms:1.0.0` after a `422` on the repository metadata, and the build
stayed green: GitHub Packages answers ambiguously enough that the deploy plugin
cannot tell an upload it already did from one it never did. A release that can
silently publish nothing is not a release process.

## What Changes

- A release is published to Maven Central through the Central Portal, using the
  `central-publishing-maven-plugin` in the existing `release` profile. The
  plugin uploads a validated bundle and the build fails when validation fails,
  so a green release means a resolvable artifact.
- Every published file is signed with GPG. The `maven-gpg-plugin` is already in
  the `release` profile; from now on its signatures are a requirement of the
  target rather than decoration, and the release workflow imports the signing
  key from repository secrets.
- **BREAKING for consumers of the GitHub Packages repository:** GitHub Packages
  stops being a publication target and `<distributionManagement>` leaves the
  POM. Central becomes the single place a release lands. Version 1.0.0 stays
  where it is, as a historical artifact, and the README says so.
- The coordinates do not change. The artifact stays `it.baccan:vmjms`, which
  requires the `it.baccan` namespace to be verified on the Central Portal by a
  DNS TXT record on `baccan.it`. That verification is a manual prerequisite of
  the maintainer and the tasks state it as such.
- The README's install instructions become a dependency block and nothing else:
  no repository declaration, no token, no `settings.xml`.
- The first release published this way is 1.1.0. Central is immutable, so the
  first run is deliberately staged for inspection before automatic publication
  is switched on.

## Capabilities

### New Capabilities

None. Publication is already part of `project-distribution`; what changes is
where a release goes and what it must satisfy to get there.

### Modified Capabilities

- `project-distribution`: the *Maven-Consumable Release Packaging* requirement
  currently accepts publication "to a Maven repository using the repository's
  own credentials", which is satisfied by GitHub Packages and therefore by an
  artifact no consumer can resolve unauthenticated. It is replaced by a
  requirement that names Maven Central, requires a signature on every published
  file, requires the build to fail when the publication does not actually
  happen, and requires that resolving a release needs no credentials and no
  repository declaration.

## Impact

- Affected specs: `project-distribution`
- Affected code: `pom.xml` (the `release` profile gains the publishing plugin,
  `<distributionManagement>` is removed), `.github/workflows/release.yml` (the
  server id, the GPG key, the profile on the deploy command),
  `README.md` (the *Install* section)
- Affected configuration: four new repository secrets, for the Central user
  token and for the GPG key and its passphrase
- No behaviour of the provider changes. Not a line of `src/` moves: this change
  is about who can obtain the jar, not what the jar does.
- Prerequisites outside the repository: the `it.baccan` namespace verified on
  the Central Portal, and a GPG key whose public half is on a public keyserver.
  Neither can be done by the build, and until both are done the release
  workflow has nothing to publish to.

## Non-Goals

- Switching the coordinates to `io.github.matteobaccan`. It would be verified
  automatically by the GitHub login and would save the DNS record, at the cost
  of a groupId that no longer matches the `it.baccan.vmjms` package for the rest
  of the project's life. The design records the comparison.
- Keeping GitHub Packages in parallel. Two targets mean two coordinates to
  explain, two places to check after a release, and a second upload whose
  failure modes we have already seen. One target.
- Backfilling 1.0.0 to Central. Central is immutable and 1.0.0 is already
  released; the first Central version is 1.1.0.
- Publishing `-SNAPSHOT` builds to the Central snapshot repository. It is
  available and it can be enabled later; nothing in the project needs it today.
- Automating the namespace verification or the key generation. Both are one-off
  manual acts by the maintainer, and both are recorded in the tasks so that the
  next person knows they exist.
