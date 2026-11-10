#!/usr/bin/env bash
# Extracts the useful part of a failed Gradle log.
#
# Writes `$RUNNER_TEMP/gradle-failure-summary.txt` (consumed by the next step, which publishes it
# as a check run) and emits the top compiler diagnostics as workflow `::error::` annotations, so a
# red build can be triaged from the run page or from the REST API alone.
#
# A `--stacktrace` Gradle log is tens of thousands of lines; the Kotlin compiler's diagnostics and
# the failing task names are the part that matters.
set -uo pipefail

LOG="${1:-}"
OUT="${RUNNER_TEMP:-/tmp}/gradle-failure-summary.txt"

PATTERN='^e: |^error: |FAILURE: |What went wrong|^> Task .*FAILED$|^Execution failed for task|Caused by: | FAILED$|tests? completed, .* failed|A problem occurred'

if [[ -z "$LOG" || ! -f "$LOG" ]]; then
  echo "No Gradle log was produced." >"$OUT"
  exit 0
fi

grep -E "$PATTERN" "$LOG" | head -n 300 >"$OUT" || true
if [[ ! -s "$OUT" ]]; then
  tail -n 200 "$LOG" >"$OUT"
fi

# Workflow-command escaping: % , \r and \n are meaningful inside ::error::.
escape() {
  printf '%s' "$1" | sed -e 's/%/%25/g' -e 's/\r/%0D/g' -e 's/\n/%0A/g'
}

COUNT=0
while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  echo "::error::$(escape "$line")"
  COUNT=$((COUNT + 1))
  if [[ "$COUNT" -ge 40 ]]; then break; fi
done <"$OUT"

# Diagnostics are best-effort; never turn a summarizer quirk into a second job failure.
exit 0
