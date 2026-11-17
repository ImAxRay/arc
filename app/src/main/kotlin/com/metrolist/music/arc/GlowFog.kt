package com.metrolist.music.arc

import android.content.Context
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import com.metrolist.music.utils.rememberPreference
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.hct.Hct
import com.metrolist.music.ui.theme.extractThemeColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

val ArcPressGlowKey = booleanPreferencesKey("arcPressGlow")

/** Which part of a pressed tile lights up: all of it, or the square cover on top of a grid item with its title under it. */
enum class GlowArea { Whole, Cover }

/** A corner big enough to round any tile into a circle or a pill. */
val CircleGlowCorner = 999.dp

/** The screen's glow layer, if it has one; tiles on screens without one press as before. */
val LocalPressGlow = compositionLocalOf<PressGlow?> { null }

// Tuned with the user in demo/glow-fog.html; everything in dp.
private const val RISE = 0.1f
private const val FADE = 0.05f
private const val FLASH = 0.4f
private val SPILL = 24.dp
private val GRID_ITEM_PADDING = 12.dp

/**
 * One glow layer per screen, drawn over its tiles: the pressed tile blooms past its edge in its cover's colour, with a
 * band of flickering dots inside its rim on the lit side. Animates only while a tile is held and while the glow fades,
 * then draws nothing until the next press. Needs Android 13's shaders; stops in battery saver and with animations off.
 */
@Stable
class PressGlow internal constructor(private val context: Context, private val scope: CoroutineScope) {
    internal var enabled = true
    internal var dark = false
    internal var fallback = Color.White
    internal var overlay: LayoutCoordinates? = null

    private var target: LayoutCoordinates? = null
    private var area = GlowArea.Whole
    private var corner = 0f
    private var rect = Rect.Zero
    private val clock = GlowClock()
    private var tint by mutableStateOf(Color.White)
    private val shader by lazy { if (Build.VERSION.SDK_INT >= 33) GlowFogShader() else null }

    fun press(coordinates: LayoutCoordinates?, cornerPx: Float, area: GlowArea, cover: String?) {
        if (coordinates == null || !enabled || !glowMayMove(context)) return
        if (target !== coordinates) {
            clock.reset()
            rect = Rect.Zero
        }
        target = coordinates
        corner = cornerPx
        this.area = area
        val dark = dark
        tint = cover?.let { GlowTints.cached(it, dark) } ?: fallback
        if (cover != null && !GlowTints.known(cover)) {
            scope.launch {
                val loaded = GlowTints.load(context, cover, dark)
                if (target === coordinates && loaded != null) tint = loaded
            }
        }
        clock.press(scope)
    }

    fun release() = clock.release()

    internal fun DrawScope.draw() {
        val e = clock.level
        if (e <= 0f) return
        val shader = shader ?: return
        val time = clock.seconds
        val color = tint
        val o = overlay
        val c = target
        var scale = 1f
        if (o != null && c != null && o.isAttached && c.isAttached) {
            val w = c.size.width.toFloat()
            val h = c.size.height.toFloat()
            val local =
                when (area) {
                    GlowArea.Whole -> Rect(0f, 0f, w, h)
                    GlowArea.Cover -> {
                        val inset = GRID_ITEM_PADDING.toPx()
                        Rect(inset, inset, w - inset, inset + w - 2 * inset)
                    }
                }
            // read through the tile's own layer, so the glow follows its dip and lean
            val a = o.localPositionOf(c, local.topLeft)
            val b = o.localPositionOf(c, local.bottomRight)
            rect = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
            scale = if (local.width > 0f) rect.width / local.width else 1f
        } else {
            // the tile left the screen before its release came, as a quick tap that starts a song can do
            clock.release()
        }
        if (rect.isEmpty) return
        shader.set(rect, corner * scale, density, time, e, idle = 0f, dark, color)
        val spill = SPILL.toPx()
        drawIntoCanvas { it.nativeCanvas.drawRect(rect.left - spill, rect.top - spill, rect.right + spill, rect.bottom + spill, shader.paint) }
    }
}

/** Whether a glow may animate right now: not in battery saver, and not with the phone's animations turned off. */
private fun glowMayMove(context: Context, shader: Boolean = true) =
    (!shader || Build.VERSION.SDK_INT >= 33) && !Visuals.still.value &&
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/** How lit a glow is: rises while held, fades after, and runs frames only while it is doing either. */
@Stable
internal class GlowClock {
    var level by mutableFloatStateOf(0f)
        private set

    // the dots' drift carries on from press to press, so no two presses look alike
    var seconds by mutableFloatStateOf(Random.nextFloat() * 500f)
        private set
    private var held = false
    // a quick tap can lift before the first frame comes; it still rises this far before fading
    private var flash = false
    private var loop: Job? = null

    fun reset() {
        level = 0f
    }

    fun press(scope: CoroutineScope) {
        held = true
        flash = true
        if (loop?.isActive != true) loop = scope.launch { run() }
    }

    fun release() {
        held = false
    }

    private suspend fun run() {
        var last = withFrameNanos { it }
        while (held || flash || level > 0.003f) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            if (level >= FLASH) flash = false
            val goal = if (held || flash) 1f else 0f
            val rate = if (goal > level) RISE else FADE
            // the demo eased by a fixed share per 60 Hz frame; this keeps that feel at any refresh rate
            level += (goal - level) * (1f - (1f - rate).pow(dt * 60f))
            seconds = (seconds + dt) % 1000f
        }
        level = 0f
    }
}

@Composable
fun rememberPressGlow(enabled: Boolean, dark: Boolean, fallback: Color): PressGlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val glow = remember { PressGlow(context.applicationContext, scope) }
    SideEffect {
        glow.enabled = enabled
        glow.dark = dark
        glow.fallback = fallback
    }
    return glow
}

/** The layer itself; place it over the screen's content. It takes no touches. */
@Composable
fun PressGlowLayer(glow: PressGlow, modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxSize()
            .onPlaced { glow.overlay = it }
            .drawBehind { with(glow) { draw() } },
    )
}

/** How lit the dots on a surface are at rest, as a share of a press. */
private const val RESTING = 0.45f

/**
 * The glow for a surface that clips its own edge, such as the mini player: dim dots along its lit side at rest, drawn
 * once and kept, then a rim of light and brighter, flickering dots while [interactions] reports a press.
 */
@Composable
fun GlowFogSurface(cover: String?, interactions: InteractionSource, corner: Dp, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < 33) return
    val on by rememberPreference(ArcPressGlowKey, true)
    if (!on || rememberVisualsStill()) return
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fallback = MaterialTheme.colorScheme.primary
    var tint by remember { mutableStateOf(fallback) }
    LaunchedEffect(cover, dark, fallback) {
        tint = cover?.let { GlowTints.cached(it, dark) ?: GlowTints.load(context, it, dark) } ?: fallback
    }
    val clock = remember { GlowClock() }
    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            when (it) {
                is PressInteraction.Press -> if (glowMayMove(context)) clock.press(this)
                is PressInteraction.Release, is PressInteraction.Cancel -> clock.release()
            }
        }
    }
    val shader = remember { GlowFogShader() }
    Spacer(
        modifier
            .fillMaxSize()
            // kept as a finished picture, so the surface redrawing for anything else doesn't run the shader again
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                shader.set(Rect(Offset.Zero, size), corner.toPx(), density, clock.seconds, clock.level, RESTING, dark, tint)
                drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, shader.paint) }
            },
    )
}

/** How lit a plain glow is, one drawn without a shader such as the soft disc behind the player's play button. */
@Composable
fun rememberPressDisc(interactions: InteractionSource): State<Float> {
    val context = LocalContext.current
    val on = rememberPreference(ArcPressGlowKey, true).value && !rememberVisualsStill()
    val level = remember { Animatable(0f) }
    LaunchedEffect(interactions, on) {
        interactions.interactions.collect {
            when (it) {
                is PressInteraction.Press -> if (on && glowMayMove(context, shader = false)) launch { level.animateTo(1f, tween(140)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch { level.animateTo(0f, tween(700)) }
            }
        }
    }
    return level.asState()
}

/** Cover colours turned into glow colours, light enough to bloom on dark and deep enough to show on light. */
private object GlowTints {
    private const val GREY = 0
    private val seeds = LruCache<String, Int>(96)

    fun known(url: String) = seeds.get(url) != null

    fun cached(url: String, dark: Boolean): Color? = seeds.get(url)?.let { toned(it, dark) }

    suspend fun load(context: Context, url: String, dark: Boolean): Color? {
        val seed =
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(url).size(64, 64).allowHardware(false).build()
                val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull() ?: return@withContext null
                val hct = Hct.fromInt(bitmap.extractThemeColor().toArgb())
                if (hct.chroma < 8.0) GREY else hct.toInt()
            } ?: return null
        seeds.put(url, seed)
        return toned(seed, dark)
    }

    private fun toned(seed: Int, dark: Boolean): Color? {
        if (seed == GREY) return null
        val hct = Hct.fromInt(seed)
        return Color(Hct.from(hct.hue, max(hct.chroma, 40.0), if (dark) 74.0 else 50.0).toInt())
    }
}

/** demo/glow-fog.html's shader for one rounded rectangle, drawn see-through over what is already there. */
@RequiresApi(33)
internal class GlowFogShader {
    private val shader = RuntimeShader(GLOW_FOG)
    val paint = Paint().apply { shader = this@GlowFogShader.shader }

    fun set(rect: Rect, corner: Float, density: Float, seconds: Float, level: Float, idle: Float, dark: Boolean, tint: Color) {
        shader.setFloatUniform("center", rect.center.x, rect.center.y)
        shader.setFloatUniform("halfSize", rect.width / 2f, rect.height / 2f)
        shader.setFloatUniform("radius", min(corner, min(rect.width, rect.height) / 2f))
        shader.setFloatUniform("dp", density)
        shader.setFloatUniform("t", seconds)
        shader.setFloatUniform("level", level)
        shader.setFloatUniform("idle", idle)
        shader.setFloatUniform("dark", if (dark) 1f else 0f)
        shader.setColorUniform("tint", tint.toArgb())
    }
}

private const val GLOW_FOG = """
uniform float2 center;
uniform float2 halfSize;
uniform float radius;
uniform float dp;
uniform float t;
uniform float level;
uniform float idle;
uniform float dark;
layout(color) uniform half4 tint;

const float GLOW_SIZE = 4.0;
const float GLOW_AMT = 1.05;
const float INNER = 1.2;
const float SOFT = 1.5;
const float PITCH = 2.5;
const float BAND = 17.0;
const float DOT_AMT = 0.55;
const float SPEED = 0.45;
const float FLICKER = 0.5;
const float GRID = 0.7853982;

float hash(float2 p) {
    float3 p3 = fract(float3(mod(p, 289.0).xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float field(float2 w, float s) {
    float a = sin(w.x * 1.3 + s * 0.07 + 1.7 * sin(w.y * 0.9 - s * 0.053));
    float b = sin(w.y * 1.1 - s * 0.061 + 1.5 * sin(w.x * 0.7 + s * 0.041 + 2.0));
    float c = sin((w.x + w.y) * 0.8 + s * 0.047 + 1.2 * sin(w.x * 1.9 - w.y * 0.6 + s * 0.029));
    return 0.5 + 0.18 * (a + b + c);
}

float sdRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 frag) {
    float2 p = frag / dp;
    float2 q = p - center / dp;
    float2 b = halfSize / dp;
    float d = sdRR(q, b, radius / dp);
    float e = level;
    float lit = max(e, idle);
    float3 tc = tint.rgb;

    float outside = smoothstep(-SOFT, SOFT, d);
    float inside = 1.0 - outside;

    float og = e * exp(-max(d, 0.0) / GLOW_SIZE) * GLOW_AMT;
    float glowA = (1.0 - exp(-og)) * mix(0.55, 0.75, dark) * outside;

    float rim = exp(-max(-d, 0.0) / (GLOW_SIZE * 0.55)) * INNER;
    float rimA = clamp(e * 0.9 * rim, 0.0, 1.0) * mix(0.45, 0.6, dark) * inside;

    float ang = 1.9 + 0.35 * sin(t * 0.13) + 0.2 * sin(t * 0.071 + 1.0);
    float2 L = float2(cos(ang), sin(ang));
    float2 n = normalize(q / max(b, float2(1.0)) + 0.0001);
    float side = smoothstep(-0.1, 0.95, dot(n, L));
    float x = (d + BAND * 0.45) / (BAND * 0.55);
    float band = exp(-x * x);
    float smoke = smoothstep(0.3, 0.85, field(p * 0.045 + float2(t * 0.6, -t * 0.35) * SPEED, t * 2.0 * SPEED));
    float m = band * side * (0.25 + 0.75 * smoke) * lit * inside;

    float ca = cos(GRID);
    float sa = sin(GRID);
    float2 g = float2(ca * p.x - sa * p.y, sa * p.x + ca * p.y) / PITCH;
    float2 cell = floor(g);
    float2 fr = fract(g) - 0.5;
    float h1 = hash(cell);
    float h2 = hash(cell + 17.0);
    float tw = 0.5 + 0.5 * sin(t * (1.2 + 3.0 * h1) * SPEED + 6.283 * h2);
    float mm = m * mix(1.0, tw, FLICKER);
    float r = 0.5 * sqrt(clamp(mm, 0.0, 1.0));
    float px = 1.0 / (PITCH * dp);
    float dv = (1.0 - smoothstep(r - px, r + px, length(fr))) * smoothstep(0.03, 0.15, mm) * DOT_AMT;
    float3 dc = mix(tc * 0.55, mix(tc * 1.15, mix(tc, float3(1.0), 0.4), e), dark);
    float dotA = clamp(dv * mix(0.45, 0.85, e), 0.0, 1.0);

    float a1 = max(glowA, rimA);
    float alpha = dotA + a1 * (1.0 - dotA);
    float3 pm = clamp(dc, 0.0, 1.0) * dotA + tc * a1 * (1.0 - dotA);
    return half4(half3(pm), half(alpha));
}
"""
