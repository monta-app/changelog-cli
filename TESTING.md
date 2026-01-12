# Local Testing Guide

This guide explains how to test changelog-cli locally with real credentials.

## Quick Start

1. **Copy the example environment file:**
   ```bash
   cp .env.example .env
   ```

2. **Fill in your credentials in `.env`:**
   - Get Slack token from Slack App settings
   - Get GitHub token from GitHub Settings → Developer settings → Personal access tokens
   - Get JIRA API token from https://id.atlassian.com/manage-profile/security/api-tokens

3. **Run the test script:**
   ```bash
   ./test-local.sh
   ```

That's it! The script will:
- Load credentials from `.env`
- Auto-detect version mode (SemVer or DateVer) from git tags
- Auto-detect service name from repository directory
- Run changelog-cli against the test repository
- Post results to Slack

## Testing Different Repositories

Pass the repository path as the first argument:

```bash
# Test wallet-service repository
./test-local.sh ../wallet-service

# Test with specific tag range on wallet-service
./test-local.sh ../wallet-service --from-tag=2026-01-08-11-30 --to-tag=2026-01-08-14-28

# Test another repo with console output
./test-local.sh ../my-other-repo --output=console
```

## Passing Additional Parameters

The script accepts any additional parameters and forwards them to changelog-cli:

```bash
# Override output mode (uses default repo)
./test-local.sh --output=console

# Specify a specific tag range
./test-local.sh --from-tag=v2.4.0 --to-tag=v2.5.0

# Override service name
./test-local.sh --service-name='Wallet Service'

# Combine repository path with overrides
./test-local.sh ../wallet-service --output=console --service-name='Custom Name'
```

## Auto-Detection

The script automatically detects:

- **Version Mode**: Analyzes git tags to determine SemVer or DateVer
  - Tags like `v1.2.3` → SemVer
  - Tags like `2026-01-08-11-30` → DateVer

- **Service Name**: Derived from repository directory name
  - `ocpp-emulator` → `Ocpp Emulator`
  - `wallet-service` → `Wallet Service`

- **GitHub User**: Uses `gh api user` to get current authenticated user

## Manual Testing

If you prefer to run commands manually:

```bash
# Load environment variables
export $(grep -v '^#' .env | xargs)

# Build the binary
./gradlew linkReleaseExecutableHost
cp build/bin/host/releaseExecutable/changelog-cli.kexe changelog-cli

# Run against test repository
cd $TEST_REPO_PATH
../changelog-cli/changelog-cli \
  --service-name='OCPP Emulator' \
  --jira-app-name="$JIRA_APP_NAME" \
  --jira-email="$JIRA_EMAIL" \
  --jira-token="$JIRA_API_TOKEN" \
  --version-mode='SemVer' \
  --output=console \
  --github-token="$GH_READ"
```

## Testing Without JIRA Validation

To test without JIRA validation (faster), omit the `--jira-email` and `--jira-token` flags:

```bash
cd $TEST_REPO_PATH
../changelog-cli/changelog-cli \
  --service-name='OCPP Emulator' \
  --jira-app-name="$JIRA_APP_NAME" \
  --version-mode='SemVer' \
  --output=console \
  --github-token="$GH_READ"
```

## Output Options

Change the `--output` flag to test different outputs:

- `--output=console` - Print to terminal
- `--output=slack` - Post to Slack channel
- `--output=slack-json` - Output Slack JSON blocks

## Security

- ⚠️ **Never commit `.env` file** - It contains secrets!
- ✅ `.env` is gitignored automatically
- ✅ `.env.example` is safe to commit (no secrets)
- ✅ Use `.env.example` as a template for others
