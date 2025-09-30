package io.github.stslex.workeeper.core.ui.kit.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.intellij.lang.annotations.Language
import kotlin.random.Random

@Composable
fun NoiseBox(
    modifier: Modifier = Modifier,
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .drawNoiseOrFallback(
                noiseIntensity = noiseIntensity,
                grainSize = grainSize,
                baseColor = baseColor,
                animated = animated,
            )
            .padding(paddingValues),
    ) {
        content()
    }
}

@Composable
fun NoiseColumn(
    modifier: Modifier = Modifier,
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .drawNoiseOrFallback(
                noiseIntensity = noiseIntensity,
                grainSize = grainSize,
                baseColor = baseColor,
                animated = animated,
            )
            .padding(paddingValues),
    ) {
        content()
    }
}

@Composable
fun Modifier.drawNoiseOrFallback(
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
): Modifier = if (
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    AppUi.uiFeatures.enableNoise
) {
    drawNoise(
        noiseIntensity = noiseIntensity,
        grainSize = grainSize,
        baseColor = baseColor,
        animated = animated,
    )
} else {
    background(baseColor)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun Modifier.drawNoise(
    noiseIntensity: Float = 0.05f,
    grainSize: Float = 400f,
    baseColor: Color = Color.Transparent,
    animated: Boolean = false,
): Modifier {
    val time by produceState(0f) {
        while (true) {
            withFrameMillis {
                value = it / 1000f
            }
        }
    }
    return drawBehind {
        val shader = RuntimeShader(FILM_GRAIN_SHADER).apply {
            setFloatUniform("resolution", size.width, size.height)
            setFloatUniform("time", if (animated) time else Random.nextFloat())
            setFloatUniform("intensity", noiseIntensity)
            setFloatUniform(
                "baseColor",
                baseColor.red,
                baseColor.green,
                baseColor.blue,
            )
            setFloatUniform("grainSize", grainSize)
        }

        drawRect(ShaderBrush(shader))
    }
}

@Language("AGSL")
private const val FILM_GRAIN_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float intensity;
    uniform float3 baseColor;
    uniform float grainSize;
    
    float random(float2 st) {
        return fract(sin(dot(st.xy, float2(12.9898, 78.233))) * 43758.5453123);
    }

    float grain(float2 uv, float scale) {
        float2 scaledUV = uv * scale;
        float2 i = floor(scaledUV);
        float2 f = fract(scaledUV);

        float2 u = f * f * (3.0 - 2.0 * f);
        
        float a = random(i + time);
        float b = random(i + float2(1.0, 0.0) + time);
        float c = random(i + float2(0.0, 1.0) + time);
        float d = random(i + float2(1.0, 1.0) + time);
        
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }
    
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;

        float g = 0.0;
        g += grain(uv, grainSize) * 0.5;
        g += grain(uv, grainSize * 2.0) * 0.25;
        g += grain(uv, grainSize * 4.0) * 0.125;
        g += grain(uv, grainSize * 8.0) * 0.0625;
        
        g = g * 0.5 + 0.25;

        float3 color = baseColor;
        
        float grainValue = (g - 0.5) * 2.0; 
        color = color + grainValue * intensity;

        color = clamp(color, 0.0, 1.0);
        
        return half4(color, 1.0);
    }
"""
