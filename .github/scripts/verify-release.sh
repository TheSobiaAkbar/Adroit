#!/usr/bin/env bash
# Usage: verify-release.sh <tag> <release-notes-output>
# Fails unless <tag> is exactly "v" + companyAdmob.version, that version isn't a snapshot, and CHANGELOG.md has a
# "## [x.y.z]" section for it. Writes that section's body to <release-notes-output>. Used by release.yml.
set -euo pipefail

tag="${1:?tag}"
notes="${2:?release notes output path}"

version=$(grep -E '^companyAdmob\.version=' gradle.properties | cut -d= -f2- | tr -d '[:space:]')
if [[ -z "$version" ]]; then
  echo "::error::companyAdmob.version is missing from gradle.properties"
  exit 1
fi
if [[ "$version" == *-SNAPSHOT ]]; then
  echo "::error::companyAdmob.version is $version: a release needs a non-snapshot version"
  exit 1
fi
if [[ "$tag" != "v$version" ]]; then
  echo "::error::Tag $tag doesn't match companyAdmob.version $version (expected v$version)"
  exit 1
fi
if [[ ! -f CHANGELOG.md ]]; then
  echo "::error::CHANGELOG.md is missing"
  exit 1
fi

# The lines after "## [version]" up to the next "## [" heading.
awk -v heading="## [$version]" '
  index($0, heading) == 1 { found = 1; next }
  found && /^## \[/ { exit }
  found { print }
' CHANGELOG.md > "$notes"

if ! grep -q "^## \[$version\]" CHANGELOG.md; then
  echo "::error::CHANGELOG.md has no '## [$version]' section"
  exit 1
fi
if [[ ! -s "$notes" ]] || ! grep -q '[^[:space:]]' "$notes"; then
  echo "::error::The CHANGELOG.md section for $version is empty"
  exit 1
fi
echo "Release $version: tag, version and CHANGELOG section verified."
