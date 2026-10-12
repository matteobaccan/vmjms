## Why

The product had no settled coordinates, no license, no user-facing
documentation, no visual identity and no automated build. Without a license the
code is not legally reusable; without a published artifact the provider cannot
be consumed with Maven by downstream projects; without CI every release depends
on a developer's workstation.

In addition, while settling the coordinates, the suite exposed a latent defect:
the default (no-argument) `InMemoryConnectionFactory` derived its Broker ID from
`System.currentTimeMillis()`, so two factories created within the same
millisecond resolved to the *same* registry entry and silently shared queues.
This violates the existing "In-Memory Broker Isolation" requirement.

## What Changes

- Settle the product on the `it.baccan` coordinates: Java packages, Maven
  `groupId` and every documentation reference.
- Publish the project under the MIT license (`LICENSE` file plus POM metadata).
- Add an English `README.md` documenting purpose, usage, broker sharing, queue
  parameters and the explicit list of unsupported JMS features.
- Add a visual identity: an SVG logo, an SVG wordmark and a rendered PNG social
  preview for the public repository.
- Guarantee that the default Broker ID is unique, so the isolation requirement
  holds regardless of clock granularity.
- Add GitHub Actions for continuous build/test and for tag-driven release
  packaging, producing Maven-consumable artifacts (main, sources and javadoc
  jars) published to GitHub Packages and attached to the GitHub Release.
- Drop the duplicated `.agent/` tool directory, keeping `.claude/` as the single
  agent-instruction source.

## Capabilities

### New Capabilities

- `project-distribution`: how the product is identified, licensed, documented,
  branded, built and published as a Maven artifact.

### Modified Capabilities

- `in-memory-jms`: strengthen the default Broker ID so isolation between
  anonymous factories is guaranteed, not merely likely.

## Impact

- **Consumers**: types are imported from `it.baccan.vmjms.*` and resolved from
  the `it.baccan` groupId. No released artifact predates these coordinates, so
  nothing downstream has to be migrated.
- **Build**: new `.github/workflows/` pipelines; POM gains license, SCM,
  developer, `distributionManagement` and release-profile metadata plus the
  source/javadoc plugins.
- **Runtime**: the generated default Broker ID gains a counter suffix, so the
  literal value of an anonymous broker's message IDs changes
  (`ID:<brokerId>:<instance>:<sequence>` keeps its shape).
- **Repository hygiene**: `.agent/` removed; `docs/` added for brand assets.
