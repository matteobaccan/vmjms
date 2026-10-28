# Releasing vmjms

A release is published to **Maven Central** through the
[Central Portal](https://central.sonatype.com), under the coordinates
`it.baccan:vmjms`. The whole publication is driven by the
[`release`](../.github/workflows/release.yml) workflow, which is triggered by a
`v*` tag (or by hand through *workflow_dispatch*). A consumer resolves the
artifact with a dependency declaration alone: no repository to declare, no
credentials.

Versions up to 1.0.0 were published to GitHub Packages; 1.1.0 onwards is on
Maven Central.

## One-off prerequisites

These live outside the build, were set up once, and are recorded here so they
are not rediscovered at the next release. None of them is in the repository.

**Verified namespace.** The `it.baccan` namespace is verified on the Central
Portal by proof of ownership of `baccan.it` (a DNS TXT record). The
verification is what allows the published groupId to keep matching the
`it.baccan.vmjms` package namespace. It survives as long as the domain is
registered under the same account.

**Signing key.** A dedicated OpenPGP key signs every published file:

| | |
|---|---|
| Key id | `4652AF9E7D5A44A6` |
| Fingerprint | `B0F5675AEF0F3C0E0A9D59754652AF9E7D5A44A6` |
| Identity | `Matteo Baccan (vmjms) <matteo.baccan@gmail.com>` |
| Type | RSA 4096 |
| Expires | 2027-04-14 |
| Public half | published on `keyserver.ubuntu.com` |

The public half on a keyserver is what lets a third party verify a signature
without ever contacting the project. The private half and its passphrase reach
the build only as the repository secrets below; neither belongs in the
repository, in a build file or in a command that ends up in a shell history.

**There is deliberately no backup of the private half.** The key is a
disposable instrument: it proves that a published file is the one the project
uploaded, and nothing else in the project hangs on its continuity. Losing it
costs one `gpg --full-generate-key` — see *Replacing the key* — and a
reinstalled machine is enough to lose it, because a GitHub secret cannot be
read back. What a lost key cannot do is be revoked, since its revocation
certificate goes with it; that is survivable for a key that is merely lost,
because it lapses by itself on its expiry date, and it is the reason the key has
an expiry date at all.

**Repository secrets.** Four, set with `gh secret set <NAME>` (or through
*Settings* → *Secrets and variables* → *Actions*):

| Secret | What it holds | Where it comes from |
|---|---|---|
| `CENTRAL_TOKEN_USERNAME` | user token name | Portal → *View Account* → *Generate User Token* |
| `CENTRAL_TOKEN_PASSWORD` | user token secret | the same token; **not** the account password |
| `MAVEN_GPG_PRIVATE_KEY` | the armored private key | `gpg --armor --export-secret-keys <KEYID>` |
| `MAVEN_GPG_PASSPHRASE` | the key's passphrase | chosen when the key was generated |

Pipe the key straight into the secret so it never lands on disk:

```sh
gpg --armor --export-secret-keys 4652AF9E7D5A44A6 | gh secret set MAVEN_GPG_PRIVATE_KEY
```

`actions/setup-java` reads the first two into the generated `settings.xml`
under the server id `central`, imports the third into the runner's keyring and
exposes the fourth to `maven-gpg-plugin`.

## Releasing a version

1. Make sure `main` is green and the changelog-worthy work is merged.
2. Tag and push:

   ```sh
   git tag v1.1.0 && git push origin v1.1.0
   ```

3. The workflow sets the POM version from the tag, builds and tests on JDK 17,
   produces the main, sources and javadoc jars, signs each of them and the POM,
   uploads the bundle to the Central Portal, and attaches the three jars to the
   GitHub Release.
4. The publishing plugin runs with `autoPublish` on and `waitUntil` set to
   `published`, so the deployment is published without anyone pressing a button
   and the workflow finishes only once the version is actually live. A job that
   ends green therefore means a resolvable artifact; a validation the Portal
   rejects fails the release instead of leaving it half done.
5. Confirm the artifact is live:

   ```sh
   curl -sI https://repo1.maven.org/maven2/it/baccan/vmjms/1.1.0/vmjms-1.1.0.jar
   ```

   It can take a few minutes to appear, and longer to show up in the Portal's
   search index.
6. Check that the [`pages`](../.github/workflows/pages.yml) workflow, which
   the last step of `release` starts, has published the site at
   <https://matteobaccan.github.io/vmjms/> with the new version in the javadoc
   title.
7. Open the next development version on `main`
   (`mvn versions:set -DnewVersion=1.2.0-SNAPSHOT`, committed as
   `chore: open 1.2.0-SNAPSHOT`).

**A published version is immutable.** It cannot be deleted, replaced or fixed,
only superseded. A mistake that reaches Central is corrected by releasing the
next patch version. Automatic publication is what 1.1.0 earned by going through
the pipeline under inspection; the safety net is now the tag itself, so read
what you are tagging, and use the local dry run below after any change to the
`release` profile.

## Documentation site

The site at <https://matteobaccan.github.io/vmjms/> serves the README as its
home page and the javadoc under `/apidocs/`, both from the latest release. It
is built by the [`pages`](../.github/workflows/pages.yml) workflow, which
checks out the tag, sets the POM version from it, runs `mvn javadoc:javadoc`
and renders the README with GitHub's own Jekyll. The tooling it uses lives in
`etc/site/` on `main`, so a tag that predates it can still be published.

`pages` has a single trigger, *workflow_dispatch* with the tag as its input.
The last step of `release` uses it, once the version is live on Central and
the GitHub Release exists, whether the release was started by a tag or by hand.
A release that fails earlier never gets there, so the site stays as it was.
Whenever the site needs rebuilding, run it by hand with the tag:

```sh
gh workflow run pages -f tag=v1.1.0
```

That rebuilds the site and nothing else: nothing goes to Central or to the
GitHub Release. Since the site is built from the tag, a README fix merged after
a release appears there only with the next release.

**One-off setting.** Pages must have *GitHub Actions* as its source
(*Settings* → *Pages* → *Build and deployment*), otherwise the deploy step
fails. The `github-pages` environment that this creates accepts deployments
from `main`, which is the branch both triggers run the workflow from.

## Local dry run

Worth doing before a first release, or after any change to the `release`
profile: it proves the artifacts and the signatures without uploading anything.

```sh
mvn -Prelease clean verify
```

Then check that `target/` holds the three jars and the POM each with a matching
`.asc`, and verify one signature by hand:

```sh
gpg --verify target/vmjms-1.1.0.jar.asc target/vmjms-1.1.0.jar
```

The key named in the output must be the one in the table above.

On a Windows machine where GnuPG is not on `PATH`, point the plugin at it
explicitly, and mind that the `gpg` bundled with Git Bash is a *different*
installation with its own keyring:

```sh
mvn -Prelease clean verify -Dgpg.executable="C:/Program Files/GnuPG/bin/gpg.exe"
```

`maven-gpg-plugin` signs in `--batch`, where gpg cannot open a pinentry
dialog. The passphrase therefore has to be available without a prompt, either
cached in `gpg-agent` (sign anything once interactively first) or passed
through loopback by exporting `MAVEN_GPG_PASSPHRASE` in the shell that runs
Maven. Export it in your own terminal, never in a shared session or a script.

## Key maintenance

The signing key expires on **2027-04-14**. An expired key fails a release; it
does not invalidate signatures already published. To extend it:

```sh
gpg --quick-set-expire B0F5675AEF0F3C0E0A9D59754652AF9E7D5A44A6 1y
gpg --keyserver keyserver.ubuntu.com --send-keys B0F5675AEF0F3C0E0A9D59754652AF9E7D5A44A6
```

The second command is not optional: the keyserver serves the old expiry until
the updated key reaches it.

### Replacing the key

If the key is lost, compromised or simply retired, generate another one and
point the secrets at it:

```sh
gpg --full-generate-key                      # RSA 4096, with a passphrase
gpg --keyserver keyserver.ubuntu.com --send-keys <NEW FINGERPRINT>
gpg --armor --export-secret-keys <NEW KEYID> | gh secret set MAVEN_GPG_PRIVATE_KEY
gh secret set MAVEN_GPG_PASSPHRASE
```

Then update the table above. Nothing on the Portal side needs to be told:
Central checks that a signature verifies against a key on a public keyserver,
it does not pin a namespace to one key. Versions already published keep
verifying against the old public half, which stays on the keyserver.

## Troubleshooting

**`mvn` is not recognized.** Maven and a JDK 17 are not installed on the
maintainer's machine. Unpack both, set `JAVA_HOME` to the JDK 17 directory and
call Maven by its full path; the build workflow is the authority on what a
release must pass, so a local run is a convenience, not a gate.

**`gpg: signing failed: No pinentry`.** The agent has no pinentry program
configured, or it is being asked to prompt while in batch mode. Declare the
program once in `gpg-agent.conf` (`pinentry-program` plus
`allow-loopback-pinentry`, then `gpgconf --kill gpg-agent`), and for a batch
signing make sure the passphrase is cached or supplied through loopback.

**`gpg: keyserver receive failed: No data`.** `keyserver.ubuntu.com`
rate-limits repeated requests, so a `--recv-keys` right after a `--send-keys`
can come back empty while the key is in fact there. Check the index directly:

```sh
curl "https://keyserver.ubuntu.com/pks/lookup?op=index&search=0x4652AF9E7D5A44A6"
```

**The Portal rejects the bundle.** The validation report names the missing
piece. Everything it requires — `name`, `description`, `url`, `licenses`,
`developers`, `scm`, the sources and javadoc jars, a signature per file — is
produced by the `release` profile, so a rejection means something in the POM
has regressed; fix it and release the next version.

**The release job hangs on the publish step.** With `waitUntil` set to
`published` the plugin polls the Portal until the version is live, which takes
minutes rather than seconds. A job still running there is normal; a job that
ends green has published. If the wait ever times out, the deployment is visible
on the Portal and the version may well be live anyway - check
`repo1.maven.org` before concluding that the release failed, and never re-tag a
version that is already there, because Central refuses a second upload of a
published version.
