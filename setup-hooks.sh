#!/bin/bash

# Setup script for git hooks.

echo "Setting up git hooks..."

# Point Git at the tracked hooks directory so hook updates apply with the next pull.
if ! git config core.hooksPath .githooks; then
    echo "❌ Failed to configure core.hooksPath. Run this script inside the repository." >&2
    exit 1
fi

echo "✅ Git hooks configured: core.hooksPath -> .githooks"
echo "To bypass pre-commit hooks (not recommended), use: git commit --no-verify"
