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
- `./gradlew linkDebugExecutableCommon` - Build debug executable
- `./changelog-cli --help` - Run the built CLI to see all options

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