package com.metrolist.music.arc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** How the dot bursts. Each song always gets the same one, so a song keeps its own little ending. */
enum class PopKind {
    /** Liquid: a crown of drops and a puddle that lingers on the bar. */
    SPLASH,

    /** Jumps out like a tennis ball and bounces away along the bar. */
    TENNIS,

    /** Flies in a high arc with a fading trail, then falls past the bar. */
    COMET,

    /** Hangs under the bar and drips like water. */
    DRIP,

    /** A fine spray in all directions. */
    SPRAY,

    /** A small spurt, then a bigger one, like something giving way. */
    DOUBLE,

    /** A blast to the right that spreads in a cone, like a shotgun shot. */
    SHOTGUN,
    ;

    companion object {
        fun forSeed(seed: Int): PopKind = entries[Random(seed).nextInt(entries.size)]
    }
}

class Droplet(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    val heavy: Boolean = false,
    // Falls straight through the bar instead of landing on it.
    val fallsThrough: Boolean = false,
    val bounce: Float = 0.42f,
    // Leaves a little puddle where it lands.
    val splats: Boolean = false,
    val hasTrail: Boolean = false,
    // Seconds spent hanging and swelling before gravity takes it.
    var hang: Float = 0f,
) {
    var squash = 0f
    var resting = false
    val trailX = if (hasTrail) FloatArray(TRAIL_POINTS) else null
    val trailY = if (hasTrail) FloatArray(TRAIL_POINTS) else null
    var trailCount = 0
    private var trailHead = 0

    internal fun recordTrail() {
        val xs = trailX ?: return
        val ys = trailY ?: return
        xs[trailHead] = x
        ys[trailHead] = y
        trailHead = (trailHead + 1) % TRAIL_POINTS
        if (trailCount < TRAIL_POINTS) trailCount++
    }

    /** The point [age] steps back along the trail, where 0 is the newest. */
    inline fun trailPoint(age: Int, use: (Float, Float) -> Unit) {
        val xs = trailX ?: return
        val ys = trailY ?: return
        val index = (trailHeadIndex() - 1 - age + TRAIL_POINTS * 2) % TRAIL_POINTS
        use(xs[index], ys[index])
    }

    fun trailHeadIndex(): Int = trailHead

    companion object {
        const val TRAIL_POINTS = 16
    }
}

/** Liquid left on the bar. It spreads, sits there for a moment, then slowly fades. */
class Puddle(val x: Float, val y: Float, val targetWidth: Float, val linger: Float) {
    var width = 0f
    var age = 0f

    val alpha: Float get() = (1f - (age - linger) / PUDDLE_FADE_SECONDS).coerceIn(0f, 1f)

    companion object {
        const val PUDDLE_FADE_SECONDS = 0.9f
    }
}

/**
 * A small particle simulation in pixels. [unit] is the screen density, so the same numbers
 * look the same on any phone. Nothing here touches Compose, which keeps it easy to test.
 */
class PopSim(private val unit: Float) {
    private class Scheduled(var delay: Float, val burst: () -> Unit)

    val droplets = ArrayList<Droplet>()
    val puddles = ArrayList<Puddle>()
    private val scheduled = ArrayList<Scheduled>()

    val isAlive: Boolean get() = droplets.isNotEmpty() || scheduled.isNotEmpty() || puddles.isNotEmpty()

    fun pop(kind: PopKind, x: Float, y: Float, random: Random) {
        when (kind) {
            PopKind.SPLASH -> {
                puddles += Puddle(x, y, targetWidth = 16f * unit, linger = 1.5f)
                spray(x, y, random, count = 8, minSpeed = 180f, maxSpeed = 460f, splats = true)
                droplets += hangingDrop(x, y, radius = 2.2f, hang = 0.5f)
                scheduled += Scheduled(0.75f) { droplets += hangingDrop(x - 5f * unit, y, radius = 1.5f, hang = 0.4f) }
            }
            PopKind.TENNIS -> {
                val launch = (620f + random.nextFloat() * 100f) * unit
                val angle = -PI * (0.54 + random.nextDouble() * 0.04)
                droplets +=
                    Droplet(
                        x,
                        y,
                        cos(angle).toFloat() * launch,
                        sin(angle).toFloat() * launch,
                        4f * unit,
                        heavy = true,
                        bounce = 0.74f,
                    )
            }
            PopKind.COMET -> {
                val launch = (860f + random.nextFloat() * 160f) * unit
                val angle = -PI * (0.66 + random.nextDouble() * 0.1)
                droplets +=
                    Droplet(
                        x,
                        y,
                        cos(angle).toFloat() * launch,
                        sin(angle).toFloat() * launch,
                        3f * unit,
                        heavy = true,
                        fallsThrough = true,
                        hasTrail = true,
                    )
            }
            PopKind.DRIP -> {
                droplets += hangingDrop(x, y, radius = 3f, hang = 0.55f)
                scheduled += Scheduled(0.9f) { droplets += hangingDrop(x, y, radius = 2.2f, hang = 0.4f) }
                scheduled += Scheduled(1.6f) { droplets += hangingDrop(x, y, radius = 1.5f, hang = 0.3f) }
            }
            PopKind.SPRAY -> spray(x, y, random, count = 14, minSpeed = 260f, maxSpeed = 820f)
            PopKind.SHOTGUN -> {
                repeat(22) {
                    val angle = -PI * (0.02 + random.nextDouble() * 0.16)
                    val speed = (650f + random.nextFloat() * 900f) * unit
                    droplets +=
                        Droplet(
                            x,
                            y,
                            cos(angle).toFloat() * speed,
                            sin(angle).toFloat() * speed,
                            (0.6f + random.nextFloat() * 1.1f) * unit,
                            hasTrail = it % 4 == 0,
                        )
                }
                // The recoil leaves a little drop rolling back along the bar.
                droplets += Droplet(x, y, -160f * unit, -120f * unit, 2.2f * unit, heavy = true)
            }
            PopKind.DOUBLE -> {
                spray(x, y, random, count = 3, minSpeed = 160f, maxSpeed = 360f)
                scheduled +=
                    Scheduled(SECOND_SPURT_SECONDS) {
                        blob(x, y, random, speed = 640f, radius = 3f)
                        spray(x, y, random, count = 6, minSpeed = 260f, maxSpeed = 640f)
                    }
            }
        }
    }

    fun spray(x: Float, y: Float, random: Random, count: Int, minSpeed: Float, maxSpeed: Float, splats: Boolean = false) {
        repeat(count) {
            val angle = -PI * (0.12 + random.nextDouble() * 0.76)
            val speed = (minSpeed + random.nextFloat() * (maxSpeed - minSpeed)) * unit
            droplets +=
                Droplet(
                    x = x,
                    y = y,
                    vx = cos(angle).toFloat() * speed,
                    vy = sin(angle).toFloat() * speed,
                    radius = (0.8f + random.nextFloat()) * unit,
                    splats = splats,
                )
        }
    }

    private fun hangingDrop(x: Float, y: Float, radius: Float, hang: Float) =
        Droplet(x, y, 0f, 0f, radius * 0.4f * unit, heavy = true, fallsThrough = true, hang = hang)

    private fun blob(x: Float, y: Float, random: Random, speed: Float, radius: Float) {
        // Leans left, back along the bar, so it usually lands on the part already played.
        val angle = -PI * (0.58 + random.nextDouble() * 0.16)
        val launch = speed * (0.85f + random.nextFloat() * 0.3f) * unit
        droplets +=
            Droplet(x, y, cos(angle).toFloat() * launch, sin(angle).toFloat() * launch, radius * unit, heavy = true)
    }

    /**
     * Moves everything forward. The bar is a thin floor from [barLeft] to [barRight] at [barY],
     * and anything past [floorY] is gone. Returns how many heavy drops hit the bar this step.
     */
    fun step(seconds: Float, barLeft: Float, barRight: Float, barY: Float, floorY: Float): Int {
        val waiting = scheduled.iterator()
        val due = ArrayList<Scheduled>()
        while (waiting.hasNext()) {
            val item = waiting.next()
            item.delay -= seconds
            if (item.delay <= 0f) {
                waiting.remove()
                due += item
            }
        }
        due.forEach { it.burst() }

        val spreading = puddles.iterator()
        while (spreading.hasNext()) {
            val puddle = spreading.next()
            puddle.age += seconds
            puddle.width += (puddle.targetWidth - puddle.width) * (1f - exp(-seconds * 9f))
            if (puddle.alpha <= 0f) spreading.remove()
        }

        var impacts = 0
        val settle = exp(-seconds * SQUASH_RECOVERY)
        val gravity = GRAVITY * unit
        val iterator = droplets.iterator()
        val newPuddles = ArrayList<Puddle>(0)
        while (iterator.hasNext()) {
            val drop = iterator.next()
            drop.squash *= settle
            if (drop.hang > 0f) {
                // Swells under the bar until it's too heavy to hold on.
                drop.hang -= seconds
                drop.radius += HANG_GROWTH * unit * seconds
                drop.y += HANG_SAG * unit * seconds
                continue
            }
            if (drop.resting) {
                // A landed drop soaks into the bar.
                drop.x += drop.vx * seconds
                drop.vx *= exp(-seconds * 5f)
                drop.radius -= ABSORB_SPEED * unit * seconds
                if (drop.radius <= 0f) iterator.remove()
                continue
            }
            val wasAbove = drop.y + drop.radius <= barY
            drop.vy += gravity * seconds
            drop.x += drop.vx * seconds
            drop.y += drop.vy * seconds
            drop.recordTrail()
            val landed = wasAbove && drop.vy > 0f && drop.y + drop.radius >= barY && drop.x in barLeft..barRight
            if (landed && !drop.fallsThrough) {
                drop.y = barY - drop.radius
                if (abs(drop.vy) < REST_SPEED * unit) {
                    drop.vy = 0f
                    drop.resting = true
                } else {
                    drop.vy = -drop.vy * drop.bounce
                    drop.vx *= if (drop.bounce > 0.6f) 0.95f else 0.75f
                }
                drop.squash = 1f
                if (drop.heavy) impacts++
                if (drop.splats) newPuddles += Puddle(drop.x, barY, targetWidth = drop.radius * 5f, linger = 0.7f)
            }
            if (drop.y > floorY) iterator.remove()
        }
        puddles += newPuddles
        return impacts
    }

    private companion object {
        const val GRAVITY = 1500f
        const val REST_SPEED = 110f
        const val SQUASH_RECOVERY = 14f
        const val ABSORB_SPEED = 5f
        const val HANG_GROWTH = 3.5f
        const val HANG_SAG = 8f
        const val SECOND_SPURT_SECONDS = 0.14f
    }
}
