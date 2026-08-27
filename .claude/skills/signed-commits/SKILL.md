---
name: signed-commits
description: Sign and verify every Git commit created or rewritten in this repository. Invoke before commit, amend, merge, rebase, cherry-pick, revert, am, pull, or push operations that can introduce commits.
---

# Signed commits

The repository ruleset requires **verified signatures**. An unsigned commit is not a style
problem here: it cannot merge, and the only ways out are a maintainer bypass or a history
rewrite. Both are expensive, so the cost of getting this wrong is paid at the end of the task,
by someone else.

Every commit you create must be signed **and** show `verified: true` on GitHub. Verification is
GitHub's answer, not yours — a local signature header proves the commit was signed, not that the
key is registered to the account.

## Before you create any commit

1. **Record the task's starting SHA.** You need it later to enumerate exactly the commits you
   created:

   ```bash
   base_sha=$(git rev-parse HEAD)
   ```

   Derive it from the actual task — the branch point of the work you are starting. On a stacked
   branch that is the branch below you, not `dev`. Never hardcode a SHA from a previous task.

2. **Confirm repo-local signing is on:**

   ```bash
   git config --local --get commit.gpgsign   # must print: true
   ```

   If it does not, set exactly that one key: `git config --local commit.gpgsign true`. Do not
   change `user.name`, `user.email`, or global configuration to make signing work.

## Creating commits

Sign explicitly on every commit-producing command. `commit.gpgsign=true` is a safety net, not a
substitute — a stray `-c commit.gpgSign=false` or a rebase path can quietly bypass it.

| Command | Signing flag |
|---|---|
| `git commit` | `-S` |
| `git merge` (non-ff) | `-S` |
| `git cherry-pick` | `-S` |
| `git revert` | `-S` |
| `git rebase` | `--gpg-sign` |
| `git am` | `-S` |
| `git commit-tree` | `-S` |

**`-S` is not `-s`.** Lowercase `-s` adds a `Signed-off-by:` trailer and signs nothing. They are
one keystroke apart and mean entirely different things.

**Never** pass `--no-gpg-sign`, and never run a commit-producing command under
`-c commit.gpgSign=false`.

## Verify every SHA you created — locally, then on GitHub

Local check, immediately after each commit. This reads the raw object, so it cannot be fooled by
a porcelain format:

```bash
git cat-file commit "$sha" | grep -Eq '^gpgsig(-sha256)? ' \
  || { echo "UNSIGNED: $sha" >&2; exit 1; }
```

Do not rely on `git verify-commit` as the gate: SSH signing needs
`gpg.ssh.allowedSignersFile` to verify locally, and a valid setup may not have one even though
GitHub verifies the signature perfectly.

## Pushing

Fetch and prove ancestry before pushing, so the push is genuinely fast-forward:

```bash
git fetch origin <branch>
git merge-base --is-ancestor origin/<branch> HEAD   # must succeed
git push origin <branch>                            # explicit ref; refuses non-ff by default
```

Never use `--force`, `--force-with-lease`, or `+refs/...`. After pushing, prove the remote moved
where you think:

```bash
[ "$(git rev-parse HEAD)" = "$(git ls-remote origin refs/heads/<branch> | cut -f1)" ]
```

## GitHub verification — the authoritative gate

Check **every SHA you created**, not just HEAD. A mid-stack unsigned commit blocks the merge just
as effectively as an unsigned tip:

```bash
for sha in $(git rev-list "$base_sha"..HEAD); do
  gh api "repos/stslex/Workeeper/git/commits/$sha" \
    --jq '"\(.sha[0:9]) verified=\(.verification.verified) reason=\(.verification.reason) at=\(.verification.verified_at)"'
done
```

Success requires `verification.verified == true` for each. Report `reason` and `verified_at`.

A brief post-push `404`, `gpgverify_error`, or `gpgverify_unavailable` is GitHub still catching
up — retry a few times with a short delay. Anything else is permanent.

**A permanent verification failure stops the task.** Report it. Do not auto-amend, rebase,
rewrite, bypass the ruleset, change identity or configuration, or upload a key to make it go
away — each of those either destroys reviewed history or silently changes what the maintainer
agreed to.

## If signing itself fails

Stop and report the blocker rather than falling back to an unsigned commit. Common causes worth
naming precisely: no signing key resolvable for the configured identity; a passphrase prompt that
cannot appear in a non-interactive environment; or a key that signs locally but is not registered
on the account as a **signing** key (GitHub keeps authentication and signing keys separate, so a
working `git push` proves nothing about verification).
