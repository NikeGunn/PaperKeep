---
name: repo-pro
description: >
  Configure a GitHub repository to industry-grade (FAANG-style) standards in one
  shot: repo metadata (description/About/topics/homepage), security features
  (Dependabot, secret scanning + push protection), branch protection, a clean
  label taxonomy, community health files (CODEOWNERS, PR/issue templates,
  dependabot config, CONTRIBUTING), and merge hygiene (squash-only, auto-delete,
  auto-merge). Use when the user says "configure this repo", "make this repo
  professional", "set up repo settings", "/repo-pro", or asks to harden/polish a
  new GitHub repo for open-source contributors.
---

# repo-pro — one-shot industry-grade GitHub repo setup

You configure a GitHub repo to the standard a mature open-source / FAANG team
would expect. Everything is done via `gh` and the GitHub REST API. Be decisive
and idempotent — re-running must be safe.

## Inputs to gather (ask only what you can't infer)

1. **Repo** — default to the current repo (`gh repo view --json nameWithOwner`).
   If the user wants a NEW repo, create it first (see "Creating a new repo").
2. **Description** — if absent, WRITE a crisp 1-sentence description from the
   README / code. Don't ask; propose one and proceed.
3. **Topics** — infer 5–10 from the stack (languages, frameworks, domain). Show
   them, then apply.
4. **Homepage** — only if the project has one (docs site, Play listing, etc.).
5. **Required status checks** — read the repo's workflows
   (`.github/workflows/*.yml`) and use the JOB-LEVEL check names (the `name:` of
   each job, or a single aggregate gate like `CI Status` if present). Confirm the
   exact names with `gh api repos/<repo>/commits/<branch>/check-runs --jq '.check_runs[].name'`.
6. **Reviews required** — default 0 for a solo dev (so they can self-merge);
   suggest 1 if they expect outside contributors to be reviewed.

## The fast path — run the script

This repo ships `scripts/setup-repo.sh` which does everything below. Prefer it:

```bash
bash scripts/setup-repo.sh \
  --repo <owner/name> \
  --description "<one-liner>" \
  --topics "<comma,separated>" \
  --check-contexts "<Check A,Check B>" \
  --reviews 0
```
Always run `--dry-run` first, show the user the plan, then run for real.
If the script isn't present (different repo), do the steps manually below.

## Manual steps (if the script is unavailable)

Run these with `gh`. Each is idempotent.

**1. Metadata**
```bash
gh repo edit <repo> --description "<one-liner>" --homepage "<url>" \
  --add-topic <t1> --add-topic <t2> ...
```

**2. Merge hygiene & features** (squash-only, clean history)
```bash
gh api -X PATCH repos/<repo> --input - <<'JSON'
{ "has_issues": true, "has_projects": true, "has_wiki": false,
  "allow_squash_merge": true, "allow_merge_commit": false, "allow_rebase_merge": false,
  "allow_auto_merge": true, "delete_branch_on_merge": true, "allow_update_branch": true,
  "squash_merge_commit_title": "PR_TITLE", "squash_merge_commit_message": "PR_BODY" }
JSON
```

**3. Security**
```bash
gh api -X PUT repos/<repo>/vulnerability-alerts
gh api -X PUT repos/<repo>/automated-security-fixes
gh api -X PATCH repos/<repo> --input - <<'JSON'
{ "security_and_analysis": {
    "secret_scanning": { "status": "enabled" },
    "secret_scanning_push_protection": { "status": "enabled" } } }
JSON
```

**4. Branch protection** (solo-friendly: blocks force-push/deletion, requires PR
+ named checks, admin can bypass)
```bash
gh api -X PUT repos/<repo>/branches/<branch>/protection --input - <<JSON
{ "required_status_checks": { "strict": true, "contexts": ["<Check A>","<Check B>"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0,
    "dismiss_stale_reviews": true, "require_code_owner_reviews": false },
  "restrictions": null, "allow_force_pushes": false, "allow_deletions": false,
  "required_linear_history": true, "required_conversation_resolution": true }
JSON
```
> CRITICAL: a required check must actually RUN on PRs or the branch is blocked
> forever. If a workflow has a `paths:` filter on `pull_request`, either remove it
> or move the filtering inside the workflow (a `changes` gate). Never require a
> check that can be skipped at the trigger level.

**5. Labels** — sync a conventional taxonomy with `gh label create ... --force`
(type:*, priority:*, good first issue, help wanted, status:*, wontfix, duplicate).

**6. Community health files** — create ONLY if missing (never overwrite):
`.github/CODEOWNERS`, `.github/pull_request_template.md`,
`.github/ISSUE_TEMPLATE/{bug_report.yml,feature_request.yml,config.yml}`,
`.github/dependabot.yml`, and `CONTRIBUTING.md` if absent. Commit them on a
branch + PR (the default branch is protected).

## Creating a new repo (when the user is starting fresh)

```bash
gh repo create <owner/name> --public --source . --remote origin --push \
  --description "<one-liner>"
```
Then run the full configuration above. Add a LICENSE file if none exists (commit
it — `gh` can't add it after creation). Add a README if missing.

## After configuring

- Show: `gh repo view <repo> --web` to eyeball the result.
- Confirm protection: `gh api repos/<repo>/branches/<branch>/protection --jq '.required_status_checks.contexts'`.
- Tell the user the few things the API can't do: social-preview image, enabling
  Discussions, and committing a LICENSE — all one-time manual steps in Settings.

## Guardrails

- Idempotent: re-running changes nothing already correct.
- Never overwrite existing community files or READMEs.
- Never auto-commit to a protected default branch — open a PR.
- Confirm destructive or outward-facing actions (creating a public repo,
  changing visibility) before doing them.
