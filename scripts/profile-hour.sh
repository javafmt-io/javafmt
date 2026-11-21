#!/usr/bin/env zsh
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)

open_newest() {
    local newest
    newest=$(ls -1dt "$ROOT"/profiles/*/ 2>/dev/null | head -1 || true)
    [[ -n "$newest" ]] || { echo "no profiles/ run yet" >&2; exit 1; }
    local html
    html=$(find "$newest" -name '*.html' | sort | head -1)
    [[ -n "$html" ]] || { echo "no flamegraph under $newest" >&2; exit 1; }
    echo "flamegraph: $html"
    open "$html"
}

if [[ "${1:-}" == "--open" ]]; then
    open_newest
    exit 0
fi

MINUTES=${MINUTES:-60}
ITERATION_SECONDS=10
FILE_PARAMS=2
WARMUP_ITERATIONS=1
OVERHEAD_SECONDS=60
ITERATIONS=$(( (MINUTES * 60 - OVERHEAD_SECONDS) / FILE_PARAMS / ITERATION_SECONDS - WARMUP_ITERATIONS ))

if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk env >/dev/null || true
    set -u
fi

if [[ "$(uname)" == "Darwin" && ! -f /opt/homebrew/opt/async-profiler/lib/libasyncProfiler.dylib ]]; then
    brew install async-profiler
fi

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$ROOT/profiles/$STAMP"
mkdir -p "$OUT"
echo "running $MINUTES min: $ITERATIONS x ${ITERATION_SECONDS}s per file; results in $OUT"

rm -rf "$ROOT/javafmt-core/build/jmh-async"
caffeinate -dimsu gradle :javafmt-core:jmh \
    -PjmhProfile=async \
    -PjmhInclude='JdkSourceBenchmark.*' \
    -PjmhIterations="$ITERATIONS" \
    -PjmhIterationSeconds="$ITERATION_SECONDS" \
    2>&1 | tee "$OUT/run.log"

cp -R "$ROOT/javafmt-core/build/jmh-async/." "$OUT/"
cp "$ROOT/javafmt-core/build/reports/jmh/results.json" "$OUT/results.json"
echo "done: $OUT"
open_newest
