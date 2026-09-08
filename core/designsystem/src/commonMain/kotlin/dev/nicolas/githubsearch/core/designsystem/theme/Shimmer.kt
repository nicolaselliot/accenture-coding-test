package dev.nicolas.githubsearch.core.designsystem.theme

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fills this element as a loading placeholder: a block in [shape], lit by a highlight that sweeps
 * across it.
 *
 * A skeleton rather than a spinner, because the two say different things. A spinner says "wait";
 * a skeleton says "wait, and here is the shape of what is coming", which is why the search results
 * and the detail stats appear to settle into place rather than to replace something unrelated.
 *
 * Colours are derived from `onSurface` rather than taken from two more palette roles. One pair of
 * alphas over the content colour is legible in both schemes — dark blocks on a light surface, light
 * blocks on a dark one — where a fixed pair of surface roles has to be re-checked against every
 * container rung the placeholder might sit on.
 *
 * Each block sweeps over its own width, so a 40dp avatar and a full-width bar are in time with each
 * other but not aligned to one screen-wide light source. Aligning them would mean translating every
 * block's position into a shared coordinate space for an effect no one looks at directly. Wrap a
 * screenful of them in a [ShimmerGroup] so they share one sweep rather than one each.
 *
 * @param shape the outline to fill, matching whatever the placeholder stands in for.
 */
@Composable
public fun Modifier.shimmer(shape: Shape = MaterialTheme.shapes.small): Modifier {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = BASE_ALPHA)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = HIGHLIGHT_ALPHA)

    // The group's sweep when there is one, otherwise this block's own — a lone placeholder, like
    // the detail header's avatar, should not have to be wrapped in a group to shimmer.
    //
    // Null all the way through means the user asked for no motion, so the block is drawn unlit and
    // no infinite transition is started at all. Starting one and holding it at a fixed progress
    // would keep the frame clock awake for an animation nobody can see.
    val progress =
        LocalShimmerSweep.current
            ?: rememberShimmerSweep()
            ?: return background(color = base, shape = shape)

    return clip(shape).drawWithCache {
        val band = BAND_WIDTH.toPx()

        // Built once per size, not once per frame. A `Brush.linearGradient` rebuilt inside the
        // draw lambda looks free but is not: `ShaderBrush` caches its native shader against the
        // size it was created for, so a fresh instance every frame compiles a new Skia shader
        // every frame — for every placeholder on screen. The band is therefore fixed at
        // [-band, 0] and moved by translating the canvas instead.
        val brush =
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(-band, 0f),
                end = Offset(0f, 0f),
            )

        onDrawBehind {
            // Read inside the draw scope, not outside it: the sweep then invalidates drawing only,
            // where reading it in composition would recompose the placeholder every frame.
            val shift = shimmerBandStart(progress.value, size.width, band) + band

            translate(left = shift) {
                // Shifted back by the same amount, so the block stays put while the band moves
                // across it. The gradient clamps past both ends, so one rect paints the whole
                // block and the highlight is simply the lit part of that clamp.
                drawRect(brush = brush, topLeft = Offset(-shift, 0f), size = size)
            }
        }
    }
}

/**
 * Drives every [shimmer] placeholder inside [content] from a single sweep.
 *
 * Without this each placeholder runs its own `InfiniteTransition`, which on the search skeleton is
 * eighteen coroutines resuming every frame to compute the same number. It also makes the phase
 * guarantee real rather than incidental: blocks composed in the same frame happen to stay in step,
 * but one appearing later would sweep out of time with the rest.
 */
@Composable
public fun ShimmerGroup(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShimmerSweep provides rememberShimmerSweep(), content = content)
}

/**
 * The sweep a [ShimmerGroup] shares with the placeholders inside it, or null outside one — and also
 * null when the reduce-motion preference asks for no sweep at all.
 */
private val LocalShimmerSweep: ProvidableCompositionLocal<State<Float>?> =
    staticCompositionLocalOf { null }

/** One looping 0..1 sweep, or null when the preference asks for none. */
@Composable
private fun rememberShimmerSweep(): State<Float>? {
    val sweep = AppMotion.rememberShimmerSweep() ?: return null

    return rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = sweep),
        label = "shimmer sweep",
    )
}

/**
 * Where the highlight band's leading edge sits at [progress], for an element [width] pixels wide
 * and a band [band] pixels wide.
 *
 * The sweep runs from entirely before the element to entirely past it, so the placeholder is dark
 * at both ends of a cycle. A band anchored at zero would be caught half-lit on the first frame of
 * every loop, which reads as a rendering fault rather than as loading.
 */
internal fun shimmerBandStart(
    progress: Float,
    width: Float,
    band: Float,
): Float = progress * (width + band) - band

/**
 * The highlight's width, in dp rather than as a fraction of the block.
 *
 * A fixed width is the physical model — one light source passing over surfaces of different sizes —
 * and it is also what keeps a 40dp avatar placeholder from being swept by a 24dp smear while the
 * bar beside it gets a 200dp one.
 */
private val BAND_WIDTH: Dp = 96.dp

/** Just enough to read as a surface rather than as a hole. */
private const val BASE_ALPHA = 0.10f

/** Bright enough to be seen at a glance, dim enough not to pulse at the edge of vision. */
private const val HIGHLIGHT_ALPHA = 0.22f
