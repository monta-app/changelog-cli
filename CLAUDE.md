# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a Kotlin/Native CLI tool that generates changelogs from Git commits using Conventional Commits. The tool analyzes Git tags and commits between them to create formatted changelogs that can be output to console, Slack, or GitHub releases.

## Build and Development Commands

### Build and Test
- `./gradlew build` - Build the project and run tests
- `./gradlew commonBinaries` - Build the native executable (default task)
- `./gradlew allTests` - Run all tests
- `./gradlew commonTest` - Run tests for the common target
- `./gradlew ktlintCheck` - Run code style checks
- `./gradlew check` - Run all verification tasks (tests + linting)

### Local Development
- `./gradlew linkDebugExecutableHost` - Build debug executable
- `./gradlew linkReleaseExecutableHost` - Build release executable (recommended for testing)
- `./changelog-cli --help` - Run the built CLI to see all options

### Rebuilding the Binary After Code Changes

**CRITICAL**: After making code changes, you MUST rebuild and copy the binary before testing, or you'll be testing stale code.

The Gradle build outputs the binary to `build/bin/host/releaseExecutable/changelog-cli.kexe`, but `test-local.sh` expects it at `./changelog-cli` in the project root. The build does NOT automatically copy the binary to the root.

**Recommended workflow after code changes**:
```bash
# 1. Delete the old binary to avoid confusion
rm -f ./changelog-cli

# 2. Clean and rebuild
./gradlew clean linkReleaseExecutableHost

# 3. Copy the new binary to project root
cp build/bin/host/releaseExecutable/changelog-cli.kexe ./changelog-cli

# 4. Verify the build timestamp
./changelog-cli --version
# Should show recent build timestamp and latest commit

# 5. Now test
./test-local.sh . --from-tag=v1.0.0 --to-tag=v1.1.0
```

**Common mistake**: Running tests without rebuilding the binary after code changes. This leads to testing old code and confusion about whether changes are working.

**Quick rebuild command** (combines all steps):
```bash
rm -f ./changelog-cli && ./gradlew clean linkReleaseExecutableHost && cp build/bin/host/releaseExecutable/changelog-cli.kexe ./changelog-cli && ./changelog-cli --version
```

### Testing Locally

**IMPORTANT**: Always use `./test-local.sh` for local testing instead of running `./changelog-cli` directly.

The `test-local.sh` script:
- Automatically loads credentials from `.env` file (Slack token, JIRA credentials, GitHub token)
- Auto-detects repository settings (service name, version mode from tags)
- Provides consistent test environment across different repositories
- Saves time by not requiring manual parameter entry

**Usage Examples**:
```bash
# Test current repository (changelog-cli)
./test-local.sh

# Test another repository (most common usage)
./test-local.sh ../service-geo
./test-local.sh ../ocpp-emulator

# Test with console output instead of Slack
./test-local.sh ../service-geo --output console

# Test specific tag range
./test-local.sh ../service-geo --from-tag=2026-01-12-21-54 --to-tag=2026-01-12-22-23

# Override service name
./test-local.sh ../wallet-service --service-name="My Custom Name"
```

**Environment Setup**:
1. Copy `.env.example` to `.env` in the project root
2. Fill in required credentials:
   - `SLACK_TOKEN` - Slack bot token for posting changelogs
   - `SLACK_CHANNEL` - Slack channel ID for test posts
   - `JIRA_EMAIL` - JIRA account email for ticket validation
   - `JIRA_API_TOKEN` - JIRA API token for authentication
   - `JIRA_APP_NAME` - JIRA workspace name (e.g., "montaapp")
   - `GH_READ` - GitHub token (usually already in environment)

## Architecture

### Core Components

**Main Entry Point**: `GenerateChangeLogCommand.kt` - CLI command using Clikt library that orchestrates the changelog generation process.

**Services Layer**:
- `ChangeLogService` - Main service orchestrating the changelog generation
- `GitService` - Handles Git operations (tags, commits, repository info)
- `GitHubService` - Manages GitHub API interactions for releases

**Model Layer**:
- `VersionMode` enum - Supports SemVer and DateVer tag formats with corresponding sorters
- `ChangeLog`, `Commit`, `ConventionalCommitType` - Core data models

**Output Layer**:
- `ChangeLogPrinter` interface with implementations for Console and Slack output
- `SlackChangeLogPrinter` - Formats and posts to Slack channels
- `ConsoleChangeLogPrinter` - Outputs to console

**Utilities**:
- `LinkResolver` - Resolves Jira and GitHub issue links in commit messages
- `MarkdownFormatter` - Formats changelog content
- `TagSorter` implementations (SemVerSorter, DateVerSorter) - Handle different versioning schemes

### Key Design Patterns

The CLI uses a strategy pattern for version sorting and output formatting. Tag sorters are pluggable based on the version mode (SemVer vs DateVer), and output printers can be swapped between console and Slack.

### Configuration

The tool supports both CLI flags and environment variables (prefixed with `CHANGELOG_`). See the README for complete environment variable reference.

### Tag Format Requirements

- **DateVer**: `YYYY-MM-DD-HH-MM` format (default)
- **SemVer**: Standard semantic versioning format
- Tags must be annotated Git tags for proper functionality