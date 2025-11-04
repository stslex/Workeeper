# UI Testing Quick Start Guide

Quick reference for writing UI tests in the Workeeper project.

## Setup

### 1. Add test-utils dependency to your feature module

```kotlin
// feature/your-feature/build.gradle.kts
dependencies {
    // ... other dependencies
    androidTestImplementation(project(":core:ui:test-utils"))
}
```

### 2. Extend BaseComposeTest

```kotlin
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest

@RunWith(AndroidJUnit4::class)
class MyFeatureScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ... tests
}
```

## Basic Test Structure

```kotlin
@RunWith(AndroidJUnit4::class)
class MyFeatureScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun myFeature_action_expectedResult() {
        // 1. Setup state
        val state = MyStore.State.INITIAL.copy(
            // ... customize state
        )

        // 2. Setup action capture using BaseComposeTest utility
        val actionCapture = ActionCapture<MyStore.Action>()

        // 3. Set content with transitions (using setTransitionContent extension)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            MyFeatureWidget(
                state = state,
                modifier = modifier,
                consume = actionCapture.consume,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope
            )
        }

        // 4. Wait for composition
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // 5. Perform actions
        composeTestRule
            .onNodeWithTag("MyButton")
            .performClick()

        // 6. Verify results
        composeTestRule
            .onNodeWithTag("MyText")
            .assertIsDisplayed()

        // 7. Verify actions using ActionCapture
        actionCapture.assertCaptured { it is MyStore.Action.Click.MyButton }
            ?: error("Button click was not captured")
    }
}
```

## Common Test Patterns

### Testing with SharedTransitionScope (Using BaseComposeTest)

```kotlin
// ✅ Recommended: Use setTransitionContent extension from BaseComposeTest
composeTestRule.setTransitionContent { animatedContentScope, modifier ->
    MyWidget(
        state = state,
        modifier = modifier,
        consume = {},
        sharedTransitionScope = this,
        animatedContentScope = animatedContentScope,
        lazyState = rememberLazyListState()
    )
}
```

### Testing with Manual Clock Control

```kotlin
// Disable auto-advance for manual control
composeTestRule.mainClock.autoAdvance = false

// Advance time and wait
composeTestRule.mainClock.advanceTimeBy(100)
composeTestRule.waitForIdle()

// Re-enable if needed
composeTestRule.mainClock.autoAdvance = true
```

### Testing Paging Data (Using PagingTestUtils)

```kotlin
import io.github.stslex.workeeper.core.ui.test.PagingTestUtils

// ✅ Recommended: Use PagingTestUtils
val mockItems = listOf(/* ... */)
val state = MyStore.State(
    items = { PagingTestUtils.createPagingFlow(mockItems) }
)

// Empty state
val emptyState = MyStore.State(
    items = { PagingTestUtils.createEmptyPagingFlow() }
)
```

### Testing Text Input (Using ComposeTestUtils)

```kotlin
import io.github.stslex.workeeper.core.ui.test.performTextReplacement

// Type text
composeTestRule
    .onNodeWithTag("MyTextField")
    .performTextInput("test text")

// ✅ Recommended: Clear and replace in one call
composeTestRule
    .onNodeWithTag("MyTextField")
    .performTextReplacement("new text")
```

### Testing List Items (Using MockDataFactory)

```kotlin
import io.github.stslex.workeeper.core.ui.test.MockDataFactory

// ✅ Recommended: Use MockDataFactory for consistent data
val uuids = MockDataFactory.createUuids(10)
val names = MockDataFactory.createTestNames("Item", 10)
val items = List(10) { index ->
    MyItem(
        uuid = uuids[index],
        name = names[index]
    )
}

// Verify items exist
items.forEach { item ->
    composeTestRule
        .onNodeWithTag("MyItem_${item.uuid}")
        .assertExists()
}

// Scroll to specific item
composeTestRule
    .onNodeWithTag("MyList")
    .performScrollToIndex(5)
```

### Testing Dialogs

```kotlin
// Test dialog open state
val state = MyStore.State.INITIAL.copy(
    dialogState = DialogState.Open
)

composeTestRule.setContent {
    MyWidget(state = state, consume = {})
}

composeTestRule
    .onNodeWithTag("MyDialog")
    .assertIsDisplayed()

// Test dialog closed state
val closedState = MyStore.State.INITIAL.copy(
    dialogState = DialogState.Closed
)

composeTestRule
    .onNodeWithTag("MyDialog")
    .assertDoesNotExist()
```

## Edge Cases to Test

### 1. Empty States
```kotlin
@Test
fun myFeature_emptyState_displaysCorrectly() {
    val state = MyStore.State(
        items = { flowOf(PagingData.from(emptyList())) }
    )

    composeTestRule.setContent {
        MyWidget(state = state, consume = {})
    }

    composeTestRule
        .onNodeWithTag("EmptyState")
        .assertIsDisplayed()
}
```

### 2. Very Long Text
```kotlin
@Test
fun myFeature_longName_displaysCorrectly() {
    val longName = "A".repeat(200)
    val state = MyStore.State.INITIAL.copy(
        name = PropertyHolder.StringProperty.new(longName)
    )

    // Should not crash
    composeTestRule.setContent {
        MyWidget(state = state, consume = {})
    }
}
```

### 3. Special Characters
```kotlin
@Test
fun myFeature_specialCharacters_handledCorrectly() {
    val specialName = "Test @#$%^&*() 123 émojis 🎉"
    val item = MyItem(
        uuid = Uuid.random().toString(),
        name = specialName
    )

    composeTestRule.setContent {
        MyWidget(items = listOf(item), consume = {})
    }

    composeTestRule
        .onNodeWithTag("MyItem_${item.uuid}")
        .assertExists()
}
```

### 4. Large Lists
```kotlin
@Test
fun myFeature_largeList_performsWell() {
    val largeList = List(100) { index ->
        MyItem(uuid = Uuid.random().toString(), name = "Item $index")
    }

    val state = MyStore.State(
        items = { flowOf(PagingData.from(largeList)) }
    )

    composeTestRule.setContent {
        MyWidget(state = state, consume = {})
    }

    // Verify list renders
    composeTestRule
        .onNodeWithTag("MyList")
        .assertIsDisplayed()
}
```

### 5. Rapid Clicks
```kotlin
@Test
fun myFeature_rapidClicks_handlesGracefully() {
    var clickCount = 0

    composeTestRule.setContent {
        MyWidget(
            state = MyStore.State.INITIAL,
            consume = { action ->
                if (action is MyStore.Action.Click) {
                    clickCount++
                }
            }
        )
    }

    // Perform rapid clicks
    repeat(10) {
        composeTestRule
            .onNodeWithTag("MyButton")
            .performClick()
    }

    assert(clickCount > 0)
}
```

## Accessibility Testing

```kotlin
@Test
fun myFeature_button_hasClickAction() {
    composeTestRule.setContent {
        MyWidget(state = MyStore.State.INITIAL, consume = {})
    }

    // Verify button has click action (accessibility)
    composeTestRule
        .onNodeWithTag("MyButton")
        .assertHasClickAction()
}

@Test
fun myFeature_textField_hasTextInputAction() {
    composeTestRule.setContent {
        MyWidget(state = MyStore.State.INITIAL, consume = {})
    }

    // Verify text field has text input semantics
    composeTestRule
        .onNodeWithTag("MyTextField")
        .assertExists()
}
```

## Common Assertions

```kotlin
// Existence
.assertExists()
.assertDoesNotExist()

// Visibility
.assertIsDisplayed()
.assertIsNotDisplayed()

// Text
.assertTextEquals("Expected Text")
.assertTextContains("Partial")

// State
.assertIsEnabled()
.assertIsNotEnabled()
.assertIsSelected()
.assertIsNotSelected()

// Semantics
.assertHasClickAction()
.assertHasNoClickAction()
```

## Common Actions

```kotlin
// Click
.performClick()

// Text
.performTextInput("text")
.performTextClearance()
.performTextReplacement("new text")

// Scroll
.performScrollTo()
.performScrollToIndex(5)
.performScrollToKey("key")

// Gesture
.performTouchInput { swipeLeft() }
.performTouchInput { swipeUp() }
```

## Tips & Best Practices

### 1. Use Test Tags
Always add test tags to composables:
```kotlin
Text(
    text = "Hello",
    modifier = Modifier.testTag("MyText")
)
```

### 2. Test Tag Naming Convention
- Screen: `"MyFeatureScreen"`
- Widget: `"MyFeatureWidget"`
- Button: `"MyFeatureButton"`, `"MyFeatureSaveButton"`
- List: `"MyFeatureList"`
- List Item: `"MyFeatureItem_${item.uuid}"`
- Dialog: `"MyFeatureDialog"`

### 3. Mock Data Creation (Using MockDataFactory)
```kotlin
import io.github.stslex.workeeper.core.ui.test.MockDataFactory

// ✅ Recommended: Use MockDataFactory
private fun createMockItems(count: Int): List<MyItem> {
    val uuids = MockDataFactory.createUuids(count)
    val names = MockDataFactory.createTestNames("Item", count)
    return List(count) { index ->
        MyItem(
            uuid = uuids[index],
            name = names[index],
            dateProperty = MockDataFactory.createDateProperty()
        )
    }
}
```

### 4. Action Verification (Using ActionCapture)
```kotlin
// ✅ Recommended: Use ActionCapture from BaseComposeTest
val actionCapture = ActionCapture<MyStore.Action>()

// In widget
consume = actionCapture.consume

// Verify action was captured
actionCapture.assertCaptured { it is MyStore.Action.Click.MyButton }
    ?: error("Button click was not captured")

// Or assert exact action
actionCapture.assertCapturedExactly(MyStore.Action.Click.MyButton)

// Or check count
actionCapture.assertCapturedCount(1)
```

### 5. Wait for Idle
Always wait for composition to complete:
```kotlin
composeTestRule.waitForIdle()
```

### 6. Test Independence
Each test should be independent:
- Don't rely on test execution order
- Create fresh state for each test
- Clean up after tests if needed

## Running Tests

```bash
# Run all UI tests
./gradlew connectedDebugAndroidTest

# Run specific feature tests
./gradlew :feature:my-feature:connectedDebugAndroidTest

# Run with logs
./gradlew connectedDebugAndroidTest --info

# Run specific test class
./gradlew connectedDebugAndroidTest \
  --tests "*.MyFeatureScreenTest"

# Run specific test method
./gradlew connectedDebugAndroidTest \
  --tests "*.MyFeatureScreenTest.myFeature_action_expectedResult"
```

## Debugging Tests

### Enable Verbose Logging
```bash
./gradlew connectedDebugAndroidTest --info --stacktrace
```

### Check Test Reports
```bash
# HTML reports
open feature/my-feature/build/reports/androidTests/connected/index.html

# XML reports
cat feature/my-feature/build/outputs/androidTest-results/connected/*.xml
```

### Common Issues

1. **Node not found**: Check test tag spelling
2. **Test timeout**: Increase timeout or add manual clock control
3. **Flaky test**: Add explicit waits or synchronization
4. **Composition error**: Verify state is valid
5. **Action not captured**: Ensure action is actually triggered

## References

- [Jetpack Compose Testing Docs](https://developer.android.com/jetpack/compose/testing)
- [Compose Test Cheat Sheet](https://developer.android.com/jetpack/compose/testing-cheatsheet)
- [Paging Testing Guide](https://developer.android.com/topic/libraries/architecture/paging/test)

---

**Last Updated**: 2025-11-04
