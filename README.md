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
CHANGELOG_VERSION_MODE # (SemVer,DateVer) what type of tagging format is being used defaults to DateVer
CHANGELOG_OUTPUT # (console,slack) where should the CLI output [defaults to console]
CHANGELOG_SLACK_TOKEN # If the output is set to slack then a slack app token should be entered here [only required if output is set to slack]
CHANGELOG_SLACK_CHANNEL_NAME # Slack channel ID (e.g., C02PDBL6GAU) or channel name (e.g., #my-channel) where the CLI should output to. Channel IDs are recommended.
CHANGELOG_SLACK_CHANNELS # Comma-separated list of Slack channel IDs (e.g., C02PDBL6GAU,C03ABCDEFGH) or channel names. Channel IDs are recommended.
```

At least one of `CHANGELOG_SLACK_CHANNEL_NAME` and `CHANGELOG_SLACK_CHANNELS` is required if output is set to `slack`

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
          slack-channel: "#releases" # Channel to print changelog to
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
