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

# Change to test repository directory
cd "$TEST_REPO_PATH"

# Detect version mode based on existing tags
# Check the latest tag to determine if using SemVer or DateVer
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -z "$LATEST_TAG" ]; then
    echo "Warning: No tags found in repository. Defaulting to DateVer."
    VERSION_MODE="DateVer"
elif [[ "$LATEST_TAG" =~ ^v?[0-9]+\.[0-9]+\.[0-9]+ ]]; then
    # Matches semver pattern (v1.2.3 or 1.2.3)
    VERSION_MODE="SemVer"
else
    # Assume DateVer format
    VERSION_MODE="DateVer"
fi

# Extract service name from repository directory name
SERVICE_NAME=$(basename "$PWD" | sed 's/-/ /g' | awk '{for(i=1;i<=NF;i++)sub(/./,toupper(substr($i,1,1)),$i)}1')

echo "Testing changelog-cli locally..."
echo "Repository: $TEST_REPO_PATH"
echo "Service: $SERVICE_NAME"
echo "Version mode: $VERSION_MODE (detected from tags)"
echo "Triggered by: $GITHUB_USER"
echo ""

# Run changelog-cli with all options
../changelog-cli/changelog-cli \
  --service-name="$SERVICE_NAME" \
  --jira-app-name="$JIRA_APP_NAME" \
  --jira-email="$JIRA_EMAIL" \
  --jira-token="$JIRA_API_TOKEN" \
  --version-mode="$VERSION_MODE" \
  --output=slack \
  --slack-channels="$SLACK_CHANNEL" \
  --slack-token="$SLACK_TOKEN" \
  --github-token="$GH_READ" \
  --triggered-by="$GITHUB_USER"

echo ""
echo "Test completed!"
