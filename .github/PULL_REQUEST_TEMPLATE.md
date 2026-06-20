<!--
PR title must follow Conventional Commits, e.g.
  feat: add port range scan
  fix(scan): handle empty results
  docs: update install steps
This title becomes the squashed commit on main and feeds the release notes.
-->

## Description

<!-- What does this PR change, and why? -->

Closes #

## Checklist

- [ ] PR title follows [Conventional Commits](https://www.conventionalcommits.org)
- [ ] `./gradlew test` and `./gradlew lint` pass locally
- [ ] UI changes include screenshots / screen recordings (if applicable)

### Release PRs only

- [ ] `versionCode` bumped by exactly 1 **and** `versionName` updated in `app/build.gradle.kts`
- [ ] Branch is named `release/v<versionName>`
- [ ] `CHANGELOG.md` has a `## [<versionName>]` entry
- [ ] `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` added (F-Droid / Play "What's New")
