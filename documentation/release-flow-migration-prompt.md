# Claude Code Prompt: Release Flow Migration

You are migrating the Workeeper repository from the current ad-hoc release workflow to the new release-branch-based flow. The full design is documented in `documentation/release-flow.md`. **Read that document first and treat it as the source of truth.** This prompt sequences the implementation work; the doc defines the target behavior.

## Context summary

- **Project**: Workeeper, an Android app, multi-module Kotlin/Compose codebase.
- **Current pain**: production release pipeline pushes a version bump back into the live `dev` branch after Play upload. When `dev` receives a concurrent push during the deploy, the back-push fails, leaving Play and the repo desynchronized.
- **Target**: bump versions exactly once on a dedicated release branch, never push back into `dev`, gate Play upload behind UI tests + environment approval, and tag/merge only after Play accepts.
- **Branch naming (literal, dots included)**:
  - `release/release-v.X.Y.0` for releases (cut from `dev`).
  - `release/release-v.X.Y.Z` (Z>=1) for hotfixes (cut from `master`).
- **Tag naming**: `release-v.X.Y.Z` (annotated, created only after successful Play upload).
- **Version format**: TOML stores `versionName = "X.Y.Z"` as a string with three dot-separated integer components, replacing the previous `X.YZ` float-like format.

## Operating principles for this task

- Work in stages. After each stage, stop and report. Do not proceed to the next stage until I confirm.
- For every change, verify locally before committing: run `./gradlew detekt`, `./gradlew lintDebug`, and the unit test target on touched modules. For shell scripts, run them against a sample TOML and verify outputs.
- Follow existing repo conventions: convention plugins in `build-logic`, custom Detekt rules, MVI architecture (not relevant here, but stay consistent if you touch any Kotlin).
- Commit messages follow `.github/git-commit-instructions.md`. Use `ci:` or `chore:` prefixes for workflow changes, `build:` for the toml/script.
- All documentation, workflow files, scripts, and commit messages are in **English**.
- If anything in `documentation/release-flow.md` conflicts with this prompt, the doc wins. Stop and ask.
- Do not modify `claude.yml`, `claude-code-review.yml`, or any feature/core code modules. Scope is `.github/` and `gradle/libs.versions.toml` only.

---

## Stage 1: TOML format + version bump script

**Goal**: switch `versionName` from `X.YZ` to `X.Y.Z` and rewrite the bump script to support release/hotfix modes.

### 1.1 Update `gradle/libs.versions.toml`

- Locate the `versionName` and `versionCode` lines under `[versions]`.
- Change `versionName` to the current Play Store production value in `X.Y.Z` format. **Ask me for the current Play production version before making this change.** Do not guess.
- Set `versionCode` to one greater than the latest accepted Play version code. **Ask me for this value before making the change.**

### 1.2 Rewrite `.github/scripts/update_versions.sh`

Rename to `.github/scripts/bump_version.sh`. The new script:

**Interface**:
```
bump_version.sh --mode=release   # bumps Y, sets Z=0, bumps versionCode +1
bump_version.sh --mode=hotfix    # bumps Z, leaves Y, bumps versionCode +1
```

**Behavior**:
- Parse `versionName` from `gradle/libs.versions.toml` as `X.Y.Z`. Fail with a clear error if it doesn't match three integer components.
- Parse `versionCode` as integer. Fail if not a valid integer.
- For `--mode=release`: new `versionName` = `${X}.${Y+1}.0`. New `versionCode` = old + 1.
- For `--mode=hotfix`: new `versionName` = `${X}.${Y}.${Z+1}`. New `versionCode` = old + 1.
- Write back to TOML using safe sed (avoid corrupting the file). Use a temp file + mv pattern.
- After write, re-read the TOML and verify the new values are exactly what was written. Fail if mismatch.
- Echo the new `versionName` and `versionCode` to stdout in a parseable form (e.g., `version_name=X.Y.Z` and `version_code=N` on separate lines), for capture by callers.

**Edge cases to handle**:
- TOML uses quotes around values: `versionName = "1.5.0"`. Preserve quoting on rewrite.
- Script must work whether or not the file ends with a newline.
- Both `versionName` and `versionCode` lines must be updated atomically (both succeed or original is restored).

### 1.3 Smoke-test the script

Create a temp directory with a fixture TOML containing `versionName = "1.5.0"` and `versionCode = "42"`. Run:

```
./.github/scripts/bump_version.sh --mode=release
# expect: versionName="1.6.0", versionCode="43"
```

Then on the same fixture (now at 1.6.0):
```
./.github/scripts/bump_version.sh --mode=hotfix
# expect: versionName="1.6.1", versionCode="44"
```

Verify both. If anything fails, fix and re-test before moving on.

### 1.4 Stop and report

Show me:
- The new TOML state.
- The new `bump_version.sh`.
- The smoke test output.

Wait for confirmation before Stage 2.

---

## Stage 2: Make `android_build_unified.yml` and `ui_tests.yml` callable

**Goal**: add `workflow_call` triggers and `ref` inputs without breaking existing PR/push triggers.

### 2.1 `android_build_unified.yml`

- Add `workflow_call` to the `on:` block alongside existing triggers:
  ```yaml
  workflow_call:
    inputs:
      ref:
        type: string
        required: true
        description: "Git ref to check out and build"
  ```
- Update the `actions/checkout@v4` step's `with:` block to include `ref: ${{ inputs.ref || github.ref }}`. This works for both modes: when called via `workflow_call`, `inputs.ref` is set; on push/PR, it falls back to `github.ref`.
- Do not change anything else. The existing job structure, caching, test reporting — all stays.

### 2.2 `ui_tests.yml`

- Add `workflow_call`:
  ```yaml
  workflow_call:
    inputs:
      test_suite:
        type: string
        required: true
        description: "smoke | regression | all"
      ref:
        type: string
        required: true
  ```
- Update both `smoke-tests` and `regression-tests` jobs:
  - Their `if:` clauses currently reference `inputs.test_suite`. This already works for both `workflow_dispatch` and `workflow_call` (both pass inputs the same way), so no change needed there.
  - Add `ref: ${{ inputs.ref || github.ref }}` to the `actions/checkout@v4` step in each job.

### 2.3 Verify

- Open `android_build_unified.yml` in `act` or push to a throwaway branch and trigger via PR. Confirm it still runs.
- For `ui_tests.yml`, just verify YAML parses (`actionlint` if available, or `yq` validate).

### 2.4 Stop and report

Show me the diffs. Wait for confirmation.

---

## Stage 3: New workflow — `cut_release.yml`

**Goal**: a manual workflow that cuts a release or hotfix branch, bumps version, opens PR to master.

### 3.1 File: `.github/workflows/cut_release.yml`

**Triggers**: `workflow_dispatch` only.

**Inputs**:
```yaml
mode:
  type: choice
  options: [release, hotfix]
  required: true
  description: "release: bump minor (Y), cut from dev. hotfix: bump patch (Z), cut from master."
```

**Permissions**:
```yaml
permissions:
  contents: write
  pull-requests: write
```

**Concurrency**: group `cut-release`, cancel-in-progress: false.

**Job steps** (single job, `runs-on: ubuntu-latest`):

1. **Determine source branch** based on input:
   - `mode=release` → source is `dev`.
   - `mode=hotfix` → source is `master`.
2. **Checkout** the source branch with `fetch-depth: 0`.
3. **Run** `bash ./.github/scripts/bump_version.sh --mode=${{ inputs.mode }}`. Capture the new `versionName` and `versionCode` into step outputs.
4. **Compute branch name**: `release/release-v.${VERSION_NAME}` (literal, with the dot after `v`).
5. **Configure git** identity as `github-actions[bot]`.
6. **Create the new branch**: `git checkout -b ${BRANCH_NAME}`.
7. **Commit** the TOML change: `git commit -am "chore: bump to v.${VERSION_NAME} (code ${VERSION_CODE})"`.
8. **Push** the new branch with the `PUSH_TOKEN` (use `git push -u origin ${BRANCH_NAME}`, *not* the third-party push action — we don't need it for a brand-new branch).
9. **Open PR** via `gh pr create`:
   - Base: `master`
   - Head: `${BRANCH_NAME}`
   - Title: `Release v.${VERSION_NAME}` (release) or `Hotfix v.${VERSION_NAME}` (hotfix)
   - Body: include version, code, mode, and a placeholder line `<!-- changelog generated at deploy time -->`.
   - Use `GH_TOKEN: ${{ secrets.PUSH_TOKEN }}` env var for `gh`.

**Failure modes to handle**:
- Branch already exists → fail with a clear message ("Branch X already exists. Delete it or pick a different version.").
- Source branch checkout fails → standard checkout action handles this.
- Push fails → unlikely (new branch), but bubble the error up.

### 3.2 Verify

Run a dry-run mentally: trace the flow for `mode=release` from `dev` at `1.5.0` → branch `release/release-v.1.6.0` is created, PR opens. For `mode=hotfix` from `master` at `1.5.0` → branch `release/release-v.1.5.1` is created.

### 3.3 Stop and report

Show me the workflow file. Wait for confirmation.

---

## Stage 4: New workflows — `pr_guard.yml` and `sync_master_to_dev.yml`

### 4.1 `.github/workflows/pr_guard.yml`

**Trigger**: `pull_request` targeting `master`.

**Permissions**: `contents: read`.

**Job**: single step that checks `${{ github.head_ref }}` matches the regex `^release/release-v\.[0-9]+\.[0-9]+\.[0-9]+$`. Fail if not.

Use a bash step:
```yaml
- name: Validate source branch
  run: |
    HEAD_REF="${{ github.head_ref }}"
    if [[ ! "$HEAD_REF" =~ ^release/release-v\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      echo "::error::Only release/release-v.X.Y.Z branches can target master. Got: $HEAD_REF"
      exit 1
    fi
    echo "Source branch OK: $HEAD_REF"
```

### 4.2 `.github/workflows/sync_master_to_dev.yml`

**Trigger**: `push` to `master`.

**Permissions**:
```yaml
contents: write
pull-requests: write
```

**Concurrency**: group `sync-master-to-dev`, cancel-in-progress: false.

**Job**: uses `peter-evans/create-pull-request@v6` to open or update PR `chore/sync-master-to-dev → dev`.

```yaml
steps:
  - uses: actions/checkout@v4
    with:
      ref: master
      fetch-depth: 0
  - uses: peter-evans/create-pull-request@v6
    with:
      token: ${{ secrets.PUSH_TOKEN }}
      base: dev
      branch: chore/sync-master-to-dev
      title: "chore: sync master to dev"
      body: |
        Auto-generated PR to propagate master changes (version bumps, hotfixes) back to dev.
      commit-message: "chore: sync master to dev"
      delete-branch: true
```

Do **not** enable auto-merge in the workflow itself — leave that as a manual GitHub setting on the PR for now (per the doc's §11 note).

### 4.3 Stop and report

Show me both files. Wait for confirmation.

---

## Stage 5: Rewrite `android_deploy_prod.yml`

**Goal**: orchestrator that runs guard → build → ui_tests → environment-gated deploy → tag → merge PR. The version is **read** here, not bumped.

### 5.1 File structure

Replace the entire contents of `.github/workflows/android_deploy_prod.yml` with the new orchestrator.

**Triggers**:
```yaml
on:
  workflow_dispatch:
    inputs:
      skip_ui_tests:
        type: boolean
        default: false
        description: "Skip UI tests. Use only on retries when UI tests passed in a prior run."
```

**Permissions**:
```yaml
contents: write
pull-requests: write
```

**Concurrency**: group `deploy-prod-${{ github.ref }}`, cancel-in-progress: false.

### 5.2 Jobs

**Job 1: `guard`**
- `runs-on: ubuntu-latest`
- Output: `version_name`, `version_code`, `pr_number`.
- Steps:
  1. Validate `github.ref` matches `^refs/heads/release/release-v\.[0-9]+\.[0-9]+\.[0-9]+$`. Fail otherwise.
  2. Extract version from branch name (strip the prefix, you get `X.Y.Z`).
  3. Checkout the ref.
  4. Read `versionName` and `versionCode` from TOML.
  5. Compare: `versionName` from TOML must equal version parsed from branch name. Fail otherwise.
  6. Find the open PR for this branch via `gh pr list --head $BRANCH --base master --json number --jq '.[0].number'`. If none, fail.
  7. Set step outputs.

**Job 2: `build`**
- `needs: guard`
- `uses: ./.github/workflows/android_build_unified.yml`
- `with: { ref: ${{ github.ref }} }`
- `secrets: inherit`

**Job 3: `ui_tests`**
- `needs: build`
- `if: ${{ !inputs.skip_ui_tests }}`
- `uses: ./.github/workflows/ui_tests.yml`
- `with: { test_suite: smoke, ref: ${{ github.ref }} }`
- `secrets: inherit`

**Job 4: `deploy`**
- `needs: [guard, build, ui_tests]`
- `if: ${{ always() && needs.build.result == 'success' && (needs.ui_tests.result == 'success' || needs.ui_tests.result == 'skipped') }}`
- `environment: production`
- `runs-on: ubuntu-latest`
- Steps (in order):
  1. Checkout the release branch with `fetch-depth: 0`.
  2. Set up JDK 21, Ruby 3.3, install bundler.
  3. Decrypt keystore (existing logic from current workflow).
  4. Configure `keystore.properties` (existing).
  5. Create `play_config.json` from `PLAY_CONFIG_JSON` secret (existing).
  6. Create `app/store/google-services.json` (existing).
  7. Apply CI gradle properties (existing copy steps).
  8. **Generate Play changelog** from previous `release-v.*` tag to HEAD. Use the existing `.github/scripts/generate_changelog.sh` script with appropriate FROM/TO refs.
  9. **Run** `bundle exec fastlane deploy`.
  10. **Tag** the current commit: `git tag -a release-v.${{ needs.guard.outputs.version_name }} -m "Release v.${{ needs.guard.outputs.version_name }}"`.
  11. **Push** the tag: `git push origin release-v.${{ needs.guard.outputs.version_name }}`.
  12. **Merge the PR**: `gh pr merge ${{ needs.guard.outputs.pr_number }} --merge --delete-branch`.
  13. Use `GH_TOKEN: ${{ secrets.PUSH_TOKEN }}` for the gh commands. Use `git -c http.extraheader=...` or set up auth via `git remote set-url` with the token for the tag push.

**Critical ordering**: fastlane deploy is step 9. Tag is step 10. Merge is step 12. If any step before fastlane fails, nothing irreversible has happened. If fastlane succeeds and step 10/11/12 fail, recovery is per `release-flow.md` §8.2.

### 5.3 Things to remove from the old `deploy_prod.yml`

- The `Update Version` step (calling `update_versions.sh`).
- The `Commit files` step (no version bump to commit).
- The `Push changes` step using `ad-m/github-push-action` (no push-back to live branch).

### 5.4 Stop and report

Show me the full new file. Wait for confirmation. This is the largest change in the migration.

---

## Stage 6: Cleanup

### 6.1 Remove `.github/workflows/version_updater.yml`

Just delete it. Its function is absorbed into `cut_release.yml`.

### 6.2 Patch `.github/workflows/android_deploy_beta.yml` (race fix only)

Do **not** restructure beta. Apply only the race fix:
- Move the `Commit files` and `Push changes` steps to **before** the `Distribute app to Beta track` step.
- This means: bump version → commit → push to dev → upload to beta. If push fails, beta upload doesn't happen, and the system stays consistent.
- File a TODO comment at the top of the file:
  ```yaml
  # TODO: This workflow still pushes back to dev. The release-branch flow used by deploy_prod
  # is the long-term replacement. See documentation/release-flow.md §11. Tracked in GitHub Issue #<TBD>.
  ```

After committing, also create a GitHub Issue titled "Restructure beta deploy to use release-branch flow" with a body referencing `documentation/release-flow.md` §1 and §11. Use `gh issue create`.

### 6.3 Stop and report

Wait for confirmation before Stage 7.

---

## Stage 7: Final verification

Before declaring done:

1. Run `actionlint` (or YAML lint) on every modified workflow.
2. Run `./gradlew detekt` on the project root to ensure nothing in `build-logic` regressed (unlikely since we didn't touch Kotlin, but verify).
3. Read `documentation/release-flow.md` end to end and verify every workflow file matches what the doc says.
4. Generate a single summary commit message body listing every file changed, grouped by stage, for me to review before pushing.

Do **not** push to `dev` or `master` yourself. I will merge the migration manually as the last step described in `release-flow.md` §10 Stage 6.

---

## What I (the human) will do after you finish

1. Review your output from each stage.
2. Manually configure branch protection, the `production` environment, and `PUSH_TOKEN` scopes per `release-flow.md` §9.
3. Merge all your changes through `dev` and `master` one final time as a manual operation (the last manual master push).
4. Run a smoke release per `release-flow.md` §10 Stage 7.

If any stage encounters something that doesn't fit the doc, **stop and ask** rather than improvising. The doc is the authoritative spec.
