# Design: The documentation and the javadoc, published on GitHub Pages

## Context

The guide is `README.md`, about 460 lines long, with a centered HTML header
that shows `docs/logo.svg`. It has two anchor links within the page and six
links to repository files: `.github/workflows/build.yml`,
`.github/workflows/release.yml`, `etc/header.txt`, `docs/release.md` and
`LICENSE`. GitHub renders all of them. A site built from the README alone would
render the two anchors and anything under `docs/`, and would break the rest.

The javadoc is configured once, on `maven-javadoc-plugin` in the POM: doclint
`all,-missing`, `failOnWarnings`, `notimestamp`. The configuration is set at
plugin level, so it applies to the `jar` goal used by the release and to the
`javadoc` goal alike.

A release starts when a `v*` tag is pushed. The POM in the tagged commit still
says `-SNAPSHOT` (`v1.1.0` carries `1.1.0-SNAPSHOT`); the release workflow sets
the version from the tag before it builds. GitHub Pages is not enabled on the
repository yet.

## Goals / Non-Goals

**Goals**

- One URL that serves the guide and the API of the version on Maven Central.
- No second copy of the guide: the README stays the only source.
- A site that cannot publish an unreleased API and cannot get in the way of a
  release.
- A way to rebuild the site for a given tag, which the first publication needs
  anyway.

**Non-Goals**

- Theme work beyond one navigation bar, versioned copies, documentation of `main`, and
  javadoc cross-links to Jakarta (see the proposal).

## Decisions

### Jekyll, as GitHub runs it, on a staged copy of the docs

The site is built by `actions/jekyll-build-pages`, the same Jekyll and the same
default plugins that GitHub Pages uses when it builds from a branch. A README
without front matter becomes the index (`jekyll-readme-index`,
`jekyll-optional-front-matter`), and `.md` links to other pages on the site
become `.html` links (`jekyll-relative-links`). The default theme is Primer,
which renders the README much as github.com does, so the README needs no
changes to look right. The `remove_originals` option of both plugins is on, so
the raw `.md` files are not published next to their HTML.

The one piece of theme work is an override of Primer's default layout, and it
was found during implementation. Primer prints the site title as an `<h1>`
above the page, so the README's own centered title appeared twice. The
override replaces that heading with a small navigation bar (*Guide*, *API
reference (javadoc)*, *GitHub*) and leaves the rest of the layout unchanged.
The bar matters for another reason too: it links the javadoc from every page
whatever the README of the published tag says. That covers `v1.1.0`, whose
README predates the link to the javadoc.

Jekyll does not run on the repository root, which holds `src/`, `target/`,
`openspec/` and many other Markdown files that are not documentation. The
workflow copies what the site needs into a staging directory: `README.md`,
`docs/` and a small `_config.yml` with the title, the description and the
theme. Jekyll runs on that directory. What gets published is therefore an
explicit list, not whatever happens not to be excluded.

Alternatives considered: MkDocs Material and the Maven site plugin. The
maintainer chose the GitHub-native option, which needs the fewest new files and
no second copy of the text.

### Links that leave the site are pinned to the tag

The links to `LICENSE`, the workflows and `etc/header.txt` are rewritten in the
staged copy, and only there. The same applies to the pages under `docs/`, where
`docs/release.md` links `../.github/workflows/release.yml`. Paths are resolved
from the directory of the page before they are tested. Each link becomes
`https://github.com/matteobaccan/vmjms/blob/<tag>/<path>`. Anchors (`#...`),
absolute URLs and paths under `docs/` are left alone, because they are valid on
the site. The README in the repository does not change, so on GitHub the links
stay relative and keep working on every branch.

The rewrite, rather than switching the README to absolute links, has two
advantages. It works on tags that already exist, which is what the first
publication of `v1.1.0` needs. It also pins each link to the version the page
documents, where an absolute link would point at whatever `main` holds today.

### The javadoc comes from the same plugin configuration as the jar

The workflow runs `mvn javadoc:javadoc` after it sets the POM version from the
tag, in the same way as the release workflow. It copies the output into
`apidocs/` of the staging directory before Jekyll runs. Javadoc HTML has no
front matter, so Jekyll copies it unchanged. This also avoids writing into
Jekyll's output directory, which the action produces from a container as root.

There is no second javadoc configuration. The doclint rules, the warnings that
fail the build and the missing timestamps all come from the POM, so the
published page and the published jar are the same document.

### A separate workflow, started by the release when it is done

The site is built by a new `pages` workflow, not by an extra job in `release`.
Its only trigger is `workflow_dispatch` with a required `tag` input, and
`release` uses it in its last step, `gh workflow run pages -f tag=<tag>`, after
the version is live on Central and the GitHub Release exists. The release
needs `actions: write` for that. A `GITHUB_TOKEN` cannot start workflows
through most events, but `workflow_dispatch` is one of the exceptions. The same
command, run by the maintainer, is the first publication and any rebuild.

The first version used `workflow_run` on `release` instead, and checked out the
`head_sha` of the triggering run. CodeQL flagged it as critical
(`actions/untrusted-checkout`), because that is the "pwn request" pattern: a
workflow with deployment rights runs code chosen by another run. It was not
exploitable here, since `release` only starts from a tag push or a dispatch,
and both need write access. But the safety depended on the triggers of another
file, and nothing in `pages` would show the risk if those triggers changed.
With `workflow_dispatch` as its only trigger, `pages` holds the guarantee by
itself: only someone with write access can start it, and it checks out
`refs/tags/<tag>`, so a branch name cannot stand in for a tag. It also covers
releases started by hand, which `workflow_run` gated on tag pushes did not.

Reasons for choosing this over a job inside `release`:

- **A failed release publishes nothing.** The site moves only after Central
  accepted the version. A tag whose release failed never shows an API that
  nobody can resolve.
- **Rebuilding the site does not touch Central.** Running the `release`
  workflow by hand would try to publish the version again, and Central refuses
  a second upload of a published version. The `pages` workflow can be run as
  often as needed.
- **It runs from `main`.** The release starts it on the default branch, and the
  maintainer does the same. That is what the `github-pages` environment accepts by default when
  it is created. A workflow triggered by a tag push would be rejected unless the
  environment allowed tags too. It is also what lets the workflow be dispatched
  for `v1.1.0`, a tag that predates the workflow file.

The job checks out the repository twice. The site tooling in `etc/site/` comes
from the branch the workflow runs on, and the content comes from the tag, in a
`release/` directory. A tag cut before the site existed does not carry the
tooling, and this was the first thing the local trial of `v1.1.0` ran into.
Keeping the tooling on `main` also means a fix to the site applies to every
tag.

The workflow gets only the permissions that deployment requires:
`contents: read`, `pages: write` and `id-token: write`. It belongs to a
`concurrency` group that does not cancel a deployment in progress.

### The README links to the javadoc, and the site becomes the homepage

A line in the README links to `https://matteobaccan.github.io/vmjms/apidocs/`,
so a reader of the guide on github.com can reach the API too. On the site, the
same link works as it is. After the first deployment, the repository's *About*
homepage is set to the site's URL.

## Risks / Trade-offs

- **[The site lags behind `main`]** A README fix merged after a release does
  not appear until the next release. → This is intended, since the site
  describes the released version. A rebuild with `workflow_dispatch` reuses the
  tagged README, so a fix that cannot wait needs a patch release. The
  repository on github.com always shows `main`.
- **[`jekyll-build-pages` renders the README differently from github.com]**
  The centered `<p align="center">` header, the badges and the tables go
  through kramdown, not through GitHub's own renderer. → Check the rendered
  site against the repository page during verification. Kramdown passes block
  HTML through, and the default theme reproduces the GitHub styles.
- **[The link rewrite misses a pattern]** A future README link written in a
  form the rewrite does not recognise would break on the site. → The rewrite
  targets the inline `](path)` form, which is the only form the README uses.
  The verification checks every link on the published page.
- **[Starting the site can turn a published release red]** If the dispatch
  in the last step of `release` fails, the run is red although the version is
  already on Central. → This is intended: the step comes after everything that
  publishes, so nothing is undone, and the red run says that the site was not
  updated. The fix is to run `pages` by hand.
- **[The first deployment needs a manual setting]** Until the Pages source is
  set to *GitHub Actions*, the deploy step fails. → This is a one-off
  maintainer step, recorded in the tasks and in `docs/release.md`.

## Migration Plan

1. Merge the workflow on `main`.
2. Enable Pages with *GitHub Actions* as the source.
3. Run `pages` by hand with `tag: v1.1.0`, and check the site.
4. Set the *About* homepage to the site's URL.

From then on, every successful release updates the site with no further
action. To roll back, disable Pages or deploy the previous tag again by hand.
