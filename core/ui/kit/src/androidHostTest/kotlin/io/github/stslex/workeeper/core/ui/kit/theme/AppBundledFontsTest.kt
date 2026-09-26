package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.stslex.workeeper.core.ui.design.resources.Res
import io.github.stslex.workeeper.core.ui.design.resources.archivo_bold_wdth116
import io.github.stslex.workeeper.core.ui.design.resources.ibm_plex_mono_medium
import io.github.stslex.workeeper.core.ui.design.resources.ibm_plex_mono_regular
import io.github.stslex.workeeper.core.ui.design.resources.ibm_plex_mono_semibold
import io.github.stslex.workeeper.core.ui.design.resources.ibm_plex_sans_medium
import io.github.stslex.workeeper.core.ui.design.resources.ibm_plex_sans_regular
import io.github.stslex.workeeper.core.ui.design.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
@OptIn(ExperimentalTestApi::class)
internal class AppBundledFontsTest {

    @Test
    fun bundledFamiliesReachTheirPhoneRoles() = runComposeUiTest {
        lateinit var actual: AppTypography
        lateinit var text: FontFamily
        lateinit var numeric: FontFamily
        lateinit var mono: FontFamily
        setContent {
            actual = rememberAppTypography()
            text = FontFamily(
                Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
                Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium),
                Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
            )
            numeric = FontFamily(Font(Res.font.archivo_bold_wdth116, FontWeight.Bold))
            mono = FontFamily(
                Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
                Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
                Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
            )
        }
        waitForIdle()
        assertEquals(text, actual.text.body.fontFamily)
        assertEquals(text, actual.text.title.fontFamily)
        assertEquals(numeric, actual.numeric.display.fontFamily)
        assertEquals(numeric, actual.dataValue.fontFamily)
        assertEquals(mono, actual.mono.meta.fontFamily)
        assertEquals("tnum", actual.numeric.display.fontFeatureSettings)
    }
}
