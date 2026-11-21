#!/usr/bin/env zsh
set -euo pipefail

[[ "$(uname)" == "Darwin" ]] || { echo "profile-hour.sh needs macOS (caffeinate, open)" >&2; exit 1; }

cd "$(dirname "$0")/.."
ROOT=$(pwd)

open_newest() {
    local runs=( "$ROOT"/profiles/*(/Nom) )
    (( ${#runs} > 0 )) || { echo "no profiles/ run yet" >&2; exit 1; }
    open_flamegraph "${runs[1]}"
}

open_flamegraph() {
    local newest=$1
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
FILE_PARAMS=$(grep -m1 '@Param' "$ROOT/javafmt-core/src/jmh/java/io/javafmt/JdkSourceBenchmark.java" | grep -o '"[^"]*"' | wc -l | tr -d ' ')
(( FILE_PARAMS >= 1 )) || { echo "could not count the @Param files in JdkSourceBenchmark" >&2; exit 1; }
WARMUP_ITERATIONS=1
OVERHEAD_SECONDS=60
ITERATIONS=$(( (MINUTES * 60 - OVERHEAD_SECONDS) / FILE_PARAMS / ITERATION_SECONDS - WARMUP_ITERATIONS ))
(( ITERATIONS >= 1 )) || { echo "MINUTES=$MINUTES is too short: need at least $(( (OVERHEAD_SECONDS + FILE_PARAMS * ITERATION_SECONDS * (WARMUP_ITERATIONS + 1)) / 60 + 1 )) minutes" >&2; exit 1; }

if [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk env >/dev/null || true
    set -u
fi

ASYNC_LIB=/opt/homebrew/opt/async-profiler/lib/libasyncProfiler.dylib
if command -v brew >/dev/null; then
    brew --prefix async-profiler >/dev/null 2>&1 || brew install async-profiler
    ASYNC_LIB="$(brew --prefix async-profiler)/lib/libasyncProfiler.dylib"
fi

STAMP=$(date +%Y%m%d-%H%M%S)
OUT="$ROOT/profiles/$STAMP"
mkdir -p "$OUT"
echo "running $MINUTES min: $ITERATIONS x ${ITERATION_SECONDS}s per file; results in $OUT"

rm -rf "$ROOT/javafmt-core/build/jmh-async"
caffeinate -dimsu gradle :javafmt-core:jmh \
    -PjmhProfile=async \
    -PasyncProfilerLib="$ASYNC_LIB" \
    -PjmhInclude='JdkSourceBenchmark.*' \
    -PjmhIterations="$ITERATIONS" \
    -PjmhIterationSeconds="$ITERATION_SECONDS" \
    2>&1 | tee "$OUT/run.log"

cp -R "$ROOT/javafmt-core/build/jmh-async/." "$OUT/"
cp "$ROOT/javafmt-core/build/reports/jmh/results.json" "$OUT/results.json"
echo "done: $OUT"
open_flamegraph "$OUT"
