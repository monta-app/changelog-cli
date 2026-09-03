### About

A CLI project dedicated to generating a change log from commits the concept is simple:
you take the last two tags, and you get all the commits in between you parse the commits based on a system known as "
Conventional Commits"
and you get a nice formatted ChangeLog for people to read!

### Usage

Please run the following command, and you will get a list of all the options with relevant information on how to use
them

`./changelog-cli --help`

### Env Vars

Instead of using the CLI for entering values it's also possible to just setup environment variables (useful for GitHub
actions)

```shell
CHANGELOG_GITHUB_RELEASE # (true,false) if set to true this will create a github release based on the latest tag [defaults to false]
CHANGELOG_GITHUB_TOKEN # (ght_xxx) the github PAT used for creating the release (requires write permission on the repository level)
CHANGELOG_JIRA_APP_NAME # (myapp) the jira app name used for generating issue urls [optional]
CHANGELOG_JIRA_EMAIL # (user@example.com) email for JIRA API authentication - required with JIRA_TOKEN to validate tickets [optional]
CHANGELOG_JIRA_TOKEN # JIRA API token for validating that tickets exist - filters out invalid tickets when provided with JIRA_EMAIL [optional]
CHANGELOG_JIRA_PROJECT_BLACKLIST # Comma-separated JIRA project keys (e.g. CUST,PARTNER) to skip when commenting on JIRA; other tickets in the release are unaffected [optional]
CHANGELOG_VERSION_MODE # (SemVer,DateVer) what type of tagging format is being used defaults to DateVer
CHANGELOG_OUTPUT # (console,slack) where should the CLI output [defaults to console]
CHANGELOG_SLACK_TOKEN # If the output is set to slack then a slack app token should be entered here [only required if output is set to slack]
CHANGELOG_SLACK_CHANNEL_NAME # Slack channel ID (e.g., C02PDBL6GAU) or channel name (e.g., #my-channel) where the CLI should output to. Channel IDs are recommended.
CHANGELOG_SLACK_CHANNELS # Comma-separated list of Slack channel IDs (e.g., C02PDBL6GAU,C03ABCDEFGH) or channel names. Channel IDs are recommended.
CHANGELOG_JOB_URL # URL to the CI/CD job that triggered this changelog generation (shown in Slack metadata) [optional]
CHANGELOG_TRIGGERED_BY # GitHub username of the person who triggered the job (shown in Slack metadata) [optional]
CHANGELOG_STAGE # Deployment stage/environment (e.g., dev, staging, production) (shown in Slack metadata) [optional]
CHANGELOG_DOCKER_IMAGE # Docker image repository URL (e.g., 077199819609.dkr.ecr.eu-west-1.amazonaws.com/geo-production) (shown in Slack metadata) [optional]
CHANGELOG_IMAGE_TAG # Current Docker image tag being deployed (e.g., commit SHA) (shown in Slack metadata) [optional]
CHANGELOG_PREVIOUS_IMAGE_TAG # Previous Docker image tag for rollback reference (shown in Slack metadata) [optional]
CHANGELOG_DEPLOYMENTS # JSON array of systems deployed in this release (see "Deployment metadata" below). Typically the deploy pipeline's rollout-wait output [optional]
CHANGELOG_MONITORING_URLS # Comma-separated list of dashboard/monitoring URLs for the release notification. Each entry is a bare URL or 'Label|https://url' [optional]
CHANGELOG_RELEASE_NOTIFY_CHANNEL # Slack channel ID or name to post a release notification to, tagging PR authors/approvers and linking the monitoring URLs [optional, requires CHANGELOG_SLACK_TOKEN]
```

At least one of `CHANGELOG_SLACK_CHANNEL_NAME` and `CHANGELOG_SLACK_CHANNELS` is required if output is set to `slack`

### Deployment metadata

`CHANGELOG_DEPLOYMENTS` accepts a JSON array describing the systems deployed in this release —
typically piped straight from the deploy pipeline's rollout wait. Each entry:

| Field | Required | Format | Example |
|---|---|---|---|
| `name` | yes | display name | `hub` |
| `revision` | no | git commit SHA (full or short) | `80aad1c` |
| `start` | no | ISO 8601 UTC timestamp | `2026-08-26T08:19:18Z` |
| `end` | no | ISO 8601 UTC timestamp | `2026-08-26T08:30:59Z` |
| `status` | no | rollout health | `healthy` |
| `url` | no | link (e.g. ArgoCD app) | `https://argocd.monta.app/applications/argocd/frontend-hub-production` |

Unknown fields are ignored and malformed JSON is dropped — it never fails the changelog.
Non-ISO `start`/`end` values are shown verbatim rather than formatted.

```shell
CHANGELOG_DEPLOYMENTS='[{"name":"hub","revision":"80aad1c","start":"2026-08-26T08:19:18Z","end":"2026-08-26T08:30:59Z","status":"healthy","url":"https://argocd.monta.app/applications/argocd/frontend-hub-production"}]'
```

### Release Notifications

Independently of `CHANGELOG_OUTPUT`, you can have the CLI post a short Slack message announcing the release,
listing dashboards to keep an eye on, and tagging the people who authored or approved the pull requests included
in it:

```shell
CHANGELOG_RELEASE_NOTIFY_CHANNEL=C02PDBL6GAU
CHANGELOG_SLACK_TOKEN=xoxb-...
CHANGELOG_MONITORING_URLS="Grafana|https://grafana.example.com/d/abc,Sentry|https://sentry.example.com/my-project"
```

The message looks roughly like:

```
Release <link to the GitHub release> is out 🚀
Dashboards to monitor:
• Grafana
• Sentry
Contributors:
• @alice #123 #124
• @bob (approver) #125
```

Contributors are tagged with a real Slack mention (`@user`) when their public GitHub email matches a Slack
account; otherwise they're linked to their GitHub profile instead. Anyone who only approved a pull request
(and didn't author one in this release) is suffixed with `(approver)`. Requires `CHANGELOG_GITHUB_TOKEN` to
resolve PR authors/approvers.

### How to Release This Project

**This project uses SemVer versioning and automated releases.**

When you merge a PR to the `main` branch, the release workflow automatically:
1. ✅ Increments the patch version (e.g., `v1.8.2` → `v1.8.3`)
2. ✅ Creates and pushes the new tag
3. ✅ Builds native binaries for x64 and ARM64 architectures
4. ✅ Generates the changelog using conventional commits
5. ✅ Creates a GitHub release with both binaries attached
6. ✅ Posts the changelog to Slack

**Manual release:**
You can trigger a release manually via GitHub Actions:
1. Go to **Actions** → "Release new version" → "Run workflow"
2. Select version bump type:
   - **patch** (v1.8.3 → v1.8.4) - Bug fixes, small updates
   - **minor** (v1.8.3 → v1.9.0) - New features, backwards compatible
   - **major** (v1.8.3 → v2.0.0) - Breaking changes

**Manual version bump before merging PR:**
For major or minor version changes, create and push the tag before merging the PR:
```bash
# Get the latest tag
LATEST_TAG=$(git tag --sort=-version:refname | head -n 1)
echo "Latest tag: $LATEST_TAG"

# Create the new tag (e.g., for minor version bump)
NEW_VERSION="v1.9.0"  # Update this with your desired version
git tag "$NEW_VERSION" -m "Release $NEW_VERSION" -a

# Push the tag
git push origin "$NEW_VERSION"

# Now merge the PR
```
Then merge your PR. The existing tag will be picked up by the release workflow.

**After a release:**
- The [action repository](https://github.com/monta-app/changelog-cli-action) is automatically updated via PR
- The automation workflow creates a PR to update the action to use the new version
- Review and merge the PR in the action repository to publish the update

**Architecture support:**
Each release includes binaries for both Linux architectures:
- `changelog-cli-x64` - For Linux x86_64 systems
- `changelog-cli-arm64` - For Linux ARM64 systems (e.g., AWS Graviton, Raspberry Pi, Apple Silicon in Linux VMs)

Download the appropriate binary for your architecture from the [releases page](https://github.com/monta-app/changelog-cli/releases).

---

### Development & Testing

#### Running Tests

Run the test suite using:
```bash
./gradlew allTests
```

All tests use [Kotest](https://kotest.io/) and cover critical functionality like:
- Link resolver validation (PR and JIRA ticket filtering)
- Merge commit detection
- Commit parsing and mapping
- Tag sorting

#### Code Coverage

This project uses [Kover](https://github.com/Kotlin/kotlinx-kover) for code coverage tracking.

**Generate coverage reports:**
```bash
./gradlew coverage
```

This will:
1. Run all tests
2. Generate HTML coverage report: `build/reports/kover/html/index.html`
3. Generate XML coverage report: `build/reports/kover/report.xml`

**Note:** Kover currently has limited support for Kotlin/Native targets. Coverage reports may show "No sources" until JVM test targets are added or Kover improves Native support. The infrastructure is in place for when this limitation is resolved.

**Available coverage tasks:**
- `./gradlew koverHtmlReport` - Generate HTML coverage report
- `./gradlew koverXmlReport` - Generate XML coverage report
- `./gradlew koverLog` - Print coverage to console
- `./gradlew koverVerify` - Verify coverage meets minimum threshold (50%)

---

### How to Use This Tool (for other projects)

Create a tag with the following format `YYYY-DD-MM-HH-MM` and tag the commit you want to release up to.

_Note: please be sure to used [annotated tags](https://git-scm.com/book/en/v2/Git-Basics-Tagging) when tagging your
releases_


> **Define a git alias to do your tagging!**
> 
> Do this in your shell:
> ```
> git config --global alias.release '!git tag "`date -u +%Y-%m-%d-%H-%M`" -m "Release `date -u +%Y-%m-%d\ %H:%M\ UTC`" -a'
> ```
> This will add a global git alias, so that you can simply do `git release` to create your annotated, correctly formatted release tag! 


In a situation where you're adding a tag to a fresh project (where there is no tags) then the plugin will look at the
commit associated with
the first tag you created and then the last commit in the repository and create a change log based on that

In a situation where you're adding a tag to a repository that already has tags, then it will just look at the 2 latest
tags and create a change log with all the commits between those two tags


### Example Tag (on main branch)

```bash
# checkout the main branch
git checkout main

# use the git alias from above to create your release tag
git release

# push the tag
git push --follow-tags
```

### Example GitHub Action Step

```yaml
  create-change-log:
    needs: build
    name: Create and publish change log
    runs-on: self-hosted
    timeout-minutes: 5
    steps:
      - name: Run changelog cli action
        uses: monta-app/changelog-cli-action@main
        with:
          service-name: "My cool service" # Name that appears in slack message
          github-release: true # Should it create a github release?
          github-token: ${{ secrets.GITHUB_TOKEN }} # shouldn't need to change this
          jira-app-name: "montaapp" # Name of the Jira app (used for linking issues)
          version-mode: "DateVer" # version of the tag format you're using
          output: "slack" # Don't change this if you want to log to slack
          slack-token: ${{ secrets.SLACK_APP_TOKEN }} # Slack APP token
          slack-channel: "#info-releases" # Channel to print changelog to
          stage: "production" # Deployment stage (optional)
          docker-image: "123456789.dkr.ecr.us-east-1.amazonaws.com/my-service" # Docker image repository (optional)
          image-tag: ${{ github.sha }} # Current image tag being deployed (optional)
          previous-image-tag: ${{ steps.get-previous-tag.outputs.tag }} # Previous tag for rollback (optional)
```

### Example Release

```Markdown
Release 2022-03-21-13-00

*🚀 Feature*
• added support for reading a cps voltage (#115)
• added support for meter source, so now only data from the configured meter will be allowed into a charge point

*🐛 Fix*
• site meter controller - PUT -> POST - unit-test • site meter controller - PUT -> POST • explicit G1 GC selection (
#114)
• actually fixed failing tests • fixed failing tests • site meter update endpoint now accepts both the uuid and integer
value • added
missing places where meter source should be placed • start period should never be negative in the charging schedule

*🧹 Chore*
• removed ratelimit noise (#112)
```
