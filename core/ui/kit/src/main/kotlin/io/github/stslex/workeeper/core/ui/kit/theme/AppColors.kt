package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Suppress("MagicNumber")
@Immutable
data class SetTypeColors(
    val warmupBackground: Color,
    val warmupForeground: Color,
    val workBackground: Color,
    val workForeground: Color,
    val failureBackground: Color,
    val failureForeground: Color,
    val dropBackground: Color,
    val dropForeground: Color,
)

@Suppress("MagicNumber")
@Immutable
data class StatusColors(
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

@Suppress("MagicNumber")
@Immutable
data class RecordColors(
    val background: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
)

@Suppress("LongParameterList")
@Immutable
data class AppColors(
    val accent: Color,
    val onAccent: Color,
    val accentTintedBackground: Color,
    val accentTintedForeground: Color,
    val surfaceTier0: Color,
    val surfaceTier1: Color,
    val surfaceTier2: Color,
    val surfaceTier3: Color,
    val surfaceTier4: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val borderStrong: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val setType: SetTypeColors,
    val status: StatusColors,
    val record: RecordColors,
    val isDark: Boolean,
)

@Suppress("MagicNumber")
fun provideDarkAppColors(): AppColors = AppColors(
    accent = Color(0xFF4A9B8E),
    onAccent = Color(0xFF0E0F0E),
    accentTintedBackground = Color(0xFF1F3835),
    accentTintedForeground = Color(0xFF6EB7AB),
    surfaceTier0 = Color(0xFF0E0F0E),
    surfaceTier1 = Color(0xFF16171A),
    surfaceTier2 = Color(0xFF1A1B1A),
    surfaceTier3 = Color(0xFF161E1C),
    surfaceTier4 = Color(0xFF1F2122),
    textPrimary = Color(0xFFE8E8E5),
    textSecondary = Color(0xFFB5B6B0),
    textTertiary = Color(0xFF6E6F6A),
    textDisabled = Color(0xFF4F5052),
    borderSubtle = Color(0xFF1F2122),
    borderDefault = Color(0xFF2A2C2D),
    borderStrong = Color(0xFF3F4143),
    inverseSurface = Color(0xFF1B1C1A),
    inverseOnSurface = Color(0xFFE8E8E5),
    setType = SetTypeColors(
        warmupBackground = Color(0xFF2A2316),
        warmupForeground = Color(0xFFDEAA62),
        workBackground = Color(0xFF161E1C),
        workForeground = Color(0xFF6EB7AB),
        failureBackground = Color(0xFF2A1818),
        failureForeground = Color(0xFFD58A8A),
        dropBackground = Color(0xFF241A2A),
        dropForeground = Color(0xFFB89BD8),
    ),
    status = StatusColors(
        success = Color(0xFF6EB7AB),
        warning = Color(0xFFDEAA62),
        error = Color(0xFFD58A8A),
        info = Color(0xFFB5B6B0),
    ),
    record = RecordColors(
        background = Color(0xFF2E2410),
        border = Color(0xFFE6B85C),
        textPrimary = Color(0xFFF6E2B0),
        textSecondary = Color(0xFFE6B85C),
    ),
    isDark = true,
)

@Suppress("MagicNumber")
fun provideLightAppColors(): AppColors = AppColors(
    accent = Color(0xFF4A9B8E),
    onAccent = Color(0xFFFFFFFF),
    accentTintedBackground = Color(0xFFDCEEE9),
    accentTintedForeground = Color(0xFF2A6B61),
    surfaceTier0 = Color(0xFFFAFAF8),
    surfaceTier1 = Color(0xFFF2F1ED),
    surfaceTier2 = Color(0xFFFFFFFF),
    surfaceTier3 = Color(0xFFEFF7F4),
    surfaceTier4 = Color(0xFFF8F7F3),
    textPrimary = Color(0xFF1B1C1A),
    textSecondary = Color(0xFF5A5B58),
    textTertiary = Color(0xFF7B7C77),
    textDisabled = Color(0xFFB5B6B0),
    borderSubtle = Color(0xFFE8E7E2),
    borderDefault = Color(0xFFE0E0DC),
    borderStrong = Color(0xFFC7C8C2),
    inverseSurface = Color(0xFF1B1C1A),
    inverseOnSurface = Color(0xFFE8E8E5),
    setType = SetTypeColors(
        warmupBackground = Color(0xFFF8E8C4),
        warmupForeground = Color(0xFF7A5418),
        workBackground = Color(0xFFDCEEE9),
        workForeground = Color(0xFF2A6B61),
        failureBackground = Color(0xFFF7DCDC),
        failureForeground = Color(0xFF7A2828),
        dropBackground = Color(0xFFE8DEF8),
        dropForeground = Color(0xFF5A2B7A),
    ),
    status = StatusColors(
        success = Color(0xFF2A6B61),
        warning = Color(0xFF7A5418),
        error = Color(0xFF7A2828),
        info = Color(0xFF5A5B58),
    ),
    record = RecordColors(
        background = Color(0xFFFFF6E0),
        border = Color(0xFFD08418),
        textPrimary = Color(0xFF6B3F08),
        textSecondary = Color(0xFF7A5418),
    ),
    isDark = false,
)
