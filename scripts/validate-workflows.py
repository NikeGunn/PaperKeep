#!/usr/bin/env python3
"""
Validation script for GitHub Actions workflow files.

Checks:
  1. Valid YAML (no tabs in indentation, correct structure)
  2. concurrency.cancel-in-progress = true on applicable workflows
  3. All secrets use ${{ secrets.* }} syntax — no hardcoded credentials
  4. Correct trigger conditions (on: push/pull_request with paths filters)
  5. timeout-minutes set on every job
  6. Actions pinned to specific versions (@v4, @v3 etc — not @latest or @main for
     first-party actions; third-party pinning checked separately)
  7. No hardcoded credentials in env blocks
  8. security-scan.yml has cron schedule

Exit code 0 = all checks pass. Exit code 1 = failures found.
"""
from __future__ import annotations

import re
import sys
import yaml
from pathlib import Path

WORKFLOWS_DIR = Path(__file__).parent.parent / ".github" / "workflows"

# Which workflows must have concurrency groups
MUST_HAVE_CONCURRENCY = {
    "android-ci.yml",
    "backend-ci.yml",
    "backend-deploy-staging.yml",
    "intelligence-ci.yml",
}

# Which workflows must have path filters on their triggers
MUST_HAVE_PATH_FILTERS = {
    "android-ci.yml",
    "backend-ci.yml",
    "intelligence-ci.yml",
    "backend-deploy-staging.yml",
}

# Patterns that suggest hardcoded secrets (value not a ${{ }} expression)
# These are checked against env block values only
HARDCODED_SECRET_PATTERNS = [
    # Real keys/passwords/tokens look like: key: "some_actual_value_not_an_expression"
    re.compile(r"^(?!.*\$\{\{)(?!test$)(?!postgres$)(?!true$)(?!false$)(?!0$).{20,}$"),
]

# Suspicious env var names that should always use secrets
SENSITIVE_ENV_NAMES = re.compile(
    r"(password|secret|token|key|api_key|ssh_key|private_key|auth|credential)",
    re.IGNORECASE,
)

# Actions that should be pinned (first-party GitHub + common ones)
MUST_BE_PINNED = re.compile(
    r"^(actions/|github/|gradle/|reactivecircus/|ruby/|securego/|"
    r"dependency-check/|trufflesecurity/|getsentry/|softprops/)"
)
# Pinned = ends with @v<number> or @<sha> (40 hex chars)
PINNED_PATTERN = re.compile(r"@(v\d|[0-9a-f]{40})")
# Explicitly allowed @main/@master for security tools (acceptable for scanners)
ALLOWED_UNPINNED = {
    "securego/gosec@master",       # security scanner — latest intentional
    "trufflesecurity/trufflehog@main",  # security scanner — latest intentional
    "dependency-check/Dependency-Check_Action@main",  # security tool
}

failures: list[str] = []
passes: list[str] = []


def fail(check: str, filename: str, detail: str) -> None:
    failures.append(f"  FAIL [{filename}] {check}: {detail}")


def ok(check: str, filename: str) -> None:
    passes.append(f"  PASS [{filename}] {check}")


def collect_uses(data: object) -> list[str]:
    """Recursively collect all 'uses:' values from a workflow dict."""
    result: list[str] = []
    if isinstance(data, dict):
        for k, v in data.items():
            if k == "uses" and isinstance(v, str):
                result.append(v)
            else:
                result.extend(collect_uses(v))
    elif isinstance(data, list):
        for item in data:
            result.extend(collect_uses(item))
    return result


def collect_env_blocks(data: object) -> list[tuple[str, object]]:
    """Recursively collect all key-value pairs inside 'env:' blocks."""
    result: list[tuple[str, object]] = []
    if isinstance(data, dict):
        for k, v in data.items():
            if k == "env" and isinstance(v, dict):
                result.extend(v.items())
            else:
                result.extend(collect_env_blocks(v))
    elif isinstance(data, list):
        for item in data:
            result.extend(collect_env_blocks(item))
    return result


def check_file(path: Path) -> None:
    filename = path.name
    print(f"\nChecking {filename} ...")

    # ── 1. Valid YAML ─────────────────────────────────────────────────────────
    raw = path.read_text(encoding="utf-8")

    # Tabs check (YAML indentation must not use literal tabs)
    tab_lines = [
        i + 1 for i, line in enumerate(raw.splitlines())
        if line.startswith("\t")
    ]
    if tab_lines:
        fail("valid-yaml/no-tabs", filename, f"tab indentation on lines {tab_lines}")
    else:
        ok("valid-yaml/no-tabs", filename)

    try:
        data = yaml.safe_load(raw)
    except yaml.YAMLError as exc:
        fail("valid-yaml/parse", filename, str(exc))
        return  # can't do further checks on unparseable file

    ok("valid-yaml/parse", filename)

    if not isinstance(data, dict):
        fail("valid-yaml/structure", filename, "top-level must be a mapping")
        return

    ok("valid-yaml/structure", filename)

    # ── 2. Concurrency groups ─────────────────────────────────────────────────
    if filename in MUST_HAVE_CONCURRENCY:
        concurrency = data.get("concurrency")
        if not concurrency:
            fail("concurrency/present", filename, "missing 'concurrency' key")
        elif not isinstance(concurrency, dict):
            fail("concurrency/structure", filename, "'concurrency' must be a mapping")
        else:
            ok("concurrency/present", filename)
            if not concurrency.get("group"):
                fail("concurrency/group", filename, "missing 'concurrency.group'")
            else:
                ok("concurrency/group", filename)
            if concurrency.get("cancel-in-progress") is not True:
                fail("concurrency/cancel-in-progress", filename,
                     f"cancel-in-progress must be true, got: {concurrency.get('cancel-in-progress')}")
            else:
                ok("concurrency/cancel-in-progress", filename)

    # ── 3. Trigger conditions with path filters ───────────────────────────────
    # NOTE: PyYAML 1.1 parses bare `on:` as boolean True — check both spellings
    if filename in MUST_HAVE_PATH_FILTERS:
        on_block = data.get(True, data.get("on", {}))
        has_paths = False
        if isinstance(on_block, dict):
            for trigger_cfg in on_block.values():
                if isinstance(trigger_cfg, dict) and "paths" in trigger_cfg:
                    has_paths = True
                    break
        if not has_paths:
            fail("triggers/path-filters", filename,
                 "no 'paths:' filter found on any trigger — every push will run the workflow")
        else:
            ok("triggers/path-filters", filename)

    # ── 4. security-scan.yml must have cron schedule ──────────────────────────
    if filename == "security-scan.yml":
        on_block = data.get(True, data.get("on", {}))
        has_cron = False
        if isinstance(on_block, dict):
            schedule = on_block.get("schedule", [])
            if isinstance(schedule, list) and any(
                isinstance(item, dict) and "cron" in item for item in schedule
            ):
                has_cron = True
        if not has_cron:
            fail("triggers/cron-schedule", filename, "security-scan.yml must have a cron schedule")
        else:
            ok("triggers/cron-schedule", filename)

    # ── 5. timeout-minutes on every job ──────────────────────────────────────
    jobs = data.get("jobs", {})
    if not isinstance(jobs, dict) or not jobs:
        fail("timeout/jobs-present", filename, "no 'jobs' found in workflow")
    else:
        ok("timeout/jobs-present", filename)
        for job_name, job_cfg in jobs.items():
            if not isinstance(job_cfg, dict):
                continue
            if "timeout-minutes" not in job_cfg:
                fail("timeout/per-job", filename,
                     f"job '{job_name}' is missing 'timeout-minutes'")
            else:
                ok(f"timeout/job-{job_name}", filename)

    # ── 6. Actions version pinning ────────────────────────────────────────────
    all_uses = collect_uses(data)
    for uses_val in all_uses:
        # Skip reusable workflow references (owner/repo/.github/workflows/foo.yml@ref)
        if ".github/workflows/" in uses_val:
            continue
        if uses_val in ALLOWED_UNPINNED:
            ok(f"pinning/{uses_val}", filename)
            continue
        if MUST_BE_PINNED.match(uses_val):
            if not PINNED_PATTERN.search(uses_val):
                fail("pinning/action-version", filename,
                     f"'{uses_val}' is not pinned to a version tag or SHA")
            else:
                ok(f"pinning/{uses_val}", filename)

    # ── 7. No hardcoded credentials in env blocks ─────────────────────────────
    env_pairs = collect_env_blocks(data)
    for env_key, env_val in env_pairs:
        if not isinstance(env_val, str):
            continue
        # Skip values that are expressions
        if "${{" in env_val:
            ok(f"secrets/env-{env_key}", filename)
            continue
        # Skip known-safe test values
        if env_val in ("test", "postgres", "true", "false", "disable",
                        "postgres://postgres:test@localhost:5432/postgres?sslmode=disable",
                        "redis://localhost:6379/0", "true", "true"):
            ok(f"secrets/env-{env_key}", filename)
            continue
        # Flag if the key name looks sensitive and value is long/opaque
        if SENSITIVE_ENV_NAMES.search(env_key):
            if len(env_val) > 8:
                fail("secrets/hardcoded-credential", filename,
                     f"env var '{env_key}' looks sensitive and its value is not a ${{{{ secrets.* }}}} expression (value: '{env_val[:20]}...')")
            else:
                ok(f"secrets/env-{env_key}", filename)
        else:
            ok(f"secrets/env-{env_key}", filename)


def main() -> int:
    workflow_files = sorted(WORKFLOWS_DIR.glob("*.yml"))

    if not workflow_files:
        print(f"ERROR: No workflow files found in {WORKFLOWS_DIR}")
        return 1

    expected_files = {
        "android-ci.yml",
        "backend-ci.yml",
        "backend-deploy-staging.yml",
        "intelligence-ci.yml",
        "security-scan.yml",
    }
    present_files = {f.name for f in workflow_files}
    missing = expected_files - present_files
    if missing:
        for m in sorted(missing):
            failures.append(f"  FAIL [MISSING] workflow file not found: {m}")
    else:
        passes.append(f"  PASS [ALL] all {len(expected_files)} required workflow files present")

    for wf_file in workflow_files:
        check_file(wf_file)

    print("\n" + "=" * 70)
    print(f"RESULTS: {len(passes)} checks passed, {len(failures)} checks failed")
    print("=" * 70)

    if passes:
        print(f"\nPASSED ({len(passes)}):")
        for p in passes:
            print(p)

    if failures:
        print(f"\nFAILED ({len(failures)}):")
        for f in failures:
            print(f)
        return 1

    print("\nAll workflow validation checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
