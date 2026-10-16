## 1. Central Portal prerequisites (manual, maintainer)

- [x] 1.1 Create the Central Portal account and register the `it.baccan` namespace.
- [x] 1.2 Verify the namespace by a DNS TXT record on `baccan.it` and confirm the verification on the Portal. **Done: `baccan.it` is verified.**
- [x] 1.3 Generate a Portal user token (*View Account* → *Generate User Token*) and keep the username/password pair for task 3.1. It is not the account password.

## 2. Signing key (manual, maintainer)

- [x] 2.1 Generate a dedicated GPG key for the project (`gpg --full-generate-key`, RSA 4096, with a passphrase) and record its key id. **Done: `4652AF9E7D5A44A6`, fingerprint `B0F5675AEF0F3C0E0A9D59754652AF9E7D5A44A6`, uid `Matteo Baccan (vmjms) <matteo.baccan@gmail.com>`.**
- [x] 2.2 Publish the public half: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`, then confirm it can be fetched back from a clean keyring. **Done: sent to keyserver.ubuntu.com and re-imported into an empty keyring.**
- [x] 2.3 Export the private half (`gpg --armor --export-secret-keys <KEYID>`) and back it up and its passphrase outside the runner and outside the repository. **Done as far as the secret goes. Decided against a backup: the key is disposable and a lost one is replaced rather than recovered, which `docs/release.md` records. The trade-off accepted with it is that a lost key cannot be revoked and lapses on 2027-04-14 instead.**
- [x] 2.4 Decide the expiry question left open in the design: an expiry date with a calendar reminder, or none. **Decided: the key expires on 2027-04-14; extend it with `gpg --quick-set-expire` before it lapses. A lapsed key fails a release, it does not invalidate signatures already published.**

## 3. Repository secrets

- [x] 3.1 Add `CENTRAL_TOKEN_USERNAME` and `CENTRAL_TOKEN_PASSWORD` from task 1.3. **Both set on 2026-10-16.**
- [x] 3.2 Add `MAVEN_GPG_PRIVATE_KEY` (the armored export from task 2.3) and `MAVEN_GPG_PASSPHRASE`. **Both set on 2026-10-16.**

## 4. POM

- [x] 4.1 Add `central-publishing-maven-plugin` 0.12.0 to the `release` profile, with `<extensions>true</extensions>` and `<publishingServerId>central</publishingServerId>`, leaving `autoPublish` off for now.
- [x] 4.2 Remove the `<distributionManagement>` block, so the release profile is the only publication path.
- [x] 4.3 Confirm `mvn verify` without `-Prelease` is unchanged: no publishing plugin resolved, no credentials expected, same test run.

## 5. Release workflow

- [x] 5.1 In `.github/workflows/release.yml`, change the `setup-java` step from `server-id: github` to `server-id: central` with `server-username: CENTRAL_TOKEN_USERNAME` and `server-password: CENTRAL_TOKEN_PASSWORD`, and add `gpg-private-key` and `gpg-passphrase: MAVEN_GPG_PASSPHRASE`.
- [x] 5.2 Replace the *Publish to GitHub Packages* step with a *Publish to Maven Central* step running `mvn -B -ntp -Prelease deploy -DskipTests`, with `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD` and `MAVEN_GPG_PASSPHRASE` in its `env`.
- [x] 5.3 Drop the now-unused `packages: write` permission, keeping `contents: write` for the GitHub Release.
- [x] 5.4 Leave the *Create GitHub Release with the jars* step as it is, and confirm the three jar paths it attaches still match what the build produces.

## 6. Local dry run, before anything is uploaded

- [x] 6.1 Run `mvn -Prelease clean verify` locally and confirm `target/` holds the main, sources and javadoc jars, each with a matching `.asc`, plus `pom.xml.asc`.
- [x] 6.2 Verify one signature by hand (`gpg --verify target/vmjms-*.jar.asc`) to prove the key in use is the one published in task 2.2.
- [x] 6.3 Unpack the sources jar and confirm it contains the sources and nothing unexpected.

## 7. First release, staged for inspection

- [x] 7.1 Tag `v1.1.0` and let the release workflow run. **Done 2026-10-16: run 38907688104 green, bundle validated, deployment `9ff32fe4-81bd-47b2-8184-e850f6a0c582` waiting for manual publishing. The three jars are attached to the GitHub Release.**
- [ ] 7.2 On the Portal, open the deployment, read the validation report, and check the coordinates, the version and the file list before publishing.
- [ ] 7.3 Publish the deployment by hand and confirm `https://repo1.maven.org/maven2/it/baccan/vmjms/1.1.0/` serves the four files and their signatures.
- [ ] 7.4 From a scratch project with an empty local repository and no credentials, resolve `it.baccan:vmjms:1.1.0` and run one send/receive, proving the credential-free consumption scenario.

## 8. Automatic publishing

- [ ] 8.1 Set `autoPublish` to `true` and `waitUntil` to `published` on the plugin, as a commit of its own.
- [ ] 8.2 Confirm on the next release that the workflow finishes only once the version is live, and fails when the publication does not complete.

## 9. Documentation

- [x] 9.1 Rewrite the README's *Install* section as a dependency block alone: remove the `<repositories>` block and the paragraph on tokens and `settings.xml`.
- [x] 9.2 Add one line stating that 1.0.0 lives on GitHub Packages and that 1.1.0 onwards is on Maven Central.
- [x] 9.3 Record the release prerequisites (verified namespace, signing key, the four secrets) where the next person will find them, so they are not rediscovered at the next release. **Done: `docs/release.md`, linked from the README's *Releasing* section. It holds no passphrase and no key material, only the public fingerprint and the secret names.**
- [x] 9.4 Check the rest of the README and `docs/` for any remaining mention of GitHub Packages as the place a release lands.

**Dry run of 2026-10-16:** `mvn -Prelease clean verify` green on JDK 17, 63 tests, the three jars plus the POM each signed (4 `.asc`), signature verified as `B0F5675AEF0F3C0E0A9D59754652AF9E7D5A44A6`, sources jar clean. Batch signing needs the passphrase cached in `gpg-agent` or passed through loopback; see `docs/release.md`.
