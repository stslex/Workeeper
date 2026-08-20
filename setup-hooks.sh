#!/bin/bash

# Setup script for git hooks

echo "Setting up git hooks..."

# Point git at the TRACKED hooks directory instead of copying a snapshot into .git/hooks.
# A copy goes stale the moment the tracked hook changes: the #235 rename-blindness fix
# (--diff-filter ACM -> ACMR) reached only clones that happened to re-run this script.
# With core.hooksPath, hook fixes deploy with `git pull` — which is also what CLAUDE.md
# has described this script as doing all along. Any pre-existing copy in .git/hooks is
# shadowed by this setting and may be deleted.
git config core.hooksPath .githooks

echo "✅ Git hooks configured: core.hooksPath -> .githooks"
echo "To bypass pre-commit hooks (not recommended), use: git commit --no-verify"
