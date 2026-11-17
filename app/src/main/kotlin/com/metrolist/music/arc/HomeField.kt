package com.metrolist.music.arc

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

val ArcHomeBackgroundKey = stringPreferencesKey("arcHomeBackground")

enum class HomeBackground { FIELD, BLADES, WAVES, OFF }

/** What Home draws behind its content; the field needs Android 13's shaders and falls back to the waves before it. */
@Composable
fun HomeBackdrop(style: HomeBackground, background: Color, tint: Color) {
    when {
        style == HomeBackground.FIELD && Build.VERSION.SDK_INT >= 33 -> HomeField(FieldLook.Lines, dark = background.luminance() < 0.5f)
        style == HomeBackground.BLADES && Build.VERSION.SDK_INT >= 33 -> HomeField(FieldLook.Blades, dark = background.luminance() < 0.5f)
        style != HomeBackground.OFF -> HomeWaves(tint)
    }
}

// Tuned on the laptop demo (demo/home-background.html, Field and Blades modes) with the user.
private enum class FieldLook(val zoom: Float, val grid: Float, val width: Float, val wash: Float, val tintMix: Float, val scale: Float) {
    // thin lines drawn sharp at full resolution
    Lines(zoom = 3.5f, grid = 0.48f, width = 0.001f, wash = 0f, tintMix = 0.42f, scale = 1f),
    // zoomed in, washed in rainbow and soft, so a picture at 15% of the screen's resolution scaled up looks the same
    Blades(zoom = 1.3f, grid = 0.57f, width = 0.0095f, wash = 0.93f, tintMix = 0.01f, scale = 0.15f),
}

private const val FPS = 9
private const val TIME_SCALE = 1.5f
private const val TURN = 0.01f

// Starts somewhere new each launch (the slice takes hours to come back round), then is kept across visits.
private var fieldSeconds = Random.nextFloat() * 20_000f

/**
 * sin over four-dimensional numbers, seen on a flat slice that turns very slowly, drawn as thin grid lines where its
 * output crosses whole numbers. Each line is white in the middle with faint rainbow edges, split wider and glowing a
 * little where it faces a slowly circling light. One small GPU shader a few times a second; it stands
 * still in battery saver or with animations off, and stops with Home.
 */
@RequiresApi(33)
@Composable
private fun HomeField(look: FieldLook, dark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val still = rememberPhoneSaver() || remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(size, look, dark, still) {
        if (size.width == 0 || size.height == 0) return@DisposableEffect onDispose {}
        val renderer = FieldRenderer(size, look, dark) { frame = it }
        val ticker = scope.launch {
            renderer.render(fieldSeconds)
            if (still) return@launch
            // waits on the frame clock, so it stops while Arc is in the background
            var last = withFrameMillis { it }
            while (true) {
                delay(1000L / FPS)
                val now = withFrameMillis { it }
                fieldSeconds += (now - last).coerceAtMost(3000L / FPS) / 1000f
                last = now
                renderer.render(fieldSeconds)
            }
        }
        onDispose {
            ticker.cancel()
            renderer.release()
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .drawBehind {
                frame?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low) }
            },
    )
}

/**
 * Draws the field into pictures of its own on the GPU, so the shader runs only when the field moves; Home redrawing for
 * anything else just shows the last picture. The picture on screen stays open until the next one is ready.
 */
@RequiresApi(33)
private class FieldRenderer(screen: IntSize, look: FieldLook, dark: Boolean, private val onFrame: (ImageBitmap) -> Unit) {
    private val size = IntSize(
        (screen.width * look.scale).roundToInt().coerceAtLeast(8),
        (screen.height * look.scale).roundToInt().coerceAtLeast(8),
    )
    private val shader = RuntimeShader(FIELD).apply {
        setFloatUniform("res", size.width.toFloat(), size.height.toFloat())
        setFloatUniform("dark", if (dark) 1f else 0f)
        setFloatUniform("zoom", look.zoom)
        setFloatUniform("grid", look.grid)
        setFloatUniform("width", look.width)
        setFloatUniform("wash", look.wash)
        setFloatUniform("tintMix", look.tintMix)
        setFloatUniform("aa", if (look.scale < 1f) 1f else 0f)
    }
    private val paint = Paint().apply { shader = this@FieldRenderer.shader }
    private val reader = ImageReader.newInstance(
        size.width,
        size.height,
        PixelFormat.RGBA_8888,
        3,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    private val node = RenderNode("HomeField").apply { setPosition(0, 0, size.width, size.height) }
    private val renderer = HardwareRenderer().apply {
        setSurface(reader.surface)
        setContentRoot(node)
        isOpaque = false
    }
    private var shown: Image? = null
    private var released = false

    init {
        reader.setOnImageAvailableListener({
            val image = it.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (released) {
                image.close()
                return@setOnImageAvailableListener
            }
            val buffer = image.hardwareBuffer
            val bitmap = buffer?.let { b -> Bitmap.wrapHardwareBuffer(b, ColorSpace.get(ColorSpace.Named.SRGB)) }
            buffer?.close()
            if (bitmap == null) {
                image.close()
                return@setOnImageAvailableListener
            }
            onFrame(bitmap.asImageBitmap())
            shown?.close()
            shown = image
        }, Handler(Looper.getMainLooper()))
    }

    fun render(seconds: Float) {
        val t = seconds * TIME_SCALE
        val a = t * TURN
        shader.setFloatUniform("t", t)
        shader.setFloatUniform("su", normalized(1f, 0.35f * sin(a * 1.3f), 0.25f * sin(a * 0.7f + 1f), 0.3f * sin(a * 0.9f + 2f)))
        shader.setFloatUniform("sw", normalized(0.2f * sin(a * 0.8f), cos(a * 0.5f), 0.7f * sin(a * 0.5f), 0.6f * sin(a * 0.5f + 0.6f)))
        shader.setFloatUniform("sc", floatArrayOf(1.2f * sin(a * 0.6f), 0.4f * sin(a * 0.45f + 1f), 0.4f * cos(a * 0.37f), 0.3f * sin(a * 0.29f + 2f)))
        val canvas = node.beginRecording()
        canvas.drawColor(android.graphics.Color.TRANSPARENT, BlendMode.CLEAR)
        canvas.drawPaint(paint)
        node.endRecording()
        renderer.createRenderRequest().syncAndDraw()
    }

    fun release() {
        released = true
        renderer.destroy()
        node.discardDisplayList()
        reader.close()
        shown = null
    }
}

private fun normalized(x: Float, y: Float, z: Float, w: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z + w * w)
    return floatArrayOf(x / l, y / l, z / l, w / l)
}

private const val FIELD = """
uniform float2 res;
uniform float t;
uniform float dark;
uniform float4 su;
uniform float4 sw;
uniform float4 sc;
uniform float zoom;
uniform float grid;
uniform float width;
uniform float wash;
uniform float tintMix;
uniform float aa;

const float GLOW = 0.09;
const float BLOOM_AT = 4.6;
const float DENSITY = 4.0;
const float SPLIT = 1.0;
const float PRISM = 0.6;
const float SHEEN = 0.6;
const float LIGHT_TURN = 0.15;
const float STRENGTH = 0.11;

// sin of a quaternion a + v: sin(a)cosh|v| + cos(a)sinh|v| v/|v|
float4 field(float2 p) {
    float4 q = sc + p.x * su + p.y * sw;
    float r = length(q.yzw);
    float e = exp(r);
    float k = r > 1e-4 ? 0.5 * (e - 1.0 / e) / r : 1.0;
    return float4(sin(q.x) * 0.5 * (e + 1.0 / e), cos(q.x) * k * q.yzw);
}

float3 spectrum(float h) { return 0.5 + 0.5 * cos(6.28318 * (h + float3(0.0, 0.33, 0.67))); }

// One family of lines: v crosses whole numbers on them and g is how fast v changes per pixel. Drawn as five copies from
// red to blue set a little apart, so the middle adds up to white and only the edges show colour, like light through
// glass; lines facing the light split more and glow a little. At low resolution (aa) a line steeper than a pixel is
// widened and dimmed to match, or the scaled-up picture shows it as a staircase.
float3 gridLine(float v, float2 g, float fade, float light) {
    float facing = 0.5 + 0.5 * cos(atan(g.y, g.x) - light);
    float gp = length(g);
    float w = width + aa * 0.25 * gp * gp;
    fade *= sqrt(width / w);
    float s = SPLIT * sqrt(width) * (1.0 + PRISM * (2.0 * facing - 1.0));
    float3 c = float3(0.0);
    float3 sum = float3(0.0);
    for (int k = 0; k < 5; k++) {
        float3 hue = spectrum(-0.67 * float(k) / 4.0);
        float d = abs(fract(v + (float(k) / 4.0 - 0.5) * s + 0.5) - 0.5);
        c += hue * exp(-d * d / w);
        sum += hue;
    }
    return c / sum * fade * (1.0 + SHEEN * (facing * facing - 0.4));
}

half4 main(float2 xy) {
    float2 p = float2(xy.x - 0.5 * res.x, 0.5 * res.y - xy.y) / res.y * zoom;
    float px = zoom / res.y;
    float4 f = field(p);
    float4 fx = field(p + float2(px, 0.0)) - f;
    float4 fy = field(p + float2(0.0, px)) - f;
    float m = length(f);
    float level = log(1.0 + m);
    float bloom = pow(smoothstep(BLOOM_AT, BLOOM_AT + 2.0, level), 2.0);
    float fade = exp(-m * 0.15);
    float light = t * LIGHT_TURN;
    float3 col = float3(GLOW * bloom);
    if (wash > 0.0) {
        // rainbow from where the output points, greyed toward white by tintMix; the soft normalise fades to grey where
        // the direction flips (yzw passing through zero) instead of jumping to the opposite colour
        float3 rainbow = mix(0.5 + 0.5 * f.yzw / (length(f.yzw) + 0.5), float3(atan(length(f.yzw), f.x) / 3.14159), 0.3);
        col += wash * mix(rainbow, float3(dot(rainbow, float3(0.333)) * 1.6), tintMix) * (0.4 + 0.6 * smoothstep(0.0, 2.0, level));
    }
    col += grid * (gridLine(f.x * DENSITY, float2(fx.x, fy.x) * DENSITY, fade, light)
            + gridLine(f.y * DENSITY, float2(fx.y, fy.y) * DENSITY, fade, light));
    col *= STRENGTH;
    float alpha = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    col = min(col, float3(alpha));
    // on a light theme the same light is drawn as ink: white lines turn dark, colours turn to their opposites
    return half4(dark > 0.5 ? col : float3(alpha) - col, alpha);
}
"""
