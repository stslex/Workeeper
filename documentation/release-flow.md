# Release Flow

This document is the single source of truth for how versions get cut, validated, and shipped to Google Play. It describes the branch model, the version format, every workflow involved, and the recovery procedures for the cases where things go wrong mid-release.

The flow is designed for a single-developer project where reliability and recoverability matter more than parallel-release throughput. It removes the "CI pushes back into a live branch" race condition that previously broke production releases, and decouples the irreversible step (Play upload) from the reversible ones (commits, branches, PRs).

---

## 1. Goals & non-goals

**Goals**
- Eliminate any race between the deploy pipeline and human pushes to `dev`.
- Make every irreversible step (Play upload, tag creation) the *last* thing that happens in its phase, so failures never leave the system in a desynchronized state.
- Bump versions exactly once per release, in a place where two writers cannot collide.
- Allow recovery (retry, partial re-run, skip flaky UI tests) without re-bumping versions or re-uploading to Play.
- Keep `master` as a literal reflection of "what is currently in production".

**Non-goals (explicitly out of scope)**
- Beta channel restructuring. The existing `android_deploy_beta.yml` is not in scope here. A TODO and GitHub Issue track this for later.
- Multi-track or staged rollouts (internal/closed/open testing tracks).
- Hotfixes from arbitrary historical tags. Hotfixes always cut from current `master`.

---

## 2. Glossary

| Term | Meaning |
|---|---|
| `dev` | Working branch. All feature work merges here. CI never pushes to it. |
| `master` | Production-state branch. Reflects exactly what is in Google Play right now. Only updated by merging release branches in. |
| Release branch | A branch in the `release/` namespace, named `release/release-v.X.Y.Z`. Each release lives on its own branch from cut to merge. |
| Release | A new minor (Y) version. Cut from `dev`. `Z` is always `0`. |
| Hotfix | A patch (Z) version on top of an already-released minor. Cut from `master`. `Z >= 1`. |
| Tag | An annotated git tag named `release-v.X.Y.Z`, created on the release branch *after* a successful Play upload. The presence of a tag means: "this version is live in Play". |
| Cut | The act of creating a release branch from a source branch and bumping the version. |
| Pipeline | The orchestrator workflow that runs build, UI tests, and deploy in sequence on a release branch. |

---

## 3. Branch model

```
              cut release             merge after deploy
   dev ─────────────────────► release/release-v.X.Y.0 ─────────► master
    ▲                                                               │
    │                                                               │
    └──── sync PR ◄─────────────────────────────────────────────────┘
                              push triggers sync


              cut hotfix              merge after deploy
   master ──────────────────► release/release-v.X.Y.Z ─────────► master
                              (Z >= 1)                             │
                                                                   │
   dev ◄──────── sync PR ◄──────────────────────────────────────────┘
```

**Branch protection — `master`:**
- Only mergeable via PR.
- Required PR source: branches matching `release/release-v.*` (enforced by `pr_guard.yml`).
- Required status checks: `build`, `pr_guard`.
- Linear history: not required (we merge with merge commits).
- `github-actions[bot]` needs bypass to merge release PRs (Settings → Branches → bypass list). Without this, `gh pr merge` from CI fails on protected master.

**Branch protection — `dev`:**
- No protection beyond standard. Sync PRs are auto-merged when conflict-free.
- `dev` never receives direct pushes from CI.

**Branch lifecycle:**
- A release branch is created by `cut_release.yml`, lives until merged into master, and is deleted as part of the merge step.
- If a release is abandoned, the branch must be deleted manually. The version bump committed to it never leaves the branch (since the PR is never merged).

---

## 4. Versioning

### 4.1 TOML format

In `gradle/libs.versions.toml`:

```toml
[versions]
versionName = "X.Y.Z"   # string, three numeric components separated by dots
versionCode = "N"        # string containing a positive integer; AGP parses to int
```

`versionName` is a string with exactly three numeric components. No `v` prefix. Examples: `"1.5.0"`, `"2.0.0"`, `"1.5.3"`.

`versionCode` is a positive monotonic integer. Each Play upload requires a strictly larger value than any previously uploaded.

### 4.2 Bump rules

| Cut type | Source | `versionName` change | `versionCode` change |
|---|---|---|---|
| Release | `dev` | `X.Y.Z → X.(Y+1).0` | `+1` |
| Hotfix | `master` | `X.Y.Z → X.Y.(Z+1)` | `+1` |

`X` (major) is never bumped automatically. A major bump is a manual edit in dev before cutting a release.

### 4.3 Where bumps happen

**Once per release, at cut time only.** The `cut_release.yml` workflow is the only place that modifies version values. This is the architectural lock that prevents version drift between branches.

`deploy_prod.yml` does **not** bump anything. It reads the version from the release branch's TOML (and validates against the branch name), uses the existing values, uploads to Play, and tags.

This means: deploy retries don't bump `versionCode`. If a retry hits Play with a `versionCode` already accepted (rare but possible if `fastlane deploy` partially succeeded and was then re-run), Play will reject with "version code already in use". Recovery is documented in §8.

### 4.4 Branch ↔ version coherence

Two invariants govern the version values on a release branch.

**Invariant 1: branch name == TOML versionName.** The release branch name encodes its version; the TOML on that branch must match. Enforced:
- At cut time: branch is created *with* the bumped TOML committed atomically.
- At deploy time: the guard step parses the version from `${{ github.ref }}` and compares it to the TOML. Mismatch = fail before upload.

This eliminates the class of bugs where someone hand-edits TOML on a release branch and the branch name lies about what's being released.

**Invariant 2: release versionCode > master versionCode.** Every accepted Play upload requires a strictly larger `versionCode` than any previously uploaded one. Enforced at deploy time: the guard step fetches master's `gradle/libs.versions.toml` and fails if the release branch's `versionCode` is not greater. The error message tells the developer to rebase on master and re-run `bump_version.sh`.

This catches the concurrent-release-and-hotfix collision (see §6.4) before fastlane, where the failure would otherwise occur after a successful build and UI test pass.

---

## 5. Workflow inventory

| Workflow | Trigger | Role |
|---|---|---|
| `android_build_unified.yml` | push:master, pull_request, dispatch, **workflow_call** | Build + unit tests + detekt + lint. Reusable. |
| `ui_tests.yml` | dispatch, **workflow_call** | Smoke / regression UI tests. Reusable. |
| `cut_release.yml` | dispatch (mode: release \| hotfix) | Creates release branch, bumps version, opens PR to master. |
| `deploy_prod.yml` | dispatch (must be on release/* branch) | Build + UI tests + Play upload + tag + merge PR. Orchestrates the others. |
| `sync_master_to_dev.yml` | push:master | Opens PR `master → dev` to propagate version bumps and hotfix changes. |
| `pr_guard.yml` | pull_request:master | Required check; fails if PR source is not a release branch. |
| `github_release_apk.yml` | push:tags `release-*`, dispatch | Builds signed APK and creates GitHub Release. Auto-triggered by tag push from deploy_prod. |
| `android_deploy_beta.yml` | dispatch | Disabled until restructured (Issue #102); race-order fix staged internally so re-enabling as model A needs no further edit. |
| `version_updater.yml` | — | **Removed.** Logic absorbed into `cut_release.yml`. |
| `claude.yml`, `claude-code-review.yml` | various | Unrelated to release flow. Not modified. |

---

## 6. Lifecycles

### 6.1 Standard release

Triggered when there are merged features in `dev` ready to ship.

1. **Cut.** Developer manually triggers `cut_release.yml` with input `mode: release`. The workflow:
   1. Checks out `dev` at HEAD.
   2. Reads current `versionName` from TOML (e.g., `1.5.0`).
   3. Computes new version: bumps `Y`, sets `Z = 0`, bumps `versionCode +1` (e.g., `1.5.0 → 1.6.0`, code `42 → 43`).
   4. Creates branch `release/release-v.1.6.0` from `dev` HEAD.
   5. Commits the TOML change to the new branch with message `chore: bump to v.1.6.0`.
   6. Pushes the branch (race-free; the branch did not exist a moment ago).
   7. Opens a PR `release/release-v.1.6.0 → master` titled `Release v.1.6.0`.

2. **Validation on the PR.** Standard PR triggers fire:
   - `android_build_unified.yml` runs build + unit tests + detekt + lint.
   - `pr_guard.yml` validates the source branch pattern.
   - These are required checks; the PR can't merge until they pass.

3. **Pipeline.** Developer manually triggers `deploy_prod.yml` from the release branch. The workflow:
   1. Guard:
      - Verifies `github.ref` matches `^refs/heads/release/release-v\.\d+\.\d+\.\d+$`. Fails fast otherwise.
      - **Pins HEAD SHA**: runs `git rev-parse HEAD` once and exposes the result as `needs.guard.outputs.sha`. All downstream jobs (build, ui_tests, deploy) consume this pin via explicit `ref:` inputs instead of resolving `github.ref` independently. This guarantees the APK shipped to Play is the same SHA that passed unit + UI tests, even if someone pushes to the release branch mid-pipeline.
      - **Branch ↔ TOML coherence**: parses version from branch name, reads TOML, fails if they don't match (§4.4 invariant 1).
      - **versionCode ↔ master coherence**: fetches master's `gradle/libs.versions.toml` and fails if the release branch's `versionCode` is not strictly greater (§4.4 invariant 2). Catches concurrent-release-and-hotfix collisions before fastlane (see §6.4); recovery in §8.6.
      - **PR lookup**: finds the open release PR, exposes its number as `needs.guard.outputs.pr_number`.
   2. Calls `android_build_unified.yml` (workflow_call) on the pinned SHA.
   3. Calls `ui_tests.yml` with `test_suite: smoke` (skippable via `skip_ui_tests` input) on the pinned SHA.
   4. Stops at the `production` GitHub Environment for manual approval (required reviewer = developer).
   5. After approval: signs and uploads to Play via fastlane.
   6. Generates Play changelog from previous release tag to HEAD.
   7. Creates annotated tag `release-v.1.6.0` on the pinned SHA.
   8. Pushes the tag.
   9. Merges the PR `release/release-v.1.6.0 → master` via `gh pr merge --merge --delete-branch --match-head-commit <pinned-sha>`. `--match-head-commit` aborts the merge if the PR head moved since guard ran — those untested commits would otherwise reach master through the merge.

   The order matters: tag and merge happen *after* Play accepts. If Play fails, no tag, no merge — the system stays in a clean retryable state.

4. **Side effects of tag push.** `github_release_apk.yml` auto-triggers on the new `release-*` tag and builds + publishes a signed APK to GitHub Releases.

5. **Side effects of master push.** `sync_master_to_dev.yml` auto-triggers and opens (or updates) PR `chore/sync-master-to-dev → dev`. If the PR has no conflicts, auto-merge picks it up. If conflicts, developer resolves manually.

### 6.2 Hotfix

Triggered when a bug is found in the currently-released version and dev contains unrelated unreleased changes.

1. **Cut.** Developer triggers `cut_release.yml` with `mode: hotfix`. The workflow:
   1. Checks out `master` at HEAD (master always reflects the live production version).
   2. Reads current `versionName` (e.g., `1.6.0`).
   3. Computes new version: bumps `Z`, leaves `Y` (e.g., `1.6.0 → 1.6.1`), bumps `versionCode +1`.
   4. Creates branch `release/release-v.1.6.1` from `master` HEAD.
   5. Commits, pushes, opens PR.

2. **Fix.** Developer pushes the actual bugfix commits directly to `release/release-v.1.6.1`. This is fine — the branch is owned by this hotfix and nobody else writes to it.

3. **Pipeline & merge.** Same as 6.1 steps 2–5.

### 6.3 Master → dev sync

Triggered by every push to `master` (i.e., after every release/hotfix merge).

1. `sync_master_to_dev.yml` runs.
2. It calls `peter-evans/create-pull-request` to open or update PR `chore/sync-master-to-dev → dev`. This action is idempotent: if a PR already exists, it updates the branch.
3. Auto-merge is enabled on the PR; it merges itself when checks pass and there are no conflicts.

The expected delta is the version bump in TOML, plus (for hotfixes) the bugfix commits. Conflicts are unusual but possible if dev independently modified `gradle/libs.versions.toml`. Developer resolves manually.

### 6.4 Concurrent release + hotfix

Possible scenario: a release PR (`release/release-v.1.6.0 → master`) is open and going through validation when a critical bug in `1.5.0` is found.

- Hotfix can be cut from `master`. Master is still at `1.5.0` (release PR hasn't merged), so hotfix becomes `1.5.1`.
- Now there are two open PRs to master with conflicting `versionName` and `versionCode` values in TOML.
- Whichever merges first wins. The other PR's deploy fails fast at the guard step (§4.4 invariant 2 — release `versionCode` is no longer strictly greater than master's) the next time it is triggered, with a clear "rebase the release branch and re-run `bump_version.sh`" message. This catches the collision **before** fastlane uploads anything to Play. Recovery procedure in §8.6.
- The branch may also need re-bumping (e.g., if hotfix `1.5.1` merges first, the release PR for `1.6.0` is still valid since `1.6.0 > 1.5.1`; but if release `1.6.0` merges first, hotfix becomes obsolete because the bug should be fixed in `1.6.0` directly or as `1.6.1`). Resolution is a human judgment call.

---

## 7. Pipeline composition

### 7.1 Reusable workflows

`android_build_unified.yml` gains a `workflow_call` trigger alongside its existing triggers:

```yaml
on:
  push:
    branches: [master]
  pull_request:
  workflow_dispatch:
  workflow_call:
    inputs:
      ref:
        type: string
        required: true
        description: "Git ref to check out and build"
```

The `actions/checkout` step uses `ref: ${{ inputs.ref || github.ref }}` to support both PR/push triggers and explicit calls.

`ui_tests.yml` similarly gains:

```yaml
on:
  workflow_dispatch:
    inputs: ...
  workflow_call:
    inputs:
      test_suite:
        type: string
        required: true
      ref:
        type: string
        required: true
```

### 7.2 `deploy_prod.yml` composition

`deploy_prod.yml` runs as a single workflow with multiple jobs that depend on each other:

```yaml
on:
  workflow_dispatch:
    inputs:
      skip_ui_tests:
        type: boolean
        default: false
        description: "Skip UI tests (use only when retrying after a known-good UI run)"

concurrency:
  group: deploy-prod-${{ github.ref }}
  cancel-in-progress: false

jobs:
  guard:
    # - validates branch ↔ TOML coherence + versionCode > master (§4.4)
    # - pins HEAD SHA to outputs.sha for downstream jobs (§6.1 step 3)
    # - locates the open release PR and exposes outputs.pr_number

  build:
    needs: guard
    uses: ./.github/workflows/android_build_unified.yml
    with:
      ref: ${{ needs.guard.outputs.sha }}
    secrets: inherit

  ui_tests:
    needs: [guard, build]
    if: ${{ !inputs.skip_ui_tests }}
    uses: ./.github/workflows/ui_tests.yml
    with:
      test_suite: smoke
      ref: ${{ needs.guard.outputs.sha }}
    secrets: inherit

  deploy:
    needs: [guard, build, ui_tests]
    if: ${{ always() && needs.build.result == 'success' && (needs.ui_tests.result == 'success' || needs.ui_tests.result == 'skipped') }}
    environment: production    # required reviewer gate
    # ... fastlane deploy + tag + gh pr merge --match-head-commit <pinned-sha>
```

### 7.3 Skip semantics

`skip_ui_tests=true` is for retries where the developer has verified UI tests are healthy in a prior run and the deploy step itself failed. It is a new workflow run (not a re-run), so `build` will execute again. This is intentional: the build is fast and ensures we're deploying the exact current branch state.

For partial re-runs that don't change inputs (e.g., deploy step crashed on a transient error and you just want to retry it), use GitHub's native "Re-run failed jobs" UI button. This re-uses the existing run's results for upstream jobs.

### 7.4 Concurrency

`concurrency.group: deploy-prod-${{ github.ref }}` ensures only one deploy can run per release branch at a time. Two simultaneous deploys of the same release would race on tag creation and PR merge.

`cancel-in-progress: false` — never auto-cancel a deploy that's running. If a second is queued, it waits.

---

## 8. Recovery procedures

### 8.1 Pipeline failed before Play upload

Examples: detekt found an issue, build failed, smoke test failed, environment approval timed out.

**Recovery:** fix and re-run.
- For code issues: push the fix to the release branch, manually re-trigger `deploy_prod.yml` with default inputs.
- For environment timeout: just re-trigger.
- The version is already committed; no bump needed.

### 8.2 Pipeline failed after Play upload but before tag/merge

Examples: tag push got rate-limited, `gh pr merge` hit a transient API error.

**State:** Play has the new version. The release branch has the bumped TOML. No tag exists. No merge happened. `master` does not yet reflect the release.

**Recovery:** manual completion of the missed steps.
1. Locally checkout the release branch.
2. Create the tag: `git tag -a release-v.X.Y.Z -m "Release v.X.Y.Z"`.
3. Push the tag: `git push origin release-v.X.Y.Z`.
4. Merge the PR: `gh pr merge <number> --merge --delete-branch`.

Do **not** re-run `deploy_prod.yml` in this state — it would attempt to upload to Play again with the same `versionCode`, which Play will reject. (The pipeline does not have a "resume from after Play" mode by design; this state is rare enough that manual recovery is acceptable.)

### 8.3 Play upload partially succeeded then errored

Examples: fastlane uploaded the AAB but errored on metadata sync, network glitch mid-upload.

**Recovery:** developer must determine Play state via Play Console.
- If the new `versionCode` is **listed** in Play (even as draft/uploaded): treat as §8.2.
- If the new `versionCode` is **not listed**: treat as §8.1 — re-run is safe.

### 8.4 Wrong version cut

Example: ran `cut_release.yml` with `mode: release` when intending `hotfix`, or vice versa.

**Recovery:** abandon the branch.
1. Close the PR (don't merge).
2. Delete the release branch locally and on origin.
3. Re-cut with the correct mode.

The TOML in `master`/`dev` is untouched (cut commits to the new branch only). No cleanup needed beyond the branch itself.

### 8.5 Need to skip UI tests

Use `skip_ui_tests: true` input on `deploy_prod.yml` workflow_dispatch. Document the reason in the run's commit/PR description.

### 8.6 Deploy guard rejected: release versionCode is not greater than master's

Example: a hotfix was cut and merged from master while a release branch was already open, so master's `versionCode` advanced past (or equals) the release branch's. The guard step fails with a "must be strictly greater than master's" error, before any build/test/upload runs.

**Recovery:**
1. Locally check out the release branch and rebase it on master:
   ```
   git fetch origin master
   git checkout release/release-v.X.Y.Z
   git rebase origin/master
   ```
2. Resolve any conflicts (typically only `gradle/libs.versions.toml`).
3. Re-bump versions on the rebased branch using the **same mode** the branch was originally cut with:
   ```
   bash ./.github/scripts/bump_version.sh --mode=<release|hotfix>
   git commit -am "chore: re-bump after master advanced"
   git push --force-with-lease
   ```
4. Re-trigger `deploy_prod.yml`. The guard now passes because the release `versionCode` is strictly greater than master's.

Nothing has been uploaded to Play, no tag exists, no merge happened — the fix is a rebase + re-bump cycle on the release branch only.

If the rebase changes the branch's intended `versionName` (e.g. a hotfix branch `release/release-v.1.6.1` rebased onto a master at `1.6.1` would re-bump to `1.6.2`, mismatching the branch name and tripping invariant 1), abandon the branch per §8.4 and re-cut instead.

---

## 9. Setup checklist

One-time configuration after the migration code lands.

**Branch protection rules (Settings → Branches):**
- `master`:
  - Require pull request before merging.
  - Require status checks: `build`, `pr_guard`.
  - Restrict who can push: include `github-actions[bot]` in bypass list, or use a PAT with bypass rights stored as `PUSH_TOKEN`.
- `dev`: no protection required.

**GitHub Environment (Settings → Environments):**
- Create `production` environment.
- Add required reviewer: yourself.
- No deployment branch restrictions (the workflow's branch guard handles this).

**Secrets (Settings → Secrets):**
- `PUSH_TOKEN` — PAT with `repo` and `workflow` scopes. Used for: pushing branches, pushing tags, opening PRs, merging PRs. Must have bypass rights on `master` branch protection.
- All existing secrets (`KEYSTORE`, `KEYSTORE_PASSPHRASE`, etc.) remain in use.

**Actions permissions (Settings → Actions → General):**
- Workflow permissions: "Read and write permissions".
- Allow GitHub Actions to create and approve pull requests: enabled (needed for `peter-evans/create-pull-request`).

---

## 10. Migration plan

The migration touches: TOML format, version script, three existing workflows (build, ui-tests, prod deploy), three new workflows (cut, sync, guard), and one removed workflow (version_updater).

It is structured to land in stages so that intermediate states are valid and existing workflows keep working until they're explicitly replaced.

### Stage 1 — Foundation (TOML + script)

- Update `gradle/libs.versions.toml`: change `versionName` to current Play production version in `X.Y.Z` format. Set `versionCode` to one above the latest accepted in Play (so the next deploy works).
- Rewrite `.github/scripts/update_versions.sh` (renamed to `bump_version.sh`) to:
  - Take a `--mode=release|hotfix` flag.
  - Parse `versionName` as `X.Y.Z`.
  - Bump per §4.2.
  - Bump `versionCode +1`.
  - Write back to TOML.
- Add a small unit test or smoke check in the script (run with sample TOML, assert output).

### Stage 2 — Make existing workflows callable

- Add `workflow_call` to `android_build_unified.yml` with `ref` input.
- Add `workflow_call` to `ui_tests.yml` with `test_suite` and `ref` inputs.
- Verify both still work for their existing triggers (PR, push, manual).

### Stage 3 — New workflows

- Add `cut_release.yml` (manual dispatch with mode + source-branch logic).
- Add `pr_guard.yml` (PR validator).
- Add `sync_master_to_dev.yml` (push:master → opens PR).

### Stage 4 — Replace `deploy_prod.yml`

- Rewrite `android_deploy_prod.yml`:
  - Branch guard.
  - TOML/branch coherence check.
  - Calls build + ui_tests reusable workflows.
  - Uses `production` environment.
  - Tag-and-merge happen *after* Play accepts.
- Remove the old commit-and-push-bump logic — version is already committed by `cut_release`.

### Stage 5 — Cleanup & beta race fix

- Delete `version_updater.yml`.
- Apply the race-condition fix to `android_deploy_beta.yml` (re-order: commit & push *before* fastlane upload, OR drop the auto-commit entirely and treat beta the same as prod with its own cut). For now, just re-order to stop the bleeding. File a TODO issue for full beta restructure.

### Stage 6 — Branch protection & one-time merge

- Configure branch protection per §9.
- Configure `production` environment per §9.
- Verify `PUSH_TOKEN` scopes per §9.
- Merge all changes through to `dev` and `master` one final time *manually* (this is the last manual master push).
- From this point on, master only receives merges from release branches.

### Stage 7 — Smoke test the new flow

- Cut a no-op release (e.g., bump to next minor with no other changes) end-to-end.
- Verify: branch created, PR opened, build/UI green, environment gate triggers approval prompt, deploy succeeds, tag pushed, GitHub Release auto-created, master updated, sync PR opened, dev gets the bump.
- If anything is off, fix on a branch and re-run before doing the next real release.

---

## 11. Open questions / future work

- **Beta channel restructure.** Track via GitHub Issue. Decision needed: cut-from-release-branch model (B) vs. keep-as-fast-from-dev (A). Today's flow stays as A with the race fixed.
- **Hotfix from arbitrary historical tag.** Currently hotfix cuts from `master` HEAD only. If a future release has been merged but a hotfix on an *older* released version is needed, this flow can't express it. Adding it would require an optional `source_ref` input to `cut_release.yml`. Defer until the case actually arises.
- **Version source for deploy: TOML vs branch name.** Current design parses branch name and validates against TOML (defense in depth). If this proves redundant in practice, simplify to one or the other.
- **Auto-merge of sync PR.** Setting up auto-merge for `chore/sync-master-to-dev` makes the loop fully hands-off in the no-conflict case. Recommend enabling after the first 2–3 releases prove the sync produces clean PRs.
- **Tracking remaining tech debt items as GitHub Issues** rather than holding them in memory or in `documentation/tech-debt.md` only.
