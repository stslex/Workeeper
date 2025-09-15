#!/bin/bash

# Setup script for git hooks

echo "Setting up git hooks..."

# Create hooks directory if it doesn't exist
mkdir -p .git/hooks

# Copy pre-commit hook
cp .githooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

echo "✅ Git hooks have been set up successfully!"
echo "To enable pre-commit linting, the pre-commit hook has been installed."
echo "To bypass pre-commit hooks (not recommended), use: git commit --no-verify"