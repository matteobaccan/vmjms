# Change: The documentation and the javadoc, published on GitHub Pages

## Why

Everything a user needs to adopt vmjms is written down, but none of it is
published as a website. The guide lives in a `README.md` that can be read only
by browsing the repository. The javadoc is held to a high standard: doclint is
on, a warning fails the build, and every class documents where it departs from
the specification. Even so, it reaches readers only as a `-javadoc.jar` on
Maven Central, which an IDE unpacks and a person almost never opens. Nothing
gives a developer who is evaluating the library one address to bookmark, link
from an answer or put in the repository's *About* box, and no address leads
from the guide to the API it describes.

Both texts already exist and are already checked by the build. All that is
missing is the step that publishes them, and the moment to take it is the
release: that is when the documentation and the API are known to match an
artifact anyone can resolve.

## What Changes

- The project gets a GitHub Pages site. The README is its home page, rendered
  by GitHub's own Jekyll with no theme work and no second copy of the text.
  The maintainer documentation in `docs/` and the brand assets the README uses
  are published with it.
- The javadoc is published under `/apidocs/` on the same site. It is generated
  from the same POM configuration as the published javadoc jar, so the
  website and the jar cannot disagree.
- The site documents the **latest release**, not `main`. It is rebuilt when a
  release completes successfully, from the tagged sources and with the released
  version number, so it never describes an API that is not yet on Maven Central.
  The maintainer can also rebuild it by hand for a given tag, which is how the
  current release, 1.1.0, gets its first site.
- Links in the README that point at repository files outside the site
  (`LICENSE`, the workflows, `etc/header.txt`) are rewritten while the site is
  built, so they point at those files on GitHub at the published tag and do not
  break.
- The README links to the published javadoc, and the repository's *About*
  homepage points at the site.

## Capabilities

### New Capabilities

None. A documentation site is part of how the project is distributed.

### Modified Capabilities

- `project-distribution`: gains a requirement for a documentation site that
  publishes the README and the javadoc of the latest release, rebuilt
  automatically after each release and rebuildable by hand for a given tag.

## Impact

- Affected specs: `project-distribution`
- Affected code: a new `pages` workflow under `.github/workflows/`, a minimal
  Jekyll configuration, and a link to the javadoc in `README.md`. No Java
  source, no POM plugin and no test changes.
- Repository settings: GitHub Pages enabled with *GitHub Actions* as the
  source, and the *About* homepage set to the site's URL. Both are one-off
  steps by the maintainer, outside the repository.
- No behaviour of the provider changes, and the release workflow is untouched:
  a site that fails to build cannot stop or undo a release.

## Non-Goals

- A documentation site with its own structure, navigation or theme beyond what
  GitHub's Jekyll gives the README, apart from one navigation bar that links
  the javadoc. The README stays the single source of the guide.
- Versioned documentation, where every release keeps its own copy online. The
  site shows one version, the latest one; older javadoc remains on javadoc.io
  and in the jars on Central.
- Documentation of unreleased work from `main`.
- Cross-links from the javadoc to the Jakarta Messaging API docs. Fetching an
  external package list at build time would make the build depend on the
  network, and with warnings treated as errors, a network outage would fail
  the build.
