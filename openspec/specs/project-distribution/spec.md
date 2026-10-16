# Specification: Project Distribution

## Purpose
This specification defines how vmjms is identified, licensed, documented,
branded, built and published as a Maven artifact.
## Requirements
### Requirement: Product Coordinates
The product SHALL be identified by the Maven coordinates `it.baccan:vmjms` and all Java types SHALL reside under the `it.baccan.vmjms` package namespace. No source file, build descriptor, user-facing document, directory name or build output SHALL reference any other namespace.

#### Scenario: Namespace is consistent
- **WHEN** tracked files, directory names and build output are searched case-insensitively for Java package declarations, imports and Maven coordinates
- **THEN** every one of them SHALL resolve to `it.baccan`

#### Scenario: Maven coordinates match the package
- **WHEN** a consumer inspects the POM
- **THEN** the `groupId` SHALL be `it.baccan` and the `artifactId` SHALL be `vmjms`

### Requirement: Open Source License
The product SHALL be distributed under the MIT license, stated both as a `LICENSE` file at the repository root and as a `<licenses>` entry in the POM.

#### Scenario: License is discoverable
- **WHEN** a consumer opens the repository root or resolves the published POM
- **THEN** the MIT license SHALL be declared in both places with the same name

### Requirement: User Documentation
The repository SHALL provide an English `README.md` that explains what the provider is, how to depend on it, how to send and receive a message, how broker isolation and sharing work, which JMS features are not implemented, and the situations the provider is meant for. The README SHALL display the project logo.

#### Scenario: Use cases are stated
- **WHEN** a developer evaluating the library reads the README
- **THEN** it SHALL present a set of plausible use cases, each explaining the situation it addresses, and SHALL state explicitly the situations for which the provider is not suitable

#### Scenario: Unsupported features are declared
- **WHEN** a developer reads the README
- **THEN** it SHALL list explicitly the JMS features that throw `UnsupportedOperationException`, including topics, asynchronous delivery, transactions, `JMSContext`, `MapMessage`, `ObjectMessage`, `StreamMessage`, `QueueBrowser` and temporary destinations

### Requirement: Visual Identity
The repository SHALL provide brand assets under `docs/`: a scalable logo, a wordmark, and a social-preview image in a raster format accepted by the hosting platform, together with its vector source. The logo SHALL carry a small, looping animation that conveys messages draining from a queue, SHALL honour the viewer's reduced-motion preference, and SHALL be accompanied by a static variant for contexts that strip animation.

#### Scenario: Logo animates subtly
- **WHEN** the logo is displayed in a browser
- **THEN** its message bars SHALL animate in a continuous loop, and the animation SHALL stop when the viewer requests reduced motion

#### Scenario: Social preview is usable by the platform
- **WHEN** the maintainer uploads the social preview to the repository settings
- **THEN** a PNG of 1280x640 pixels SHALL be available under `docs/`

### Requirement: Continuous Build
Every push and pull request SHALL be compiled and tested automatically on the Java versions the product supports.

#### Scenario: Pull request is validated
- **WHEN** a pull request is opened
- **THEN** the workflow SHALL run the full Maven verify lifecycle on JDK 17 and JDK 21, and SHALL fail the check if compilation or any test fails

### Requirement: Maven-Consumable Release Packaging
A release SHALL be produced from a version tag and SHALL publish artifacts that downstream projects can resolve with Maven: the main jar, a sources jar and a javadoc jar.

#### Scenario: Tag triggers publication
- **WHEN** a tag matching `v*` is pushed
- **THEN** the release workflow SHALL build the main, sources and javadoc jars, publish them to a Maven repository using the repository's own credentials, and attach them to the corresponding GitHub Release

#### Scenario: Release is reproducible on demand
- **WHEN** the maintainer triggers the release workflow manually
- **THEN** the same artifacts SHALL be produced without requiring a new tag

### Requirement: Common Source File Header
Every Java source file in the repository, under `src/main/java` and `src/test/java` alike, SHALL begin with one common header, identical in every file, stating the product and the address of its repository, the copyright of the author, and the licence as an SPDX identifier. The header SHALL be the first content of the file, before the package declaration, and SHALL be a plain block comment so that it is never read as documentation of the package or of a type.

The build SHALL verify the header as part of the `verify` lifecycle and SHALL fail when a source file is missing it or carries one that differs from the single template the project keeps. The failure SHALL name the files at fault. The project SHALL also provide a command that writes the header into the files that lack it, so that complying does not require editing each file by hand.

The text of the header SHALL be held in one file, so that the check, the command that applies it and any future change to it all read the same source of truth. The copyright year SHALL be fixed and SHALL NOT be derived from the current date, so that the build does not start failing on the first day of a new year.

#### Scenario: A reader of the sources learns where they come from
- **WHEN** a developer opens any Java source file, in the repository or unpacked from the published sources jar
- **THEN** the first lines SHALL name the product, the URL of its GitHub repository, the copyright holder and the licence

#### Scenario: A file without the header fails the build
- **WHEN** `mvn verify` runs on a tree where a Java source file has no header, for example one just added
- **THEN** the build SHALL fail, and the output SHALL name that file

#### Scenario: A modified header fails the build
- **WHEN** a Java source file carries a header whose text differs from the template, for example with another repository URL or another copyright holder
- **THEN** the build SHALL fail and name that file, so that the check is on the content of the header and not merely on its presence

#### Scenario: The headers can be applied without hand-editing
- **WHEN** the maintainer runs the command the project provides for it
- **THEN** every Java source file missing the header SHALL receive it, placed before the package declaration and above the package javadoc where one is present, and files that already carry it SHALL be left untouched

#### Scenario: A pull request cannot land a file without the header
- **WHEN** a pull request adds or changes a Java source file without the header
- **THEN** the continuous build SHALL fail the check, for the same reason and with the same message a local `mvn verify` gives

#### Scenario: Package documentation survives the header
- **WHEN** the header is present in a `package-info.java` file
- **THEN** the package documentation SHALL remain the javadoc comment that follows the header, and the generated javadoc SHALL be unchanged

#### Scenario: The compiled artifact is unaffected
- **WHEN** the jar is built before and after the headers are introduced
- **THEN** the compiled classes SHALL be the same, since a comment is not code

