## 1. Re-coordinate the product

- [x] 1.1 Lay out the main and test sources under `it/baccan/vmjms`, preserving history, and remove any leftover package directory.
- [x] 1.2 Rewrite every `package`/`import` statement and fully-qualified reference to `it.baccan.vmjms`.
- [x] 1.3 Set the POM `groupId` to `it.baccan`.
- [x] 1.4 Update the archived OpenSpec documents that cite the old package.
- [x] 1.5 Verify that `it.baccan.vmjms` is the only namespace present in tracked files, directory names and build output.

## 2. License and documentation

- [x] 2.1 Add a MIT `LICENSE` file for Matteo Baccan.
- [x] 2.2 Declare the MIT license in the POM `<licenses>` block.
- [x] 2.3 Write an English `README.md` covering purpose, install, quick start, broker isolation/sharing, queue-name parameters, message IDs, the supported matrix and the unsupported features.
- [x] 2.4 Add a "Use cases" section presenting plausible adoption scenarios and stating what the provider is not for.

## 3. Visual identity

- [x] 3.1 Create `docs/logo.svg`.
- [x] 3.2 Create `docs/logo-wordmark.svg`.
- [x] 3.3 Create `docs/social-preview.svg` at 1280x640 and render `docs/social-preview.png`.
- [x] 3.4 Reference the logo from the README.
- [x] 3.5 Animate the logo (messages draining from the queue), honour `prefers-reduced-motion`, and provide `docs/logo-static.svg` for contexts that strip animation.

## 4. Guarantee default broker isolation

- [x] 4.1 Make the generated default Broker ID unique within the JVM by appending a monotonic counter to the hex timestamp.
- [x] 4.2 Confirm `testIsolatedFactories` passes and that explicit Broker IDs are still honoured verbatim.

## 5. Build and release automation

- [x] 5.1 Add `.github/workflows/build.yml` running `mvn verify` on push and pull request over a JDK 17/21 matrix with Maven caching.
- [x] 5.2 Add POM metadata required for publication: `url`, `scm`, `developers`, `distributionManagement` for GitHub Packages.
- [x] 5.3 Add the `maven-source-plugin` and `maven-javadoc-plugin` so sources and javadoc jars are produced, plus a `release` profile for GPG signing.
- [x] 5.4 Add `.github/workflows/release.yml` that, on a `v*` tag or manual dispatch, builds the jars, publishes them to GitHub Packages and attaches them to the GitHub Release.
- [x] 5.5 Document the install/consume and release procedure in the README.

## 6. Repository hygiene

- [x] 6.1 Remove the duplicated `.agent/` directory, keeping `.claude/`.

## 7. Verification

- [x] 7.1 Recompile main and test sources from scratch.
- [x] 7.2 Run the whole test suite and confirm every test passes.
