package io.github.stslex.workeeper.core.ui.test

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput

/**
 * Extension functions for common Compose testing operations
 */

/**
 * Performs text input after clearing the existing text
 */
fun SemanticsNodeInteraction.performTextReplacement(text: String): SemanticsNodeInteraction {
    performTextClearance()
    performTextInput(text)
    return this
}
