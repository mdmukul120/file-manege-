#!/usr/bin/env bash
# Usage: ./push-to-github.sh [repo-name]
# Needs: git and GitHub CLI (gh) logged in via `gh auth login`.
set -e
NAME="${1:-ai-file-organizer}"
git init -b main 2>/dev/null || true
git add -A
git commit -m "AI File Organizer" || true
gh repo create "$NAME" --public --source . --push
echo "Done. Open the Actions tab; the APK appears under Releases in ~10 minutes."
