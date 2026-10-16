## ADDED Requirements

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
