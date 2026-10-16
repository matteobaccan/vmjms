## Context

The project already produces everything Maven Central requires and sends it to
the wrong place. `mvn -Prelease verify` builds the main jar, a sources jar and a
javadoc jar, the POM carries `name`, `description`, `url`, `licenses`,
`developers` and `scm`, and the `release` profile already runs
`maven-gpg-plugin` 3.2.8. What is missing is the publisher and the account that
backs it.

The current target is GitHub Packages, declared in `<distributionManagement>`
and fed by the built-in `GITHUB_TOKEN`. It worked exactly once, for 1.0.0, and
the log of that run shows why it cannot be trusted twice: a `422` on the
repository metadata, followed by `Skipping already-deployed
it.baccan:vmjms:1.0.0` and a green build. The artifacts are there, so the upload
did happen; but the existence check the deploy plugin performs against GitHub
Packages is unreliable enough that a rerun publishes nothing and still reports
success.

Maven Central is reached through the Central Portal (`central.sonatype.com`).
OSSRH and `oss.sonatype.org` are retired, so `nexus-staging-maven-plugin` and
the staging workflow built around it are not an option: the current path is a
signed bundle uploaded to the Portal, which validates it and then either waits
for a human or publishes it.

Two prerequisites live outside the repository and cannot be automated: a
verified namespace, and a GPG key whose public half is on a public keyserver.

## Goals / Non-Goals

**Goals:**

- A released version is resolvable from Maven Central with nothing but a
  `<dependency>` block: no credentials, no repository declaration.
- A release workflow that fails when the publication fails, rather than
  reporting success over a silent no-op.
- One publication target, so that "where is the release" has one answer.
- The manual prerequisites written down, in the order that avoids the traps.

**Non-Goals:**

- Changing the coordinates, the package names or anything in `src/`.
- Keeping GitHub Packages alive in parallel, or moving 1.0.0 to Central.
- Publishing snapshots.
- Automating the Portal account, the namespace verification or the key
  generation.

## Decisions

### Publish through `central-publishing-maven-plugin`, in the `release` profile

Version 0.12.0, with `<extensions>true</extensions>` and
`<publishingServerId>central</publishingServerId>`. The plugin stages the
artifacts, generates the checksums Central requires, zips a bundle, uploads it
and blocks until the Portal has validated it.

*Alternatives considered.* Calling the Publisher API by hand with `curl` after
assembling the bundle ourselves: fewer moving parts in the POM, but we would own
the checksum generation, the bundle layout and the polling, all of which the
plugin already does and the Portal documents against the plugin. JReleaser:
capable and pleasant, but it brings a second release tool and its own
configuration file to a project whose release is twenty lines of workflow.
Neither pays for itself here.

The plugin sits in the `release` profile rather than in the main build so that
`mvn verify` on a contributor's machine and in the build workflow stays exactly
what it is today: no publishing plugin loaded, no credentials expected.

### Keep the coordinates at `it.baccan`, verified by DNS

`io.github.matteobaccan` would be verified automatically by the Portal because
the account signs in with GitHub, and would save the DNS record entirely. It was
rejected for three reasons that outlive the five minutes it saves. The groupId
would stop matching the `it.baccan.vmjms` package, permanently, because renaming
the package is a breaking change nobody should pay for cosmetics. The namespace
would be tied to a personal GitHub username, and the Portal does not
auto-register `io.github.<organization>`, so moving the project under an
organization later would not move the namespace with it. And 1.0.0 is already
published as `it.baccan:vmjms`, so keeping the groupId keeps one coordinate for
the library's whole history.

The cost is a TXT record on `baccan.it` once, and keeping that domain
registered, which is not a new obligation.

### Remove `<distributionManagement>` instead of keeping two targets

With `<extensions>true</extensions>` the plugin replaces the deploy step, so
inside the `release` profile the GitHub Packages repository would be bypassed
whether it is declared or not. Leaving it declared would mean a `mvn deploy`
without the profile still pushes to GitHub Packages, the one behaviour this
change exists to retire, left reachable by forgetting a flag. It goes.

The consequence is deliberate: after this change, `mvn deploy` without
`-Prelease` fails for want of a repository, and the only way to publish is the
release profile.

### Stage the first release, then switch on automatic publishing

Central is immutable: a published version cannot be deleted, replaced or fixed,
only superseded. So the plugin is committed with `autoPublish` left off, which
makes 1.1.0 upload, validate and then wait on the Portal for a human to look at
the deployment and press publish. Once that run has proved the pipeline,
`autoPublish=true` and `waitUntil=published` land as a separate, small commit,
at which point a tag is enough and the workflow finishes only when the version
is actually live.

*Alternative considered.* Shipping `autoPublish=true` immediately and relying on
the Portal's validation to catch mistakes. Validation checks signatures,
checksums and POM metadata; it does not check that the jar is the one we meant
to release. For the first run through a pipeline nobody has exercised, one pair
of human eyes is cheap insurance against a permanent mistake.

### Credentials: a Portal user token and a dedicated signing key

The Portal's user token is a username/password pair generated from the account
page; it is not the account's own password. It reaches Maven as the `central`
server id, which `actions/setup-java` writes into the generated `settings.xml`
from two secrets, so no credential is ever in the repository.

GPG is handled the same way: `setup-java` imports the private key from a secret,
and `maven-gpg-plugin` 3.x reads the passphrase from `MAVEN_GPG_PASSPHRASE` and
already runs `gpg` with loopback pinentry, which is what makes signing work on a
runner with no tty.

### Keep attaching the jars to the GitHub Release

Unchanged, and worth stating: it costs nothing, it gives a download path to
someone who does not want Maven at all, and it is the fallback if a Portal
validation ever blocks a release we have already tagged.

## Risks / Trade-offs

- **Confirming the namespace before the TXT record has propagated** - the
  Portal's own documentation warns that the `NXDOMAIN` is cached and the
  verification then stalls until the cache expires. The tasks put the record
  first and a propagation check between it and the confirmation.
- **A wrong 1.1.0 on Central cannot be withdrawn** - the first run is staged for
  manual publication, and `mvn -Prelease clean verify` is run locally first so
  that the `.asc` files, the sources jar and the javadoc jar are inspected
  before anything is uploaded.
- **Losing the signing key, or letting it expire** - the key is generated for
  this purpose, its public half goes to a keyserver and its private half is
  backed up outside the runner. A key that expires silently turns the next
  release red, not wrong.
- **Validation rejects the bundle for missing POM metadata** - all the required
  elements are already in the POM and were checked against the requirements
  page; the first staged run confirms it against the real validator.
- **`baccan.it` lapsing** - the namespace is tied to continued ownership of the
  domain. This is the standing cost of the DNS route and the reason it was worth
  comparing with `io.github.matteobaccan` in the open.
- **A consumer relying on GitHub Packages** - none is known, and 1.0.0 stays
  resolvable there; the README states where each version lives.
- **`javadoc` is configured with `failOnWarnings=true`** - stricter than Central
  requires, so it fails in `verify` on the maintainer's machine rather than in a
  Portal validation report. Kept as it is.

## Migration Plan

1. Portal account, namespace `it.baccan` requested, TXT record on `baccan.it`,
   propagation checked, then verification confirmed.
2. Signing key generated, public half sent to a keyserver, private half and
   passphrase stored as repository secrets together with the Portal token.
3. `pom.xml`: publishing plugin into the `release` profile,
   `<distributionManagement>` out.
4. `release.yml`: `central` server id, GPG key import, `-Prelease` on the deploy
   command.
5. Tag `v1.1.0`, let it stage, inspect the deployment on the Portal, publish,
   and confirm the artifact under
   `https://repo1.maven.org/maven2/it/baccan/vmjms/1.1.0/`.
6. `autoPublish=true` and `waitUntil=published`.
7. The README's *Install* section rewritten, with a line on where 1.0.0 lives.

**Rollback.** Before step 5 nothing is public and reverting the two commits
restores the current state. After it, Central cannot be rolled back: the
recovery for a bad release is 1.1.1, which is the ordinary way of things and the
reason step 5 is staged.

## Open Questions

- Does the signing key get an expiry date, with a calendar reminder, or none at
  all? A key that never expires is one less red release and one more key that
  outlives its usefulness.
- Snapshots on Central can be enabled per namespace later. Worth it only if
  someone outside the project ever wants to try `main` before a tag.
