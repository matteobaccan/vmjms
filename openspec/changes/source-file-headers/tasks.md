## 1. Define the header

- [x] 1.1 Add the header template as a single file in the repository, holding the product name, the repository URL, the copyright of Matteo Baccan with the fixed year and the SPDX identifier of the MIT licence. **Done: `etc/header.txt`.**
- [x] 1.2 Confirm the text matches what `LICENSE` and the POM already state, so the three do not disagree. **Confirmed: `LICENSE` says `Copyright (c) 2026 Matteo Baccan`, the POM `<url>` is `https://github.com/matteobaccan/vmjms`; the header repeats both verbatim.**

## 2. Wire the check into the build

- [x] 2.1 Add the header plugin to the POM, with the `check` goal bound to the `verify` lifecycle. **Done: `com.mycila:license-maven-plugin` 5.1.2, `check` bound to `verify`.**
- [x] 2.2 Scope it to the Java sources of main and test, and exclude everything that is not a Java source. **Scoped to `src/main/java/**/*.java` and `src/test/java/**/*.java`, `SLASHSTAR_STYLE`.**
- [x] 2.3 Confirm a missing header fails `mvn verify` and that the output names the offending file, by removing one header on purpose and putting it back. **Verified: header stripped from `InMemoryQueue.java`, `mvn verify` failed naming that file, then restored.**
- [x] 2.4 Confirm a changed header fails too, by altering the repository URL in one file on purpose. **Verified with `strictCheck`: the URL changed to another account failed the build and named the file.**
- [x] 2.5 Confirm the command that applies the header leaves an already correct file untouched, so running it twice produces no diff. **Verified: a second `license:format` reported no update and left the diff identical.**

## 3. Apply the header to the existing sources

- [x] 3.1 Apply the header to the Java sources that lack it, in a commit that changes nothing else. **Done: 27 files, 228 lines added, nothing else touched.**
- [x] 3.2 Check by hand that in each `package-info.java` the header sits above the package javadoc, and that the javadoc comment is still the one documenting the package. **Checked: in all four the header is a `/*` block above the `/**` package javadoc.**
- [x] 3.3 Build the javadoc and confirm the three package summaries still show their own text and not the copyright notice. **Verified on the generated javadoc: the four package summaries still open with their own text and no page contains the SPDX line.**
- [x] 3.4 Confirm the compiled classes are unchanged by the headers, comparing the jar built before and after. **Verified by disassembly: all 22 classes identical in `javap -c -constants`. The raw bytes differ only in the `LineNumberTable`, which shifts by the seven header lines; the code does not.**

## 4. Verification

- [x] 4.1 Run the whole suite and `mvn clean verify` on JDK 17 and JDK 21, as CI does. **Green on JDK 17 and on JDK 21, 63 tests, header check included.**
- [x] 4.2 Unpack the published sources jar and confirm every file in it carries the header. **Verified: 21 of 21 Java files in the sources jar carry the header.**
- [x] 4.3 Confirm the continuous build fails a branch that adds a Java file without the header, which is the same check running where it matters. **Verified on a throwaway branch: run 38913213570 failed on JDK 17 and JDK 21 alike, naming `HeaderCheckProbe.java`, with the same message a local `verify` gives. Branch deleted.**

## 5. Documentation

- [x] 5.1 State the rule and the command that applies it where a contributor will look for it, so that meeting the check does not require reading the POM. **Done: *Source headers* section in the README, with `mvn license:format`.**
