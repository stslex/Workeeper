# Check Names Reference

This document lists all check run names to avoid conflicts between workflows.

## Test Reporting Check Names

### Unit Tests (android_build.yml)

| Action | Check Name | Comment Title |
|--------|-----------|---------------|
| EnricoMi | 🧪 Unit Test Results | 🧪 Unit Test Results |
| mikepenz | 📊 Detailed Unit Test Report | - |
| dorny | 📈 Unit Test Report | - |

### UI Tests (android_ui_tests.yml)

| Action | Check Name | Comment Title |
|--------|-----------|---------------|
| EnricoMi | 🧪 UI Test Results (API 34) | 🧪 UI Test Results (API 34) |
| mikepenz | 📊 Detailed Test Report (API 34) | - |
| dorny | 📈 Test Report (API 34) | - |

## Why Different Names?

Using different `check_name` values prevents workflows from overwriting each other's results:

- ✅ **Unit Test Results** - Shows only unit test results
- ✅ **UI Test Results (API 34)** - Shows only UI test results
- ✅ No conflicts - Both appear in PR simultaneously

## Adding New Test Types

If you add more test workflows, ensure unique check names:

```yaml
- name: Publish Integration Test Results
  uses: EnricoMi/publish-unit-test-result-action@v2
  with:
    check_name: 🧪 Integration Test Results  # Must be unique!
    comment_title: 🧪 Integration Test Results
```

## Current PR Comment Structure

When both workflows run, PR will show:

```
🧪 Unit Test Results
45 tests   45 ✅  12s ⏱️

🧪 UI Test Results (API 34)
8 tests   8 ✅  8s ⏱️
```

Each comment is separate and does not overwrite the other.

## Check Run Order in PR

Checks appear in alphabetical order:

1. 📈 Test Report (API 34)
2. 📈 Unit Test Report
3. 📊 Detailed Test Report (API 34)
4. 📊 Detailed Unit Test Report
5. 🧪 UI Test Results (API 34)
6. 🧪 Unit Test Results

## Troubleshooting

### Check gets overwritten
**Solution:** Ensure `check_name` is unique across all workflows

### Multiple comments with same title
**Solution:** Use different `comment_title` values

### Can't find specific test results
**Solution:** Check the emoji prefix and test type in check name
