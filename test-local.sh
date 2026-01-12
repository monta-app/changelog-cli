#!/bin/bash
set -e

# Usage information
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Usage: $0 [repository_path] [additional options for changelog-cli]"
    echo ""
    echo "Arguments:"
    echo "  repository_path   Path to the repository to test (default: ../ocpp-emulator)"
    echo ""
    echo "This script auto-detects repository settings and passes them to changelog-cli."
    echo "You can override any auto-detected values or add extra options."
    echo ""
    echo "Examples:"
    echo "  $0                                         # Use default repo (../ocpp-emulator)"
    echo "  $0 ../wallet-service                       # Test wallet-service repo"
    echo "  $0 ../ocpp-emulator --output=console       # Override output mode"
    echo "  $0 ../wallet-service --from-tag=2026-01-08-11-30 --to-tag=2026-01-08-14-28"
    echo "  $0 --service-name='My Service'             # Use default repo, override service name"
    echo ""
    exit 0
fi

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

# Parse repository path from first argument if it doesn't start with --
if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
    TEST_REPO_PATH="$1"
    shift  # Remove first argument so remaining args are passed to changelog-cli
else
    # Default to ocpp-emulator if not provided
    TEST_REPO_PATH=${TEST_REPO_PATH:-../ocpp-emulator}
fi

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

# Show additional parameters if provided
if [ $# -gt 0 ]; then
    echo "Additional parameters: $*"
fi
echo ""

# Build the base command with auto-detected values
CMD=(
    ../changelog-cli/changelog-cli
    --service-name="$SERVICE_NAME"
    --jira-app-name="$JIRA_APP_NAME"
    --jira-email="$JIRA_EMAIL"
    --jira-token="$JIRA_API_TOKEN"
    --version-mode="$VERSION_MODE"
    --output=slack
    --slack-channels="$SLACK_CHANNEL"
    --slack-token="$SLACK_TOKEN"
    --github-token="$GH_READ"
    --triggered-by="$GITHUB_USER"
)

# Add any additional parameters passed to this script
CMD+=("$@")

# Run the command
"${CMD[@]}"

echo ""
echo "Test completed!"
