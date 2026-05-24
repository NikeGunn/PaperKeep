# GitHub Setup — Industry-Grade Repo, One Shot

> A reusable, end-to-end guide to take a brand-new (or existing) GitHub repo to
> the standard a mature open-source / FAANG team expects — using `gh` commands
> you run **once**. Hand this file to Claude Code and say *"configure this repo
> per docs/github-setup.md"*, or run the bundled script yourself.

---

## 3 ways to use this

| Way | Command | When |
|---|---|---|
| **Claude Code skill** | type `/repo-pro` | Easiest — Claude reads your repo, writes the description/topics, and runs everything. |
| **One script** | `bash scripts/setup-repo.sh` | No Claude needed. Idempotent, `--dry-run` supported. |
| **Manual `gh`** | the commands below | Learn it / do it on a repo without the script. |

All three apply the **same** configuration. Pick whichever fits the moment.

---

## Prerequisites (once per machine)

```bash
gh --version           # need GitHub CLI
gh auth login          # authenticate (scopes: repo, workflow, read:org, delete_repo)
gh auth status         # confirm
# jq is used by the script:
jq --version
```

---

## A. Create a brand-new repo from a local project

```bash
# from your project root, after `git init` and a first commit
gh repo create <owner>/<name> \
  --public \
  --source . \
  --remote origin \
  --push \
  --description "One crisp sentence about what this does"
```
- `--public` (or `--private`) sets visibility.
- `--source .` uses the current folder; `--push` pushes the initial commit.
- Add a `LICENSE` file and `README.md` before this if you can (the API can't add
  a license after creation — it must be a committed file).

Already have a repo on GitHub? Skip this and go to **B**.

---

## B. Configure it to industry standard — the one shot

### Option 1 — the script (recommended)
```bash
# preview first (changes nothing):
bash scripts/setup-repo.sh --dry-run \
  --description "One crisp sentence" \
  --topics "android,kotlin,security,jetpack-compose" \
  --check-contexts "CI Status,Security Scan" \
  --reviews 0

# then for real (drop --dry-run):
bash scripts/setup-repo.sh \
  --description "One crisp sentence" \
  --topics "android,kotlin,security,jetpack-compose" \
  --check-contexts "CI Status,Security Scan" \
  --reviews 0
```

### Option 2 — Claude Code
Open Claude Code in the repo and type:
```
/repo-pro
```
It infers the description, topics, and the right status-check names from your
workflows, shows you the plan, and applies it.

---

## C. What "industry-grade" means here (and the raw `gh` for each)

Everything the script/skill does, explained — so you understand and can master `gh`.

### 1. Repo metadata — the "About" box
A good About box = description + topics + homepage. It's how people find and trust your repo.
```bash
gh repo edit <repo> \
  --description "Android document scanner — private, on-device, encrypted." \
  --homepage "https://your-site-or-play-listing" \
  --add-topic android --add-topic kotlin --add-topic security \
  --add-topic jetpack-compose --add-topic privacy
```

### 2. Merge hygiene — clean, linear history
Squash-only merges keep `main` history one-commit-per-PR. Auto-delete keeps the branch list tidy.
```bash
gh api -X PATCH repos/<repo> --input - <<'JSON'
{ "has_issues": true, "has_projects": true, "has_wiki": false,
  "allow_squash_merge": true, "allow_merge_commit": false, "allow_rebase_merge": false,
  "allow_auto_merge": true, "delete_branch_on_merge": true, "allow_update_branch": true,
  "squash_merge_commit_title": "PR_TITLE", "squash_merge_commit_message": "PR_BODY" }
JSON
```

### 3. Security — free, turn it all on
```bash
gh api -X PUT repos/<repo>/vulnerability-alerts        # Dependabot alerts
gh api -X PUT repos/<repo>/automated-security-fixes    # auto-PRs for vuln fixes
gh api -X PATCH repos/<repo> --input - <<'JSON'        # secret scanning + push protection
{ "security_and_analysis": {
    "secret_scanning": { "status": "enabled" },
    "secret_scanning_push_protection": { "status": "enabled" } } }
JSON
```
Push protection blocks a commit *before* it leaks a key. Free on public repos.

### 4. Branch protection — the safety net
Solo-friendly: blocks force-push/deletion, requires a PR + named status checks,
but lets you (admin) bypass in a pinch.
```bash
gh api -X PUT repos/<repo>/branches/main/protection --input - <<'JSON'
{ "required_status_checks": { "strict": true, "contexts": ["CI Status","TruffleHog Secrets Scan"] },
  "enforce_admins": false,
  "required_pull_request_reviews": { "required_approving_review_count": 0,
    "dismiss_stale_reviews": true, "require_code_owner_reviews": false },
  "restrictions": null, "allow_force_pushes": false, "allow_deletions": false,
  "required_linear_history": true, "required_conversation_resolution": true }
JSON
```
> ⚠️ **The #1 trap:** a required status check must actually *run on PRs*, or your
> merge button is blocked forever. If a workflow has `on: pull_request: paths: [...]`,
> a PR that doesn't touch those paths never produces the check → permanent block.
> Fix: drop the `paths:` filter and filter *inside* the workflow (a `changes` job),
> or require an aggregate gate job (like `CI Status`) that always runs.

Check the exact check names your CI produces:
```bash
gh api repos/<repo>/commits/main/check-runs --jq '.check_runs[].name' | sort -u
```

### 5. Labels — a clean taxonomy
A consistent label set makes triage and "good first issue" discovery real.
```bash
gh label create "type: bug"          --color d73a4a --description "Something isn't working" --force
gh label create "type: feature"      --color 0e8a16 --description "New feature or request"   --force
gh label create "good first issue"   --color 7057ff --description "Good for newcomers"        --force
gh label create "help wanted"        --color 008672 --description "Extra attention is wanted" --force
# ...the script creates the full set (type:*, priority:*, status:*, wontfix, duplicate)
```

### 6. Community health files
Create these (the script makes them only if missing — never overwrites):
- `.github/CODEOWNERS` — who reviews what
- `.github/pull_request_template.md` — consistent PRs
- `.github/ISSUE_TEMPLATE/bug_report.yml` + `feature_request.yml` + `config.yml`
- `.github/dependabot.yml` — weekly dependency PRs
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `LICENSE`

---

## D. `gh` mastery — the commands a senior reaches for daily

### Issues
```bash
gh issue create --title "Crash on API 26" --body "..." --label "type: bug,priority: high"
gh issue list --label "good first issue" --state open
gh issue view 12 --comments
gh issue close 12 --reason completed
gh issue edit 12 --add-label "status: in progress" --add-assignee @me
gh issue develop 12 --checkout          # create+checkout a branch for an issue
```

### Pull requests
```bash
gh pr create --fill                      # title/body from commits
gh pr create --title "..." --body "..." --base main
gh pr status                             # what's mine / needs my review
gh pr checks                             # CI status of current PR
gh pr view 2 --json mergeable,mergeStateStatus
gh pr merge 2 --squash --delete-branch   # merge (matches squash-only setting)
gh pr ready / gh pr ready --undo         # toggle draft
```

### Releases (this project also has `scripts/release.sh`)
```bash
gh release create v2.0.0 --generate-notes
gh release upload v2.0.0 app-release.apk
gh release list
```

### Actions / CI
```bash
gh run list --branch main
gh run view <id> --log-failed
gh run watch <id> --exit-status
gh workflow run android-release.yml -f track=internal
```

### Repo & API power tools
```bash
gh repo view <repo> --web
gh repo edit <repo> --enable-discussions   # toggle features
gh api repos/<repo>/branches/main/protection --jq '.required_status_checks.contexts'
gh api graphql -f query='...'               # anything the REST/flags don't cover
gh secret set NAME < file                   # add a repo secret (see RELEASE_SECRETS_SETUP.md)
gh secret list
```

---

## E. The handful of things the API/CLI can't do (one-time, in Settings UI)

- **Social preview image** (Settings → General → Social preview)
- **Enable Discussions** UI niceties (the `--enable-discussions` flag toggles it on)
- **First Play Store upload** (unrelated to GitHub — see `docs/RELEASE_SECRETS_SETUP.md`)

---

## F. Copy-paste: configure a fresh repo end-to-end

```bash
# 1. create + push
gh repo create <owner>/<name> --public --source . --remote origin --push \
  --description "One crisp sentence"

# 2. one-shot config (preview, then apply)
bash scripts/setup-repo.sh --dry-run --topics "tag1,tag2,tag3" --check-contexts "CI Status"
bash scripts/setup-repo.sh           --topics "tag1,tag2,tag3" --check-contexts "CI Status"

# 3. verify
gh repo view <owner>/<name> --web
```

That's a repo configured the way a senior engineer at a top company would expect:
discoverable, secured, protected, contributor-ready — in one shot.

---

### Related in this repo
- `scripts/setup-repo.sh` — the automation engine
- `.claude/skills/repo-pro/SKILL.md` — the `/repo-pro` Claude Code skill
- `docs/HOW_TO_RELEASE.md` — shipping versions
- `docs/RELEASE_SECRETS_SETUP.md` — CI/release secrets
