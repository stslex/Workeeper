# UI Test Reporting - Quick Start

## What You Get

When you create a PR with UI test changes, you automatically get:

### 1. Auto PR Comment (EnricoMi)

```
🧪 UI Test Results (API 34)
8 tests   8 ✅  8s ⏱️
✅ All tests passed!
```

### 2. Three Check Reports

- 🧪 **UI Test Results** - Summary with trends
- 📊 **Detailed Test Report** - Per-test breakdown with job summary
- 📈 **Test Report** - Visual report with code annotations

### 3. Artifacts (downloadable)

- HTML test reports
- Screenshots (on failure)
- Logcat output

## How to Use

### Reading Results

1. **Quick check:** Look at PR checks ✅/❌
2. **Summary:** Read auto-comment in PR
3. **Details:** Click any check for full report
4. **Debugging:** Download artifacts if tests failed

### What Each Report Shows

**🧪 UI Test Results (EnricoMi)**

- Pass/fail counts
- Comparison with previous run
- Trend indicators (↗️/↘️)
- Direct links to failed tests

**📊 Detailed Test Report (mikepenz)**

- Per-test execution time
- Module grouping
- Stack traces
- Beautiful HTML summary in workflow

**📈 Test Report (dorny)**

- Code annotations on test files
- Filterable results
- Performance metrics
- Test trends

## Examples

### All Tests Pass

```
✅ All checks passed
📝 Comment: "8 tests ✅ All tests passed!"
```

### Some Tests Fail

```
❌ Checks failed
📝 Comment: "8 tests  6 ✅  2 ❌"
📍 Annotations on failed test files
📊 Stack traces in check details
📸 Screenshots available as artifact
```

## Viewing Reports

### In PR

1. Go to PR page
2. Click "Checks" tab at top
3. Select any report from sidebar
4. View detailed results

### In Workflow Run

1. Go to Actions tab
2. Find "Android UI Tests" workflow
3. Click on specific run
4. Scroll down for job summary
5. Download artifacts at bottom

## Configuration

All reporting is automatic! Configured in `.github/workflows/android_build_unified.yml`

**Using GitHub Actions:**

- `EnricoMi/publish-unit-test-result-action@v2`
- `mikepenz/action-junit-report@v4`
- `dorny/test-reporter@v1`

## Tips

💡 **Check runs are clickable** - Click on any check to see full details

💡 **Annotations show location** - Failed tests annotate the exact test code

💡 **Compare trends** - See if you added/removed/fixed tests vs previous run

💡 **Job summary is rich** - Workflow run page shows beautiful HTML tables

💡 **Artifacts expire** - Download within retention period:

- Test reports: 30 days
- Screenshots: 14 days
- Logcat: 7 days

## Need More Info?

See [TEST_REPORTING.md](TEST_REPORTING.md) for comprehensive documentation.
