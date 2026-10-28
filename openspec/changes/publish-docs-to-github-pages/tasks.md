## 1. Site sources

- [x] 1.1 Add the Jekyll configuration that the staged site uses: title, description and the default theme. The file must not be read by anything else in the repository. **Done: `etc/site/_config.yml`, with `remove_originals` on for `readme_index` and `optional_front_matter`, so the raw `.md` files are not published next to their HTML.**
- [x] 1.2 Add the script that stages the site from a checked-out tag. It copies `README.md` and `docs/`, and rewrites the README's relative links to files outside the site into `https://github.com/matteobaccan/vmjms/blob/<tag>/<path>`. Anchors, absolute URLs and `docs/` paths are left untouched. **Done: `etc/site/stage.sh`. It rewrites the links in `docs/*.md` as well, resolving each path from the page's own directory, because `docs/release.md` links `../.github/workflows/release.yml`. It takes the Jekyll files from its own directory, not from the tag.**
- [x] 1.3 Run the staging script locally against the current README and check its output: the `LICENSE`, workflow and `etc/header.txt` links are rewritten, while `#not-supported`, `#broker-lifecycle`, `docs/logo.svg` and `docs/release.md` are not. **Verified: the 4 README links outside the site and the `../` link in `docs/release.md` are rewritten to `blob/v1.1.0/...`. The two anchors, the logo and `docs/release.md` are untouched.**
- [x] 1.4 Override Primer's default layout so the site title is not printed above the README's own, and link the javadoc from every page. **Added during implementation: `etc/site/_layouts/default.html`, the Primer 0.6.0 layout with its `<h1>` replaced by a navigation bar. It is needed because the README of `v1.1.0` has no javadoc link.**

## 2. Workflow

- [x] 2.1 Look up the current major versions of `actions/configure-pages`, `actions/jekyll-build-pages`, `actions/upload-pages-artifact` and `actions/deploy-pages`, so the workflow matches the pinning style of the existing workflows. **v6, v1 (v1.0.13), v5 and v5 respectively.**
- [x] 2.2 Add `.github/workflows/pages.yml`, triggered by `workflow_dispatch` with a required `tag` input. Give it only the `contents: read`, `pages: write` and `id-token: write` permissions, and a concurrency group that does not cancel a deployment in progress. **Done. The YAML parses. actionlint was not run locally. A first version also had a `workflow_run` trigger on `release`; CodeQL flagged its checkout of `head_sha` as `actions/untrusted-checkout` (critical) on PR #23, so it was replaced by 2.5.**
- [x] 2.5 Make the last step of `release` start `pages` for the released tag, and give `release` the `actions: write` permission it needs to do so. **Done: `gh workflow run pages --ref <default branch> -f tag=<tag>`, after the GitHub Release step.**
- [x] 2.3 In the workflow, check out the tag, set the POM version from it, run `mvn javadoc:javadoc` on JDK 17, stage the site with the javadoc under `apidocs/`, build it with Jekyll, then upload and deploy it to the `github-pages` environment. **Done, with two checkouts: the tooling from the workflow's branch, the tag in `release/`. A single checkout of the tag failed the local trial, because `v1.1.0` has no `etc/site/`.**
- [x] 2.4 Run the javadoc and staging steps locally on `v1.1.0` with JDK 17. Confirm the javadoc is titled `1.1.0` and that its file list matches the published `vmjms-1.1.0-javadoc.jar`. **Verified, Temurin 17 and Maven 3.9.11:**
  - **Title.** The javadoc is titled `vmjms 1.1.0 API`.
  - **Jar on Central.** It has the same 73 files as the jar on Central. Outside `legal/`, which on Windows holds pointers to the JDK instead of the full texts, the contents differ only in the footer year: the jar on Central says `© 1980`, a local build `© 2026`.
  - **Local javadoc jar.** It is identical to the jar the same POM builds locally: 0 files differ.
  - **Jekyll build.** The site was built with the action's own image (`jekyll-build-pages:v1.0.13`) under Podman and checked in a browser. Logo, badges, code blocks and tables render, there is one `<h1>` per page, and the javadoc comes out byte for byte unchanged.

## 3. Documentation

- [x] 3.1 Add a link to the published javadoc in the README. **Added as *API reference*, in the list of coordinates at the top.**
- [x] 3.2 Record in `docs/release.md` that a successful release updates the site, how to rebuild it by hand for a tag, and the one-off Pages setting. **New *Documentation site* section, plus a step in *Releasing a version*.**
- [x] 3.3 State the site in the README's *Releasing* section, next to Maven Central and the GitHub Release.

## 4. Enablement and verification (after merge, with the maintainer)

- [ ] 4.1 Enable GitHub Pages with *GitHub Actions* as the source.
- [ ] 4.2 Run `pages` by hand with `tag: v1.1.0` and confirm it deploys.
- [ ] 4.3 Open the site. Check the logo, the badges, the tables and the code blocks against the README on github.com, and follow every link on the home page.
- [ ] 4.4 Open `/apidocs/` and confirm the version, the four package summaries and the search.
- [ ] 4.5 Set the repository's *About* homepage to the site's URL.
- [ ] 4.6 Archive this change into the specs.
