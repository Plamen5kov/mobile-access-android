#!/usr/bin/env bash
set -euo pipefail

# Release script for Terminal app
# Usage: ./scripts/release.sh [major|minor|patch]
# Default: patch

PROPS_FILE="gradle.properties"
BUMP_TYPE="${1:-patch}"

# Read current version
CURRENT=$(grep "appVersionName=" "$PROPS_FILE" | cut -d= -f2)
if [[ -z "$CURRENT" ]]; then
    echo "ERROR: Could not read appVersionName from $PROPS_FILE"
    exit 1
fi

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT"

# Calculate new version
case "$BUMP_TYPE" in
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    patch) PATCH=$((PATCH + 1)) ;;
    *)
        echo "Usage: $0 [major|minor|patch]"
        echo "  major: $CURRENT -> $((MAJOR+1)).0.0"
        echo "  minor: $CURRENT -> $MAJOR.$((MINOR+1)).0"
        echo "  patch: $CURRENT -> $MAJOR.$MINOR.$((PATCH+1))"
        exit 1
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
TAG="v$NEW_VERSION"

echo ""
echo "  Current version: $CURRENT"
echo "  New version:     $NEW_VERSION"
echo "  Tag:             $TAG"
echo "  Bump type:       $BUMP_TYPE"
echo ""

# Check for uncommitted changes
if [[ -n $(git status --porcelain) ]]; then
    echo "ERROR: You have uncommitted changes. Commit or stash them first."
    exit 1
fi

# Check tag doesn't already exist
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "ERROR: Tag $TAG already exists."
    exit 1
fi

# Confirm
read -p "  Release $TAG? [y/N] " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Update version in gradle.properties
sed -i "s/appVersionName=$CURRENT/appVersionName=$NEW_VERSION/" "$PROPS_FILE"

# Commit, tag, push
git add "$PROPS_FILE"
git commit -m "chore: bump version to $NEW_VERSION"
git tag "$TAG"
git push
git push origin "$TAG"

echo ""
echo "  Released $TAG"
echo "  CI is building: https://github.com/Plamen5kov/mobile-access-android/actions"
echo "  Release will appear at: https://github.com/Plamen5kov/mobile-access-android/releases/tag/$TAG"
echo ""
