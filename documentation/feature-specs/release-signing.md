# Release signing — a verified version bump and an enforced `master` ruleset

**Status:** approved by the owner on 2026-10-11; amended and approved again on 2026-10-12. Ready
for implementation.

- **Specification base:** `dev` at `7007cfe8` (the runner pin, PR #327), `master` at `543f7634`
  (release 1.53.1). Evidence is `path:line` there, or the command a row names. Rows marked (API)
  were read from GitHub's API on 2026-10-12, in the discovery of the first implementation run.
  Rows marked (docs) come from GitHub's documentation. Discovery (§9) reads both again.
- **Amendment of 2026-10-12:** the first implementation run stopped in discovery, before writing
  anything. The `master` ruleset holds five rules, not three, and the `all` ruleset covers every
  branch, not only `dev` and `master`.

  Under R2 as approved on 2026-10-11, the workflow token would have moved and deleted branches
  that `all` protects, so the first dry run would have failed.

  The amendment rewrites the Problem section, F10, F11, R2 and R5, and adds F16. It updates F4 and
  F15 for the new base, and the legend of §2. It changes §4.1 steps 2 to 5 and 7 and its job
  summary, and §4.4, §4.6, §5.1, §5.3, §6, §8, §9 and §11. The PR is renamed PR-RS, because the
  KMP track's PR-R (StringResolver) uses that name.
- **Delivery:**
  - one PR into `dev` (PR-RS);
  - one owner change to the `master` ruleset;
  - three documentation commits on `dev`: this file; the enforcement documentation after the
    ruleset is read back; and, after the next release-mode cut reaches `master`, the removal of
    the `--ref dev` note (§7).

  No Android code changes.
- **Origin:** the release backlog item "rulesets, signing of the bump commit" (live sync §13), and
  the rulesets as read on 2026-10-11, while the Wear Phase 2 specification was landed.

## Problem

`CLAUDE.md` and the `signed-commits` skill say that the ruleset requires verified signatures. It
does not:

- the only branch condition of the `master` ruleset is the string `refs/heads/"master", "main"`. It
  matches no branch, so the ruleset's five rules apply nowhere: pull request, no force push,
  verified signatures, three required checks that no workflow reports any more, and an automatic
  Copilot review;
- the `dev` ruleset is disabled;
- only the `all` ruleset applies, on every branch: no deletion, and two required status checks.
  The owner bypasses it.

Fixing the condition alone would break the next release in two ways:

- `cut_release.yml` commits the version bump as `github-actions[bot]` without a signature (F3).
  `android_deploy_prod.yml` merges the release PR only after the phone bundle is live on Play (F5).
  With signatures required, that merge would fail with the release in production, its tag pushed,
  and `master` behind it.
- Once the bypass list is empty, the three stale required checks would keep every PR into
  `master` waiting for checks that never report.

## 1. Goal and success criteria

After this increment:

1. GitHub verifies every commit a release cut creates. A cut that cannot produce a verified commit
   stops before it opens a PR.
2. A release whose PR cannot merge under `master`'s rules stops before the Play upload.
3. The `master` ruleset applies to `master`: changes only through a PR, no force push, verified
   signatures only.
4. `CLAUDE.md`, the skill, `release-flow.md` and `ci-cd.md` say what GitHub enforces, and where.

Success means all of:

- the self-tests and named mutations of §5 behave as stated;
- two dry-run cuts (release and hotfix mode) are green, with a verified commit and nothing left
  behind;
- the effective rules of `master` read back as §4.4 says;
- the first real cut after PR-RS records a verified bump in §10.

## 2. Decisions

L: locked by the owner. P: proposed by this specification and approved with it on 2026-10-11; R2
and R5 as amended on 2026-10-12.

| Id | | Decision |
|---|---|---|
| R1 | L | **One change fixes both: the ruleset condition and a signed bump** (owner, 2026-10-11). |
| R2 | P | **The bump commit is made through GitHub's REST Git database API with the workflow's `GITHUB_TOKEN`, as `github-actions[bot]`:** a blob, a tree and a commit, with no author, committer or signature. GitHub signs commits that a bot makes this way (F11). The workflow token writes these objects and no ref. `PUSH_TOKEN` creates the branch at the new commit and, on cleanup, deletes it. Under `all`, only bypass actors such as the owner may delete a branch, or move one to a commit that has passed no check (F16), and a new ref may need Workflows (write) (F15). Rejected: `createCommitOnBranch` with the workflow token (approved on 2026-10-11), because it moves a branch, which `all` refuses (F16). Rejected: the same mutation with `PUSH_TOKEN`. GitHub would sign that commit, but as the owner, so a machine commit would read as the owner's own, and the branch would exist for a moment without its bump. Rejected: an SSH signing key in Actions secrets, because CI would hold a key that signs as the owner. Rejected: a GitHub Actions bypass on `all`, because every workflow could then delete branches and skip the checks. Rejected: a bypass on the `master` ruleset, because the rule would then not hold for any merge. Amended on 2026-10-12. |
| R3 | P | **A cut proves its commit before it opens the PR.** It reads the commit back and fails unless the commit is verified, has the source head as its only parent, and changes only `gradle/libs.versions.toml`. A cut deletes a branch it created if it does not open the PR. A `dry_run` input runs the same path on a throwaway branch, deletes that branch, and opens no PR. |
| R4 | P | **The deploy stops before the Play upload if the release PR cannot merge.** It checks twice: in the guard job, and again right before the upload. Each check requires `mergeable` = `MERGEABLE`, `mergeStateStatus` = `CLEAN`, `HAS_HOOKS` or `UNSTABLE`, and the PR head equal to the pinned SHA. |
| R5 | P | **The `master` ruleset targets exactly `refs/heads/master` and keeps exactly three rules:** pull request, no force push, verified signatures. Its pull request rule needs no approval, no code-owner review and no extra approval for unattributed Copilot pull requests, and it allows merge commits. Its two other rules are deleted. One is the required status checks, whose three names no workflow reports (F10). The other is the automatic Copilot code review, which has never run because the ruleset matched no branch. It has no bypass actors; in an emergency the owner edits the ruleset. The `all` and `dev` rulesets, and their bypass actors, do not change. The cut's branch writes, and the deploy's deletion of the release branch, rely on the owner's bypass of `all` (F16). Rejected: renaming the stale checks on `master` to the two real ones, because that adds a release gate this increment does not need (§8). Amended on 2026-10-12. |
| R6 | P | **The owner fixes the ruleset right after PR-RS merges, with no release in flight.** Until a release-mode cut made after that merges into `master`, every cut is dispatched with `--ref dev` (§4.5). Rejected: fixing the ruleset only after the next release, which leaves `master` unprotected until then. |
| R7 | P | **Out of scope:** release tags stay unsigned annotated tags, and `dev` gets no signature rule. The disabled beta lane keeps its local commit; if it is ever re-enabled, it must take the R2 path. |

## 3. Verified facts at the base

| Id | Fact | Evidence |
|---|---|---|
| F1 | The default branch is `master`. `gh workflow run` without `--ref`, and the Actions UI's "Use workflow from" by default, run the workflow file from it. | `git ls-remote --symref origin HEAD` |
| F2 | `cut_release.yml` runs as follows: (1) checks out the **source branch** (`dev`, or `master` for a hotfix) with `PUSH_TOKEN`, with no `path:`, so the working tree is the source branch, not the dispatch ref; (2) bumps the TOML with the source branch's `bump_version.sh`; (3) commits locally as `github-actions[bot]`, unsigned; (4) pushes the branch with the checkout's credentials; (5) opens the PR with `PUSH_TOKEN`. Its token has `contents: write` and `pull-requests: write`. | `.github/workflows/cut_release.yml:14-16,43-48,50-68,80-98,100-129` |
| F3 | No bump commit on `master` carries a signature: 1.52.1 `12bfc733`, 1.52.2 `73ec5ee7`, 1.53.0 `805e4266`, 1.53.1 `d827d112`. | `git cat-file -p` of each (no `gpgsig`); `documentation/feature-specs/wear-live-sync.md:950` |
| F4 | Every commit on `dev` that `master` does not have carries a signature (57 at the base). GitHub's merge commits carry its PGP signature, including the release merges that the deploy makes through the API with `PUSH_TOKEN` (`543f7634`, `6bf23319`). | `git rev-list origin/master..origin/dev`, then `git cat-file -p` of each |
| F5 | The deploy guard pins the head SHA and finds the release PR. The `deploy` job checks out the release branch with `PUSH_TOKEN`, then (1) uploads to Play; (2) pushes an annotated tag with those credentials, with `github-actions[bot]` as tagger; (3) merges the PR with `gh pr merge --merge --delete-branch --match-head-commit` and `PUSH_TOKEN`. `deploy_wear` runs only when `deploy` succeeded. | `.github/workflows/android_deploy_prod.yml:33-37,58-63,108-121,148-153,236-239,241-256,258-263,288-290` |
| F6 | The master → dev sync resets to `origin/master` and opens or updates a PR with `PUSH_TOKEN`. It creates no commit. | `.github/workflows/sync_master_to_dev.yml:20-40` |
| F7 | `release-flow.md` describes classic branch protection with a bypass for `github-actions[bot]` or `PUSH_TOKEN`; describes the cut as a local commit; and states that the branch is created with the bumped TOML committed atomically. | `documentation/release-flow.md:58-67,113,166-173,207-212,470-483` |
| F8 | `ci-cd.md` says that `PUSH_TOKEN` pushes the version-bump commit, and describes each release decision helper with its self-test in "Release pipeline". The unified workflow runs those self-tests. | `documentation/ci-cd.md:17,503,583,654,711`; `.github/workflows/android_build_unified.yml:499-503` |
| F9 | `CLAUDE.md` and the skill say that the ruleset requires verified signatures. | `CLAUDE.md:47-48`; `.claude/skills/signed-commits/SKILL.md:8-11` |
| F10 | (API) Three rulesets. There is no tag ruleset and no classic branch protection (`branches/master/protection` returns 404). (1) `master` (id 7777375), active. Its only include condition is the string `refs/heads/"master", "main"`, so it matches no branch. Bypass: repository roles 2 and 5, `always`. Five rules: `pull_request` (1 approval, code-owner review on, merge methods merge, squash and rebase, `require_extra_approval_for_unattributed_changes` on, the other settings off); `non_fast_forward`; `required_signatures`; `required_status_checks`, strict, for `Build project`, `Run UI Tests (34, google_apis, x86_64)` and `UI Tests Summary`; `copilot_code_review` (on push, not on drafts). No workflow on `dev` or `master` reports those three check names: `7ec3e596` (2025-11-05) removed the workflows that did. (2) `all` (id 8116593), active, on every branch (`~ALL`): `deletion`, and `required_status_checks`, not strict, for `Build and Unit Tests` and `KMP iOS kit smoke`. Bypass: repository roles 2 and 5, `always`. (3) `dev` (id 18553518), disabled, on `refs/heads/dev`. Its rules are `deletion`, `non_fast_forward`, `creation`, `update` and `pull_request`, and it has no bypass actors. `rules/branches/master` and `rules/branches/dev` both show only the two rules of `all`. The owner's push of `7fa361b8` printed "Bypassed rule violations for refs/heads/dev: 2 of 2 required status checks are expected". | `GET /repos/stslex/Workeeper/rulesets/<id>`, `rules/branches/{master,dev}` and `branches/master/protection` (with an admin token) on 2026-10-12; `git grep` on `origin/dev` and `origin/master`; `git show 7ec3e596`; the push output of `7fa361b8` |
| F11 | (docs) A bot's commit is verified only if the request is "authenticated as the GitHub App or bot" and carries no custom author, committer or signature information, "such as Commits API". The REST "Create a commit" endpoint takes `message`, `tree` and `parents`. Its author defaults to the authenticated user, and its response carries a `verification` object. `peter-evans/create-pull-request` signs commits as `github-actions[bot]` with the `GITHUB_TOKEN` through exactly this path: create the blob, tree and commit, then the ref. It documents that on this path a personal access token's commits "will *not* be signed". GraphQL `createCommitOnBranch` is different: GitHub signs its commits "if supported", authored as the token's owner. GitHub does not state outright that a bot's REST commit is signed. D1 proves it, because the guard requires `verified: true`. | "About commit signature verification"; REST reference, Git database (blobs, trees, commits); `peter-evans/create-pull-request` `src/github-helper.ts:293-464` and `docs/concepts-guidelines.md:373-382`, at `59910dc4` |
| F12 | (docs) With required signatures, an update of a branch checks every commit in its range, and unsigned commits on a PR's head branch can block its merge. | "Available rules for rulesets" |
| F13 | (docs) Events that the `GITHUB_TOKEN` triggers start no workflow run, except `workflow_dispatch` and `repository_dispatch`; a `pull_request` it opens, synchronizes or reopens starts runs that wait for approval. The release PR is opened with `PUSH_TOKEN`, so its checks run as today. | "GITHUB_TOKEN" (Actions concepts) |
| F14 | `android_deploy_beta.yml` is disabled (`if: false`). It would commit and push as `github-actions[bot]` without a signature. | `.github/workflows/android_deploy_beta.yml:1-4,22,135-154` |
| F15 | (docs) Creating a reference through REST needs "Contents (write)", or "Contents (write) and Workflows (write)"; deleting one needs "Contents (write)" only. A workflow's `permissions:` block has no workflows key, so the `GITHUB_TOKEN` can never hold Workflows (write). Reports tie that permission to a ref at a commit whose `.github/workflows` differ from the default branch. At the base, 12 workflow files differ between `master` and `dev`; 5 did before PR #327. `PUSH_TOKEN` is a PAT with the `workflow` scope. | REST reference, Git references; community discussion 121022 and datahub-project/datahub PR 20283; `git diff --stat origin/master origin/dev -- .github/workflows`; `documentation/release-flow.md:483` |
| F16 | (API, docs) `all` covers every branch, and only its bypass actors, which are repository roles, escape it. Under "Restrict deletions", "only users with bypass permissions can delete branches or tags whose name matches the pattern". Required status checks: "Choose which status checks must pass before the ref is updated. When enabled, commits must first be pushed to another ref where the checks pass." The `GITHUB_TOKEN` is not a bypass actor. So it can delete no branch, and it cannot move a branch to a commit that has not passed both checks, as a fresh bump commit has not. It cannot create a branch at such a commit either, unless the rule sets `do_not_enforce_on_create`, which discovery records. The owner's credentials bypass both rules, and `PUSH_TOKEN` acts as the owner. Today the cut pushes each new release branch, at an unchecked bump commit, with `PUSH_TOKEN`. The deploy deletes the branch with `gh pr merge --delete-branch` and `PUSH_TOKEN` (F2, F5), and no `release/*` branch remains on origin. The deploy's merges `6bf23319` and `543f7634` name the owner as author. | F10; "Available rules for rulesets"; GraphQL reference, `RequiredStatusChecksParameters`; the push output of `7fa361b8`; `git ls-remote --heads origin 'release/*'`; `git log -1 --format='%an %cn'` of both merges |

**Assumption S1**, not tested before the first release under the fixed ruleset: a PR into a branch
that requires signatures, carrying an unverified commit, has `mergeStateStatus` `BLOCKED`. GitHub
documents `BLOCKED` as "The merge is blocked" and documents that unsigned commits can block a
merge; the exact value is not documented. If S1 is false, the deploy check passes such a PR, the
merge is refused after the upload, and `release-flow.md` §8.12 applies.

## 4. Design

### 4.1 `cut_release.yml`

Inputs: `mode`, unchanged, and `dry_run` (boolean, default `false`). "Determine source branch",
"Checkout source branch" and "Bump version" do not change. Around and after them:

0. **Tools.** Right after "Checkout source branch", a second `actions/checkout` into a path nested
   in the source tree:
   - `ref: ${{ github.sha }}`, the commit of the dispatch ref;
   - `path: .ci`, sparse to `.github/scripts`;
   - `persist-credentials: false`.

   It must come after the source checkout. A checkout into the workspace root that finds no `.git`
   empties the workspace first, so a `.ci` made before it would be deleted.

   The helpers run from `.ci/.github/scripts/`. The source checkout stays the working tree and
   keeps running its own `bump_version.sh`. This is needed because the source branch lacks the
   helpers until PR-RS reaches it (F2). `.ci` and the files this step writes stay untracked. The
   commit carries only the TOML, and the diff check below is limited to it.
1. **Branch name:** `release/release-v.X.Y.Z`, or `ci/bump-dry-run-<run id>` in a dry run. In a
   dry run, the cut fails before any write unless the name matches `^ci/bump-dry-run-[0-9]+$`. The
   existing "Verify release branch does not already exist" step checks the name.
2. **Parent and expected objects:** record the source head (`git rev-parse HEAD`) and its tree
   (`git rev-parse 'HEAD^{tree}'`). Then record two expected values, locally:
   - the blob: `git hash-object gradle/libs.versions.toml`;
   - the tree: `git add gradle/libs.versions.toml`, then `git write-tree`.
3. **Commit (R2):** three writes with `GH_TOKEN: ${{ github.token }}`. Each writes an object, and
   none writes a ref:
   - `POST /repos/{owner}/{repo}/git/blobs` with the bumped TOML in base64 (`encoding: base64`).
     The returned `sha` must equal the expected blob.
   - `POST /repos/{owner}/{repo}/git/trees` with `base_tree` set to the source head's tree, and
     exactly one entry. The entry has `path` `gradle/libs.versions.toml`, the `mode` that
     `git ls-files -s` shows for it, `type` `blob`, and the blob's `sha`. The returned `sha` must
     equal the expected tree.
   - `POST /repos/{owner}/{repo}/git/commits` with `message` `chore: bump to v.X.Y.Z (code N)`
     (unchanged), the tree, and the source head as the only parent. It sends no `author`,
     `committer` or `signature`. Record the commit's sha.

   A failure or a mismatch stops the cut. No branch exists yet, so nothing is deleted. An
   unreferenced object stays in GitHub's object store, which is harmless.

   These writes need Contents (write) only. The REST reference names Workflows (write) only for
   creating a reference (F15). Branch and tag rulesets do not apply to objects. Push rulesets do
   apply to the blob and tree endpoints, but GitHub offers them only for private and internal
   repositories, and this repository is public. Discovery confirms that no ruleset targets `push`
   (§9 Q2).
4. **Branch:** create the ref at the commit with `GH_TOKEN: ${{ secrets.PUSH_TOKEN }}`:
   `gh api repos/$GITHUB_REPOSITORY/git/refs -f ref=refs/heads/$BRANCH -f sha=<commit>`. `$BRANCH`
   is the name that step 1 checked. The branch is born with its bump.

   `PUSH_TOKEN` does this for two reasons: the owner bypasses `all` (F16), and the token holds the
   `workflow` scope (F15). The workflow token could do neither.

   The step outputs the ref's name, the commit's sha and `created`:
   - `created=true` when GitHub answers 201.
   - On 422, the ref already exists: `created` stays false, and the cut fails.
   - On any other failure, the step reads `GET /repos/{owner}/{repo}/git/ref/heads/<branch>`. A lost
     response may hide a ref that was created. The commit is new in this run, so a ref pointing at
     it is this run's. If the ref points at the commit, `created=true` and the cut goes on.
     Otherwise the cut fails.
5. **Proof (R3):**
   - Write `gh api repos/$GITHUB_REPOSITORY/commits/<commit>` to `bump-commit.json`.
   - Run
     `python3 .ci/.github/scripts/release_bump_guard.py --commit bump-commit.json --parent <source head> --path gradle/libs.versions.toml`.
   - On exit 3, or when `gh api` fails with HTTP 404, read again: up to 5 times, 5 s apart. After
     the last read only exit 0 continues; anything else stops the cut.
   - Then run `git fetch origin "$BRANCH"`. `FETCH_HEAD` must be the commit, and
     `git diff --exit-code FETCH_HEAD -- gradle/libs.versions.toml` must pass: the committed file
     equals the local bump.
   - In a dry run, upload `bump-commit.json` as the artifact
     `bump-commit-attempt-${{ github.run_attempt }}` (for K2). A re-run keeps the earlier
     attempt's artifacts, and `upload-artifact@v4` refuses a name the run already holds.
6. **PR:** a real cut opens the PR exactly as today, with `PUSH_TOKEN`; the step carries
   `if: ${{ !inputs.dry_run }}`. A dry run opens none.
7. **Cleanup:** one step with `if: always()` deletes the branch with `PUSH_TOKEN`
   (`DELETE /repos/{owner}/{repo}/git/refs/heads/<branch>`). It reads only step 4's outputs, never
   the name from "Bump version". It deletes only when all of these hold:
   - step 4 output `created=true`;
   - the name matches `^(release/release-v\.[0-9]+\.[0-9]+\.[0-9]+|ci/bump-dry-run-[0-9]+)$`;
   - in a dry run, the name is exactly `ci/bump-dry-run-<this run's id>`;
   - read just before the delete, the ref still points at step 4's commit;
   - either the run is a dry run, or the PR step did not succeed.

   Under `all`, only bypass actors may delete a branch (F16). This delete runs as the owner, so
   GitHub no longer stops a wrong delete; these conditions are what keep it to this run's
   branch.

   This covers failure, cancellation and timeout. It does not cover a forced cancel or a lost
   runner; then delete the branch by hand (`release-flow.md` §8.4). It never touches a branch
   this run did not create.
8. **Removed:** the "Configure git identity" step, and the local commit and push of the branch.

The job summary prints:
- the commit oid, with its `verified` and `reason`;
- the result of the two sha checks of step 3;
- in a dry run, that the branch was deleted.

### 4.2 `release_bump_guard.py`

`.github/scripts/release_bump_guard.py` uses the standard library only, in the style of
`wear_track_decision.py`:

    python3 .github/scripts/release_bump_guard.py --commit <json> --parent <sha> --path <path>
    python3 .github/scripts/release_bump_guard.py --self-test

`<json>` is the REST commit (`GET /repos/{owner}/{repo}/commits/{sha}`).

- `DECISION PASS` (exit 0) requires all of:
  - `commit.verification.verified` is `true`;
  - `parents` holds exactly one entry, whose `sha` is `<parent>`;
  - `files` holds exactly one entry, with `filename` = `<path>` and `status` = `modified`.
- `DECISION RETRY` (exit 3): `commit.verification.reason` is `gpgverify_unavailable` or
  `gpgverify_error`. Both mean that GitHub could not check the signature yet.
- `DECISION FAIL: <reason>` (exit 1) otherwise. The reason names the failed check and
  `commit.verification.reason`.
- Exit 2: unreadable input.

The self-test runs 9 fixtures:

- PASS;
- unverified, reason `unsigned`: FAIL;
- `gpgverify_unavailable`: RETRY;
- `gpgverify_error`: RETRY;
- two parents: FAIL;
- another parent: FAIL;
- two files: FAIL;
- another file: FAIL;
- status `added`: FAIL.

It exits 0 only when each fixture gives its expected decision.

### 4.3 `release_pr_guard.py` and the deploy checks

`.github/scripts/release_pr_guard.py` uses the standard library only:

    python3 .github/scripts/release_pr_guard.py --pr <json> --head <sha>
    python3 .github/scripts/release_pr_guard.py --self-test

`<json>` is `gh pr view <n> --json number,mergeable,mergeStateStatus,headRefOid`.

- PASS (exit 0) requires all of:
  - `mergeable` is `MERGEABLE`;
  - `mergeStateStatus` is `CLEAN`, `HAS_HOOKS` or `UNSTABLE`;
  - `headRefOid` equals `<sha>`.
- RETRY (exit 3) when either field is `UNKNOWN` and the head matches.
- FAIL (exit 1) otherwise, printing every value.
- Exit 2: unreadable input.

The self-test runs 25 fixtures: each pair of `mergeable` (`MERGEABLE`, `CONFLICTING`, `UNKNOWN`) and
`mergeStateStatus` (`BEHIND`, `BLOCKED`, `CLEAN`, `DIRTY`, `DRAFT`, `HAS_HOOKS`, `UNKNOWN`,
`UNSTABLE`) with a matching head, plus one passing pair with a moved head.

`android_deploy_prod.yml` runs it twice, both times with `PUSH_TOKEN`, the guard's PR number and
the pinned SHA. On RETRY it reads the PR again, up to 6 times, 10 s apart, then fails. A failure
names `release-flow.md` §8.11.

- **Guard job:** a step right after "Find open release PR for this branch". The guard job checks
  out the release branch, so the helper is there for any release branch that contains PR-RS.
- **Deploy job:** a step right before "Distribute app to Prod track". It catches a push to the
  release branch, or a rule change, during the run's build and UI tests.

The unified workflow's step "Self-test the release decision helpers" runs both new self-tests.

### 4.4 The `master` ruleset (owner action, §7 step 3)

Precondition: no release in flight. No PR is open into `master`, and no `release/*` branch exists.
A release cut before PR-RS has no deploy checks, so its merge would be refused after the upload.

Target state of the ruleset `master` (id 7777375, or the id discovery finds):

- **Enforcement:** active.
- **Target:** branch. Include exactly `refs/heads/master`; exclude nothing.
- **Rules,** exactly three:
  - `pull_request`: required approving reviews 0. These settings are off: dismiss stale reviews,
    code-owner review, last-push approval, thread resolution, and the additional approval for
    unattributed Copilot pull requests (`require_extra_approval_for_unattributed_changes`). The
    allowed merge methods include `merge`.
  - `non_fast_forward`.
  - `required_signatures`.
- **Deleted:** `required_status_checks` (the three stale names of F10) and `copilot_code_review`.
- **Bypass actors:** none.

GitHub documents that the additional approval for unattributed Copilot pull requests has no effect
when a ruleset requires zero approvals. Copilot does not open release PRs either. The setting is
still turned off, so that the release merge does not depend on a setting in public preview.

In the UI: Settings → Rules → Rulesets → `master`.

1. Under Target branches, delete the existing entry.
2. Add target → Include by pattern → `master`.
3. Empty the Bypass list.
4. Under "Require a pull request before merging", set the required approvals to 0. Clear the
   code-owner review, and the additional approval for unattributed Copilot pull requests.
5. Clear "Require status checks to pass", and the automatic Copilot code review.
6. Check the rule settings above.
7. Save changes.

Read back:

- `gh api repos/stslex/Workeeper/rulesets/<id>`: the state above, with exactly three rules.
- `gh api repos/stslex/Workeeper/rules/branches/master`: `pull_request`, `non_fast_forward` and
  `required_signatures` from `master`; `deletion` and `required_status_checks` from `all`; nothing
  else.
- `gh api repos/stslex/Workeeper/rules/branches/dev`: the same as in discovery.

### 4.5 Until `master` holds the new cut

`master` keeps the old `cut_release.yml` until the first release-mode cut made after PR-RS merges
into `master`. A hotfix does not carry it there. Until then, dispatch every cut from `dev`:

    gh workflow run cut_release.yml --ref dev -f mode=release    # or -f mode=hotfix

In the Actions UI, set "Use workflow from" to `dev`; it defaults to `master`. The source branch
comes from the mode, not from the ref, so a hotfix is still cut from `master`; the tools come from
`dev` (§4.1 step 0).

A cut dispatched from `master` makes an unsigned bump:

- **Release mode:** the branch carries the new deploy checks. They stop the deploy before the
  upload (`release-flow.md` §8.11). Close the PR, delete the branch, and cut again from `dev`.
- **Hotfix mode:** the branch comes from `master` and has no checks. The upload succeeds and the
  merge is refused (`release-flow.md` §8.12).

### 4.6 Documentation

In PR-RS, `release-flow.md`:

- §4.4 invariant 1, "At cut time": the bump commit is made first, through the API, and the branch
  is created at it, so a release branch never exists without its bump. A cut that does not open
  its PR deletes the branch; only a forced cancel or a lost runner can leave one (§8.4).
- §6.1 step 1 and §6.2 step 1: the cut of §4.1, the dry run, and the dispatch rule of §4.5.
- §6.1 step 3.1: the PR check in the guard job, and again before the upload.
- §8.11 "Deploy stopped: the release PR cannot merge". One recovery per cause:
  - `BLOCKED` with an unverified commit. List the unverified commits with
    `gh api repos/stslex/Workeeper/compare/master...<branch> --paginate --jq '.commits[] | select(.commit.verification.verified | not) | .sha'`.
    - The cut's bump: close the PR, delete the branch, and cut again from `dev`.
    - A commit from `dev`: the owner decides between two options. (a) A history rewrite of `dev`
      that re-signs it. (b) Turn off "Require signed commits" in the `master` ruleset before
      dispatching the deploy, and turn it on again after the merge, then read the rules back. In
      (b), `master` is unprotected for the length of that run.
  - `BLOCKED` while the checks are still running: dispatch again when they are green.
  - `DIRTY` or `BEHIND`: resolve, as for any PR.
  - `DRAFT`: mark the PR ready.
  - A moved head: §8.1.
- §8.12 "Release live, merge refused by the ruleset" (owner):
  1. Turn off "Require signed commits" in the `master` ruleset.
  2. `gh pr merge <n> --merge --delete-branch --match-head-commit <pinned sha>`.
  3. Turn the rule on again, and read the rules back with
     `gh api repos/stslex/Workeeper/rules/branches/master`.
  4. Dispatch `android_deploy_wear.yml` on the tag, as §8.7 describes; `deploy_wear` did not run.
- §8.13 "Cut failed: the bump commit is not verified". Nothing was opened, and the branch was
  deleted. Read `verification.reason` in the run log, and GitHub's status page, before cutting
  again.

In PR-RS, `ci-cd.md`:

- the `cut_release.yml` row: the dry run, and the commit made through the REST Git database API;
- the `PUSH_TOKEN` row. It creates the release branch at the bump commit, and deletes a branch the
  cut created but opened no PR for. It opens and merges release PRs, pushes the release tag, and
  runs the sync. Three of these writes rely on the owner's bypass of the `all` ruleset:
  - creating a branch at the unchecked bump commit;
  - the cut's delete;
  - the merge's `--delete-branch`.

  Opening PRs and pushing the tag do not, because `all` is a branch ruleset. The workflow token
  makes the bump commit's blob, tree and commit, and writes no ref;
- "Release pipeline": a paragraph for each new helper and its self-test, next to the ones for
  `wear_track_decision.py` and `listing_drift.py`.

After the owner's ruleset change is read back, one documentation commit on `dev` changes:

- `release-flow.md` §3, "Branch protection": replaced by the three rulesets as §4.4 and F10 leave
  them. It says that `all` covers every branch. Under `all`, only its bypass actors may delete a
  branch, or move one to a commit that has not passed its checks. Unless `do_not_enforce_on_create`
  is set, the same holds for creating a branch at such a commit (F16);
- `release-flow.md` §9: the same. `PUSH_TOKEN` needs no bypass on `master`, and relies on the
  owner's bypass of `all`;
- `CLAUDE.md:47-48`, exactly:

      The `master` ruleset requires **verified signatures**, and every commit on `dev` reaches
      `master` with the next release. GitHub does not check signatures on `dev`, so an unsigned
      commit lands there silently and then blocks that release: the deploy guard stops it before
      the Play upload, and undoing it costs a history rewrite or a temporary change to the ruleset.

- `.claude/skills/signed-commits/SKILL.md:8-11`, exactly:

      The `master` ruleset requires **verified signatures**, and every commit on `dev` reaches
      `master` with the next release. An unsigned commit is not a style problem here: it blocks
      that release, and the only ways out are a history rewrite or a temporary change to the
      ruleset. Both are expensive, so the cost of getting this wrong is paid at the end of the
      task, by someone else.

The same commit adds this specification's ledger rows.

## 5. Test contract

### 5.1 Checks

- `release_bump_guard.py --self-test` (9 fixtures) and `release_pr_guard.py --self-test`
  (25 fixtures), locally and in the unified workflow's helper step.
- `actionlint` (1.7.12 or newer) on `cut_release.yml`, `android_deploy_prod.yml` and
  `android_build_unified.yml`: no findings beyond those it reports on `origin/dev`'s copies of the
  same files. Compare by file, rule and message, not by line. Print both.
- Tokens, statically, in the PR head's `cut_release.yml`, printing each step with its token:
  - every step that writes or deletes a ref (`git/refs`) runs with `PUSH_TOKEN`;
  - the blob, tree and commit writes run with the workflow token;
  - no step runs `git commit` or `git push`.
- Live, against GitHub:
  - K1: the bump guard on `d827d112`, 1.53.1's unsigned bump on `master`, with its parent
    `6bf23319`, gives `DECISION FAIL` and names the verification;
  - K2: the bump guard on D1's commit, with D1's logged source head as the parent, gives
    `DECISION PASS`. Read the commit live (`commits/<oid>`). Fall back to D1's artifact only if
    GitHub no longer serves it, and say so;
  - K3: the PR guard on PR-RS itself, with its current head, gives PASS once its checks are green;
  - K4: the same with another SHA as `--head` gives FAIL.

### 5.2 Named mutations

These run after C1 is committed. Save a copy of the file, apply the mutation, and run the
self-test RED, recording its command and exit code. Copy the saved file back, and confirm with
`cmp` against the copy and with `git diff --quiet` that it is restored; never use `git checkout`
for this. Then run the self-test GREEN.

| Id | Mutation | Must fail |
|---|---|---|
| M-R1 | the bump guard ignores `verification.verified` | bump self-test, the unsigned fixture |
| M-R2 | the bump guard ignores the parent | bump self-test, another parent |
| M-R3 | the bump guard accepts two changed files | bump self-test, two files |
| M-R4 | the bump guard treats `gpgverify_error` as FAIL | bump self-test, the RETRY fixtures |
| M-R5 | the PR guard accepts `BLOCKED` | PR self-test |
| M-R6 | the PR guard treats `UNKNOWN` as PASS | PR self-test |
| M-R7 | the PR guard ignores the head | PR self-test, the moved head |

### 5.3 Dry runs, from the PR branch

- D1: `gh workflow run cut_release.yml --ref <PR-RS branch> -f mode=release -f dry_run=true`.
- D2: the same, with `-f mode=hotfix`.

Before dispatching, confirm three things in the PR head's `cut_release.yml`:
- the branch-name check of §4.1 step 1;
- the PR step's `if`;
- the cleanup's conditions of §4.1 step 7: it reads only step 4's outputs, and checks the ref's
  name and commit before it deletes.

Each run must show all of:

- the run is green;
- its log shows the two sha checks of §4.1 step 3 passing, `DECISION PASS` and the commit oid;
- the probe branch named in its log (exactly 1) no longer exists on origin;
- no PR exists for the probe branch;
- the `release/*` branches on origin and the PRs into `master` (any state) are the same as before
  the dispatch.

K2 runs on D1's commit. Run D1 and D2 again after any change to `cut_release.yml` or the bump
guard, and K3 and K4 after any change to the PR guard or the deploy workflow.

### 5.4 Gates

The PR's CI checks are green, and `actionlint` passes on every workflow the PR changes, as §5.1
defines.

## 6. Failure modes

| Situation | Result |
|---|---|
| a blob, tree or commit write fails, or a returned sha differs from the local one | the cut fails at step 3, before any branch exists, and deletes nothing |
| the ref already exists | the cut fails at step 4 and deletes nothing |
| the response to the ref creation is lost | step 4 reads the ref. If it points at this run's commit, the cut goes on as if the call had succeeded; otherwise the cut fails and deletes nothing |
| the commit is not verified | the cut fails before the PR (`release-flow.md` §8.13) and deletes its branch |
| the owner no longer bypasses `all`, or `PUSH_TOKEN` is rotated to an account without the bypass | the cut fails at step 4, unless `all` exempts creation (F16). Step 7 and the deploy can then no longer delete a branch. Restore the bypass, or rotate the token back to the owner |
| a cut dispatched from `master` while §4.5 applies | §4.5 |
| an unsigned commit reached `dev` | the deploy stops before the upload (`release-flow.md` §8.11), if S1 holds |
| the deploy was dispatched while the PR's checks were still running | the guard stops it (`BLOCKED`); dispatch again when they are green |
| a push to the release branch during the run | the check before the upload stops it |
| the merge is refused after the upload anyway | `release-flow.md` §8.12 |
| the ruleset is edited wrongly | the read-back of §4.4 fails, and the owner corrects it |

## 7. Order

1. The owner approves this file, and it lands on `dev` (a direct push).
2. PR-RS into `dev`. It contains:
   - the two helpers with their self-tests;
   - the changes to `cut_release.yml`;
   - the two deploy checks;
   - the unified workflow's helper step;
   - the PR-RS documentation of §4.6.

   D1 and D2 run before the merge. Then the bot review and an independent review. The owner
   merges with a merge commit.
3. With no release in flight, the owner edits the `master` ruleset (§4.4), and the implementer
   reads it back.
4. One documentation commit on `dev` (§4.6, after the read-back), with the ledger rows.
5. The next cut (release 1.54.0, or a hotfix before it) is dispatched from `dev`. Its bump is the
   first real verified bump, recorded in the ledger.
6. After a release-mode cut carries PR-RS into `master`, one documentation commit drops the
   `--ref dev` note.

## 8. Out of scope

- R7.
- Required status checks on `master` beyond those of `all` (R5).
- Narrowing `all` to the long-lived branches, or a GitHub Actions bypass on it (R2).
- The `production` environment: `release-flow.md` §6.1 describes an approval that the environment
  does not enforce (live sync §13).
- `CLAUDE.md`'s claim that the pre-commit hook runs detekt on every commit.

## 9. Discovery and STOP conditions

Discovery rows, each with command, evidence and verdict:

- **Q1** F1–F16 at the current `dev` and `master`. Line drift is recorded, and F4 and F15 record
  their counts.
- **Q2** The rulesets in full, with `target`, `bypass_actors`, `conditions`, the `pull_request`
  parameters, the required check names, `do_not_enforce_on_create` and `enforcement`. Also:
  - `rules/branches/master` and `rules/branches/dev`;
  - `branches/master/protection`, with an admin token: 404 expected.

  No ruleset has `target: push`. A field that F10 does not state is recorded; it is not a STOP.
- **Q3** `compare/master...dev` (paginated): every commit verified.

STOP and report when:

- a dry run does not produce a verified commit; leaves a branch or a PR behind; or changes the
  `release/*` branches or the PRs into `master`;
- GitHub rejects `dry_run` on a dispatch from the PR branch;
- the rulesets differ from F10 in a way §4.4 does not cover, such as another ruleset on `master`
  or a tag ruleset. A bypass actor on `master` is covered: §4.4 removes it. The owner's bypass on
  `all` is expected and stays, because the cut depends on it (F16);
- Q3 finds an unverified commit on `dev`;
- a release is in flight when the ruleset edit is due;
- anything would change the `all` or `dev` ruleset, a secret, an environment or the Actions
  settings;
- the work needs a change outside §4.

## 10. Implementation ledger

Append-only. One row per PR, run, owner action and release step.

| Date | Item | Commit or run | Evidence |
|---|---|---|---|

## 11. Sources

- [release-flow.md](../release-flow.md) §3, §4.4, §6, §8, §9; [ci-cd.md](../ci-cd.md);
  [wear-live-sync.md](wear-live-sync.md) §13 and its ledger.
- GitHub documentation, read on 2026-10-11 and 2026-10-12:
  - "About commit signature verification";
  - "Available rules for rulesets";
  - the GraphQL reference: Commits (`createCommitOnBranch`), Pulls (`MergeStateStatus`) and
    `RequiredStatusChecksParameters`;
  - the REST reference: Git database (blobs, trees, commits), and Git references (permissions);
  - "GITHUB_TOKEN" (Actions concepts);
  - the REST reference, Commits (verification reasons).
- `peter-evans/create-pull-request` at `59910dc4`: `src/github-helper.ts:293-464` and
  `docs/concepts-guidelines.md:373-382`, the signed-commit path for bot tokens.
