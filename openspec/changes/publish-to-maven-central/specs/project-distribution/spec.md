## MODIFIED Requirements

### Requirement: Maven-Consumable Release Packaging
A release SHALL be produced from a version tag and SHALL publish artifacts that
downstream projects can resolve with Maven: the main jar, a sources jar and a
javadoc jar.

The publication target SHALL be Maven Central, reached through the Central
Portal, and SHALL be the only deployment repository the build declares. Every
file the release publishes, the three jars and the POM alike, SHALL carry a
detached GPG signature produced during the build.

A consumer SHALL be able to resolve a released version with a dependency
declaration alone, without credentials and without declaring a repository.

The release workflow SHALL fail when the publication does not complete, so that
a successful run always means a resolvable artifact. It SHALL NOT report success
over an upload that was skipped, rejected or silently dropped.

The same jars SHALL also be attached to the corresponding GitHub Release, so a
consumer who does not use Maven can download them directly.

#### Scenario: Tag triggers publication
- **WHEN** a tag matching `v*` is pushed
- **THEN** the release workflow SHALL build the main, sources and javadoc jars, sign them, upload them to the Central Portal under the coordinates `it.baccan:vmjms`, and attach the jars to the corresponding GitHub Release

#### Scenario: Release is reproducible on demand
- **WHEN** the maintainer triggers the release workflow manually
- **THEN** the same artifacts SHALL be produced without requiring a new tag

#### Scenario: A consumer resolves a release with no credentials
- **WHEN** a developer adds the `it.baccan:vmjms` dependency to a project whose `settings.xml` holds no credentials for this project and whose POM declares no additional repository
- **THEN** Maven SHALL resolve the jar, the POM and, on request, the sources and javadoc jars from Maven Central

#### Scenario: Every published file is signed
- **WHEN** a consumer fetches a released file together with its `.asc` companion and verifies it against the project's public key as published on a public keyserver
- **THEN** the signature SHALL verify, and this SHALL hold for the main jar, the sources jar, the javadoc jar and the POM

#### Scenario: A publication that does not happen fails the release
- **WHEN** the upload is rejected, the bundle fails validation, or the publisher reports that it has not published the version
- **THEN** the release workflow SHALL fail, and the failure SHALL name the reason the publication did not complete

#### Scenario: A released version cannot be overwritten
- **WHEN** the release workflow runs twice for a version already published to Maven Central
- **THEN** the second run SHALL fail rather than appear to succeed, because a published version is immutable

#### Scenario: Jars remain downloadable without Maven
- **WHEN** a developer opens the GitHub Release for a published version
- **THEN** the main, sources and javadoc jars SHALL be attached to it

## ADDED Requirements

### Requirement: Verified Publication Namespace and Signing Material
The `it.baccan` namespace SHALL be verified for this project on the Central
Portal, by proof of ownership of the `baccan.it` domain, so that the published
coordinates continue to match the `it.baccan.vmjms` package namespace.

The key that signs the release SHALL have its public half published on a public
keyserver, so that any consumer can verify a signature without obtaining the key
from the project. Its private half and passphrase SHALL reach the release
workflow only as repository secrets, and SHALL NOT be stored in the repository in
any form.

Neither the namespace verification nor the creation of the key is performed by
the build; both are acts of the maintainer, and the project SHALL record them as
prerequisites of a release rather than leaving them to be rediscovered.

#### Scenario: Published coordinates match the package namespace
- **WHEN** a consumer resolves the artifact from Maven Central and opens it
- **THEN** the groupId SHALL be `it.baccan` and the types inside SHALL reside under `it.baccan.vmjms`

#### Scenario: A signature is verifiable by a third party
- **WHEN** a consumer who has never contacted the project imports the signing key from a public keyserver and verifies a released file against its `.asc`
- **THEN** the verification SHALL succeed and SHALL identify the project's signing key

#### Scenario: No signing material is in the repository
- **WHEN** the tracked files of the repository are searched for private key material or a signing passphrase
- **THEN** none SHALL be found, and the release workflow SHALL obtain both from repository secrets
