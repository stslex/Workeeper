#!/bin/bash

# Baseline Manager Script for Workeeper Project
# Manages centralized lint and detekt baseline files

BASELINES_DIR="$(dirname "$0")"
ROOT_DIR="$(dirname "$0")/.."

function show_help() {
    echo "Baseline Manager for Workeeper Project (Simplified)"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  list                  List baseline files (2 files total)"
    echo "  update                Update all baseline files"
    echo "  update-lint           Update only lint baseline file"
    echo "  update-detekt         Update only detekt baseline file"
    echo "  clean                 Remove all baseline files"
    echo "  stats                 Show baseline statistics"
    echo ""
    echo "Options:"
    echo "  -h, --help           Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 list"
    echo "  $0 update"
    echo "  $0 stats"
    echo ""
    echo "Note: Using simplified structure with single baseline files for all modules"
}

function list_baselines() {
    echo "=== Centralized Baseline Files ==="
    echo "Location: $BASELINES_DIR"
    echo ""

    local lint_baseline="$BASELINES_DIR/lint-baseline.xml"
    local detekt_baseline="$BASELINES_DIR/detekt-baseline.xml"

    echo "📄 Lint baseline:"
    if [ -f "$lint_baseline" ]; then
        local size=$(stat -c%s "$lint_baseline")
        local issues=$(grep -c "<issue" "$lint_baseline" 2>/dev/null || echo "0")
        echo "  ✅ lint-baseline.xml ($size bytes, $issues issues)"
    else
        echo "  ❌ lint-baseline.xml (not found)"
    fi

    echo ""
    echo "📄 Detekt baseline:"
    if [ -f "$detekt_baseline" ]; then
        local size=$(stat -c%s "$detekt_baseline")
        local issues=$(grep -c "<ID>" "$detekt_baseline" 2>/dev/null || echo "0")
        echo "  ✅ detekt-baseline.xml ($size bytes, $issues issues)"
    else
        echo "  ❌ detekt-baseline.xml (not found)"
    fi

    echo ""
    echo "📊 Total files: 2 (simplified structure)"
}

function update_lint_baselines() {
    echo "=== Updating Lint Baselines ==="
    cd "$ROOT_DIR"

    echo "Running lint to generate new baselines..."
    if ./gradlew lintDebug --continue; then
        echo "✅ Lint baselines updated successfully"
    else
        echo "⚠️  Some lint issues found - baselines may have been updated"
    fi
}

function update_detekt_baselines() {
    echo "=== Updating Detekt Baselines ==="
    cd "$ROOT_DIR"

    echo "Running detekt to generate new baselines..."
    if ./gradlew detektBaseline; then
        echo "✅ Detekt baselines updated successfully"
    else
        echo "❌ Failed to update detekt baselines"
        return 1
    fi
}

function update_all_baselines() {
    update_lint_baselines
    echo ""
    update_detekt_baselines
}

function clean_baselines() {
    echo "=== Cleaning All Baseline Files ==="
    read -p "Are you sure you want to delete all baseline files? (y/N): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        rm -f "$BASELINES_DIR/lint-baseline.xml"
        rm -f "$BASELINES_DIR/detekt-baseline.xml"
        echo "✅ All baseline files deleted"
    else
        echo "❌ Operation cancelled"
    fi
}

function show_baseline_stats() {
    echo "=== Baseline Statistics ==="

    local lint_baseline="$BASELINES_DIR/lint-baseline.xml"
    local detekt_baseline="$BASELINES_DIR/detekt-baseline.xml"

    if [ -f "$lint_baseline" ]; then
        echo "📊 Lint baseline:"
        local issues_count=$(grep -c "<issue" "$lint_baseline" 2>/dev/null || echo "0")
        local size=$(stat -c%s "$lint_baseline")
        echo "   Total issues suppressed: $issues_count"
        echo "   File size: $size bytes"
    else
        echo "❌ Lint baseline not found"
    fi

    echo ""
    if [ -f "$detekt_baseline" ]; then
        echo "📊 Detekt baseline:"
        local issues_count=$(grep -c "<ID>" "$detekt_baseline" 2>/dev/null || echo "0")
        local size=$(stat -c%s "$detekt_baseline")
        echo "   Total issues suppressed: $issues_count"
        echo "   File size: $size bytes"
    else
        echo "❌ Detekt baseline not found"
    fi
}

# Main script logic
case "$1" in
    "list"|"ls")
        list_baselines
        ;;
    "update")
        update_all_baselines
        ;;
    "update-lint")
        update_lint_baselines
        ;;
    "update-detekt")
        update_detekt_baselines
        ;;
    "clean")
        clean_baselines
        ;;
    "stats")
        show_baseline_stats
        ;;
    "-h"|"--help"|"help")
        show_help
        ;;
    "")
        show_help
        ;;
    *)
        echo "❌ Unknown command: $1"
        echo "Use '$0 --help' for usage information"
        exit 1
        ;;
esac