## ADDED Requirements

### Requirement: Published Documentation Site
The project SHALL publish a documentation site on GitHub Pages that serves the README as its home page and the javadoc under `/apidocs/`. The site SHALL document the latest released version: its README and javadoc SHALL be built from the sources of that version's tag, and the javadoc SHALL carry the released version number, not a `-SNAPSHOT` one. The javadoc on the site SHALL be generated from the same build configuration as the published javadoc jar.

The site SHALL be rebuilt automatically after a release completes successfully, and SHALL NOT be rebuilt by a release that failed. The maintainer SHALL be able to rebuild the site by hand for a given tag without publishing anything to the Maven repository. A failure to build or deploy the site SHALL NOT fail or undo a release.

Every link on the home page SHALL resolve. Links to repository files that are not part of the site SHALL point to those files on GitHub at the published tag. The README SHALL link to the published javadoc.

#### Scenario: A developer finds guide and API at one address
- **WHEN** a developer opens the site's root URL
- **THEN** it SHALL show the README with the project logo, and a link from it SHALL lead to the javadoc under `/apidocs/`

#### Scenario: A successful release updates the site
- **WHEN** the release workflow completes successfully for a tag `vX.Y.Z`
- **THEN** the site SHALL be rebuilt from that tag, and its javadoc SHALL be titled with version `X.Y.Z`

#### Scenario: A failed release leaves the site as it was
- **WHEN** the release workflow fails for a tag
- **THEN** the site SHALL NOT be rebuilt, and it SHALL keep documenting the last version that was released successfully

#### Scenario: The maintainer rebuilds the site for a tag
- **WHEN** the maintainer runs the site workflow by hand with a tag
- **THEN** the site SHALL be rebuilt from that tag, and nothing SHALL be published to the Maven repository or to GitHub Releases

#### Scenario: Links to repository files do not break
- **WHEN** a reader follows a link on the site's home page to a repository file that the site does not publish, such as `LICENSE`
- **THEN** the link SHALL open that file on GitHub at the tag the site was built from

#### Scenario: Site and jar carry the same javadoc
- **WHEN** the javadoc on the site and the javadoc jar published for the same version are compared
- **THEN** they SHALL contain the same pages
