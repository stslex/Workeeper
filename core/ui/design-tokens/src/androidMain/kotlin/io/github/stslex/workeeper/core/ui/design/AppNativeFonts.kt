package io.github.stslex.workeeper.core.ui.design

import android.content.Context
import android.graphics.Typeface
import io.github.stslex.workeeper.core.ui.design.resources.Res

data class AppNativeFonts(val text: Typeface, val numeric: Typeface, val mono: Typeface)

fun workeeperNativeFonts(context: Context): AppNativeFonts = AppNativeFonts(
    text = bundledTypeface(context, "ibm_plex_sans_regular.ttf"),
    numeric = bundledTypeface(context, "archivo_bold_wdth116.ttf"),
    mono = bundledTypeface(context, "ibm_plex_mono_regular.ttf"),
)

private fun bundledTypeface(context: Context, name: String): Typeface {
    val uri = Res.getUri("font/$name")
    check(uri.startsWith(ASSET_PREFIX)) { "Expected an Android bundled font resource" }
    return Typeface.createFromAsset(context.assets, uri.removePrefix(ASSET_PREFIX))
}

private const val ASSET_PREFIX = "file:///android_asset/"
