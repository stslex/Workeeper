package io.github.stslex.workeeper.core.ui.design

import androidx.compose.runtime.Composable
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

/**
 * Text family for every worded slot. Bundled rather than fetched, so the first frame is never
 * set in a fallback face; 400/500/600 ship and Bold still resolves by synthesis off the 600.
 * Composable because Compose-resource `Font(...)` is composable — see the theme adapter.
 */
@Composable
fun workeeperTextFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/**
 * Display family for numerals only — Archivo `wdth 116 / wght 700`; `licenses/README.md` has
 * its provenance. GUARD: zero Cyrillic coverage — digits and `: . , - + / %`, never a string.
 */
@Composable
fun workeeperNumericFontFamily(): FontFamily = FontFamily(
    Font(Res.font.archivo_bold_wdth116, FontWeight.Bold),
)

/**
 * Monospace family for units and meta text. Shares vertical metrics with [workeeperTextFontFamily]
 * so it co-baselines inline; tabular by default and covers Cyrillic in full.
 */
@Composable
fun workeeperMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)
