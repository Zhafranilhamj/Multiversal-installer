#!/usr/bin/env bash
# Writes the useful part of a failed Gradle log to the job summary.
#
# A `--stacktrace` Gradle log is tens of thousands of lines and the raw runner log is awkward to
# search from a phone or from an API-only environment. The Kotlin compiler's diagnostics and the
# failing task names are the part anyone triaging a red build actually needs, so they are lifted
# into the summary; the full log still ships as an artifact from the next step.
set -uo pipefail

LOG="${1:-}"

if [[ -z "$LOG" || ! -f "$LOG" ]]; then
  echo "No Gradle log was produced." >>"${GITHUB_STEP_SUMMARY:-/dev/null}"
  exit 0
fi

PATTERN='^e: |^error: |^w: .*[Ee]rror|FAILURE: |What went wrong|^> Task .*FAILED$|^Execution failed for task|Caused by: |FAILED$|tests? completed, .* failed'

{
  echo '### Gradle failure summary'
  echo
  echo '```'
  grep -E "$PATTERN" "$LOG" | head -n 250
  echo '```'
  echo
  echo "Full log: the \`*-gradle-log\` artifact of this run."
} >>"${GITHUB_STEP_SUMMARY:-/dev/null}"
