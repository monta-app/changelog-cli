#!/bin/bash
set -e

# Load environment variables from .env file
if [ -f .env ]; then
    export $(grep -v '^#' .env | xargs)
else
    echo "Error: .env file not found. Copy .env.example to .env and fill in your credentials."
    exit 1
fi

# Get current GitHub user
GITHUB_USER=$(gh api user -q .login 2>/dev/null || echo "")
if [ -z "$GITHUB_USER" ]; then
    echo "Warning: Could not get GitHub user from gh cli. Make sure you're authenticated with 'gh auth login'"
    GITHUB_USER="unknown"
fi

# Default to ocpp-emulator if TEST_REPO_PATH not set
TEST_REPO_PATH=${TEST_REPO_PATH:-../ocpp-emulator}

echo "Testing changelog-cli with JIRA validation..."
echo "Repository: $TEST_REPO_PATH"
echo "Triggered by: $GITHUB_USER"
echo ""

# Change to test repository directory
cd "$TEST_REPO_PATH"

# Run changelog-cli with all options
../changelog-cli/changelog-cli \
  --service-name='OCPP Emulator' \
  --jira-app-name="$JIRA_APP_NAME" \
  --jira-email="$JIRA_EMAIL" \
  --jira-token="$JIRA_API_TOKEN" \
  --version-mode='SemVer' \
  --output=slack \
  --slack-channels="$SLACK_CHANNEL" \
  --slack-token="$SLACK_TOKEN" \
  --github-token="$GH_READ" \
  --triggered-by="$GITHUB_USER"

echo ""
echo "Test completed!"
