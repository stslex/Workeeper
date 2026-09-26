package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography
import io.github.stslex.workeeper.core.ui.design.AppDesignDimensions
import io.github.stslex.workeeper.core.ui.design.workeeperMonoFontFamily
import io.github.stslex.workeeper.core.ui.design.workeeperNumericFontFamily
import io.github.stslex.workeeper.core.ui.design.workeeperTextFontFamily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class WearBrandThemeTest {

    @Test
    fun textNumeralsAndAuxiliaryRolesUseTheirSharedFamilies() = runComposeUiTest {
        lateinit var actual: Typography
        lateinit var shapes: Shapes
        lateinit var text: FontFamily
        lateinit var numeric: FontFamily
        lateinit var mono: FontFamily
        setContent {
            text = workeeperTextFontFamily()
            numeric = workeeperNumericFontFamily()
            mono = workeeperMonoFontFamily()
            WearAppTheme {
                actual = MaterialTheme.typography
                shapes = MaterialTheme.shapes
            }
        }
        waitForIdle()
        listOf(
            actual.displayLarge, actual.displayMedium, actual.displaySmall,
            actual.titleLarge, actual.titleMedium, actual.titleSmall,
            actual.bodyLarge, actual.bodyMedium, actual.bodySmall,
            actual.labelLarge, actual.labelMedium, actual.labelSmall,
        ).forEach { assertEquals(text, it.fontFamily) }
        listOf(
            actual.numeralExtraLarge,
            actual.numeralLarge,
            actual.numeralMedium,
            actual.numeralSmall,
            actual.numeralExtraSmall,
        ).forEach {
            assertEquals(numeric, it.fontFamily)
            assertEquals("tnum", it.fontFeatureSettings)
        }
        assertEquals(mono, actual.bodyExtraSmall.fontFamily)
        assertEquals(mono, actual.arcSmall.fontFamily)
        assertEquals(RoundedCornerShape(AppDesignDimensions.Shape.small), shapes.small)
        assertEquals(RoundedCornerShape(AppDesignDimensions.Shape.medium), shapes.medium)
        assertEquals(RoundedCornerShape(AppDesignDimensions.Shape.large), shapes.large)
    }
}
