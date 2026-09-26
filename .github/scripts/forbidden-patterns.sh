#!/usr/bin/env bash
# Fails on source patterns the libraries must never contain. Run from the repository root; used by ci.yml.
# Needs the full git history (the old publisher ID is read from an early commit, never written down here).
set -euo pipefail

failed=0
report() {
  echo "::error::$1"
  failed=1
}

library_sources=(admob-core/src/main admob-views/src/main admob-compose/src/main admob-testing/src/main admob-firebase/src/main)

# 1. No println in library code: logging goes through AdLogger.
if matches=$(git grep -n 'println(' -- "${library_sources[@]}"); then
  report "println( in library sources:"
  echo "$matches"
fi

# 2. android.util.Log only inside the default logger implementation.
if matches=$(git grep -n 'android\.util\.Log' -- "${library_sources[@]}" ':!**/AndroidAdLogger.kt'); then
  report "android.util.Log outside AndroidAdLogger:"
  echo "$matches"
fi

# 3. Google Mobile Ads / UMP / Play Services imports only in admob-core's adapter package (ADR-003).
if matches=$(git grep -nE '^import com\.google\.android\.(libraries\.ads|ump|gms)\.' -- "${library_sources[@]}" \
  ':!admob-core/src/main/kotlin/com/company/admob/internal/google/**'); then
  report "Google SDK imports outside admob-core internal/google:"
  echo "$matches"
fi

# 4. The real publisher ID the project started with must not reappear anywhere (only Google's sample IDs and
#    placeholders are allowed). Derived from the commit that removed it, so it's never spelled out in the repo.
if ! old_manifest=$(git show 2f76e72:app/src/main/AndroidManifest.xml 2>/dev/null); then
  report "Commit 2f76e72 isn't available: check out the full history (fetch-depth: 0)."
else
  publisher=$(grep -o 'ca-app-pub-[0-9]*' <<<"$old_manifest" | head -1)
  if [[ -z "$publisher" ]]; then
    report "No publisher ID found in 2f76e72:app/src/main/AndroidManifest.xml."
  elif git grep -q -F "$publisher" -- .; then
    # File names only: the ID itself isn't printed to the log.
    report "The old real publisher ID appears in: $(git grep -l -F "$publisher" -- . | tr '\n' ' ')"
  fi
fi

if [[ $failed -ne 0 ]]; then
  exit 1
fi
echo "Forbidden patterns: none found."
