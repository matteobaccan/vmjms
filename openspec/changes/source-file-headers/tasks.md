## 1. Define the header

- [ ] 1.1 Add the header template as a single file in the repository, holding the product name, the repository URL, the copyright of Matteo Baccan with the fixed year and the SPDX identifier of the MIT licence.
- [ ] 1.2 Confirm the text matches what `LICENSE` and the POM already state, so the three do not disagree.

## 2. Wire the check into the build

- [ ] 2.1 Add the header plugin to the POM, with the `check` goal bound to the `verify` lifecycle.
- [ ] 2.2 Scope it to the Java sources of main and test, and exclude everything that is not a Java source.
- [ ] 2.3 Confirm a missing header fails `mvn verify` and that the output names the offending file, by removing one header on purpose and putting it back.
- [ ] 2.4 Confirm a changed header fails too, by altering the repository URL in one file on purpose.
- [ ] 2.5 Confirm the command that applies the header leaves an already correct file untouched, so running it twice produces no diff.

## 3. Apply the header to the existing sources

- [ ] 3.1 Apply the header to the Java sources that lack it, in a commit that changes nothing else.
- [ ] 3.2 Check by hand that in each `package-info.java` the header sits above the package javadoc, and that the javadoc comment is still the one documenting the package.
- [ ] 3.3 Build the javadoc and confirm the three package summaries still show their own text and not the copyright notice.
- [ ] 3.4 Confirm the compiled classes are unchanged by the headers, comparing the jar built before and after.

## 4. Verification

- [ ] 4.1 Run the whole suite and `mvn clean verify` on JDK 17 and JDK 21, as CI does.
- [ ] 4.2 Unpack the published sources jar and confirm every file in it carries the header.
- [ ] 4.3 Confirm the continuous build fails a branch that adds a Java file without the header, which is the same check running where it matters.

## 5. Documentation

- [ ] 5.1 State the rule and the command that applies it where a contributor will look for it, so that meeting the check does not require reading the POM.
