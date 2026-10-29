#!/usr/bin/env python3
"""Compare a JMH run against a committed baseline and write a GitHub step summary.

Reports rather than gates. JMH numbers on shared CI runners move around by 10-20% between runs
for reasons that have nothing to do with the code, so the failure threshold is set well above
that: only a regression large enough to be a real change trips it. Everything else is reported
in the summary for a human to read the trend from.
"""

import json
import os
import sys

# A benchmark has to get this much slower than baseline before the job fails. Chosen to sit
# clearly above observed runner noise; tighten it only if the scheduled runs prove quieter.
FAIL_THRESHOLD = 1.50
WARN_THRESHOLD = 1.20


def key(entry):
    params = entry.get("params", {})
    suffix = ",".join(f"{k}={v}" for k, v in sorted(params.items()))
    name = entry["benchmark"].rsplit(".", 1)[-1]
    return f"{name}[{suffix}]" if suffix else name


def scores(path):
    with open(path) as handle:
        return {key(entry): entry["primaryMetric"]["score"] for entry in json.load(handle)}


def main():
    current_path, baseline_path = sys.argv[1], sys.argv[2]
    current = scores(current_path)

    if not os.path.exists(baseline_path):
        write_summary(["No baseline committed yet. Current numbers:", ""] + [f"- `{k}`: {v:.3f}" for k, v in sorted(current.items())])
        return 0

    baseline = scores(baseline_path)
    rows, regressions = [], []
    for name in sorted(current):
        now = current[name]
        before = baseline.get(name)
        if before is None:
            rows.append(f"| `{name}` | — | {now:.3f} | new |")
            continue
        ratio = now / before if before else 1.0
        verdict = "ok"
        if ratio >= FAIL_THRESHOLD:
            verdict = "**REGRESSED**"
            regressions.append(f"{name}: {before:.3f} -> {now:.3f} ({ratio:.2f}x)")
        elif ratio >= WARN_THRESHOLD:
            verdict = "slower"
        elif ratio <= 1 / WARN_THRESHOLD:
            verdict = "faster"
        rows.append(f"| `{name}` | {before:.3f} | {now:.3f} | {ratio:.2f}x {verdict} |")

    gone = sorted(set(baseline) - set(current))
    lines = ["## JMH vs baseline", "", "| benchmark | baseline | current | change |", "|---|---|---|---|"] + rows
    if gone:
        lines += ["", "Benchmarks in the baseline but not in this run: " + ", ".join(f"`{g}`" for g in gone)]
    lines += ["", f"Fails above {FAIL_THRESHOLD:.2f}x; anything smaller is within CI runner noise."]
    write_summary(lines)

    if regressions:
        print("Regressions:\n  " + "\n  ".join(regressions), file=sys.stderr)
        return 1
    return 0


def write_summary(lines):
    text = "\n".join(lines) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as handle:
            handle.write(text)


if __name__ == "__main__":
    sys.exit(main())
