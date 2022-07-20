### About

A CLI project dedicated to generating a change log from commits the concept is simple:
you take the last two tags, and you get all the commits in between you parse the commits based on a system known as "Conventional Commits"
and you get a nice formatted ChangeLog for people to read!

### Usage

Please run the following command, and you will get a list of all the options with relevant information on how to use them

`./changelog-cli --help`

### Env Vars

Instead of using the CLI for entering values it's also possible to just setup environment variables (useful for GitHub actions)

```shell
CHANGELOG_GITHUB_RELEASE # (true,false) if set to true this will create a github release based on the latest tag [defaults to false]
CHANGELOG_GITHUB_TOKEN # (ght_xxx) the github PAT used for creating the release (requires write permission on the repository level) 
CHANGELOG_JIRA_APP_NAME # (myapp) the jira app name used for generating issue urls [optional]
CHANGELOG_OUTPUT # (console,slack) where should the CLI output [defaults to console]
CHANGELOG_SLACK_TOKEN # If the output is set to slack then a slack app token should be entered here [only required if output is set to slack]
CHANGELOG_SLACK_CHANNEL_NAME # the channel where the cli should be outputting to
CHANGELOG_SLACK_CHANNELS # Comma-separated list of Slack channels where the changelog will be posted.
```

At least one of `CHANGELOG_SLACK_CHANNEL_NAME` and `CHANGELOG_SLACK_CHANNELS` is required if output is set to `slack`

### How to use

Create a tag with the following format `YYYY-DD-MM-HH-MM` and tag the commit you want to release up to

In a situation where you're adding a tag to a fresh project (where there is no tags) then the plugin will look at the commit associated with
the first tag you created and then the last commit in the repository and create a change log based on that

In a situation where you're adding a tag to a repository that already has tags, then it will just look at the 2 latest tags and create a
change log with all the commits between those two tags

_Note: please be sure to used [annotated tags](https://git-scm.com/book/en/v2/Git-Basics-Tagging) when tagging your releases_

### Example Tag (on main branch)

```bash
# checkout the main branch
git checkout main

# create the annotated tag, with a message
git tag -a 2022-04-21-14-45 -m "Release 2022-04-21 14-45"

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
• site meter controller - PUT -> POST - unit-test • site meter controller - PUT -> POST • explicit G1 GC selection (#114)
• actually fixed failing tests • fixed failing tests • site meter update endpoint now accepts both the uuid and integer value • added
missing places where meter source should be placed • start period should never be negative in the charging schedule

*🧹 Chore*
• removed ratelimit noise (#112)
```
