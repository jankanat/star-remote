package kz.alnazar.starremote.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** Общий фон для стекла: что размывать и когда перерисовывать. */
class Backdrop(val layer: GraphicsLayer, val tick: MutableLongState)

/**
 * Матовое стекло: настоящее размытие звёзд под элементом (Android 12+),
 * полупрозрачная заливка, кромка, блик за пальцем и редкий пробегающий отблеск.
 */
@Composable
fun Glass(
    backdrop: Backdrop?,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sheen: Boolean = false,
    sheenOffsetMs: Int = 0,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var touch by remember { mutableStateOf(Offset(0f, 0f)) }
    var lit by remember { mutableStateOf(false) }
    val litA by animateFloatAsState(if (lit) 1f else 0f, tween(if (lit) 120 else 550), label = "glint")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed && enabled) .975f else 1f, tween(180), label = "press")
    val alpha = if (enabled) 1f else .42f

    var m = modifier
        .scale(sc)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: continue
                    touch = c.position
                    lit = c.pressed
                }
            }
        }
    if (onClick != null) m = m.clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)

    Box(m) {
        if (backdrop != null && Build.VERSION.SDK_INT >= 31) {
            Box(
                Modifier.matchParentSize()
                    .blur(22.dp, BlurredEdgeTreatment.Rectangle)
                    .drawBehind {
                        backdrop.tick.longValue
                        translate(-origin.x, -origin.y) { drawLayer(backdrop.layer) }
                    }
            )
        }
        Box(
            Modifier.matchParentSize().drawBehind {
                // заливка стекла
                drawRect(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = .13f * alpha + (if (backdrop == null) .05f else 0f)),
                        .46f to Color.White.copy(alpha = .035f * alpha),
                        1f to Color(0xFF7896FF).copy(alpha = .07f * alpha),
                        start = Offset.Zero, end = Offset(size.width * .6f, size.height * 1.6f),
                    )
                )
                // светлая кромка сверху
                drawLine(Color.White.copy(alpha = .28f * alpha), Offset(0f, .5f), Offset(size.width, .5f), 1.dp.toPx())
                // блик за пальцем
                if (litA > .01f) {
                    val r = 220.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.White.copy(alpha = .24f * litA), .62f to Color.Transparent, 1f to Color.Transparent,
                            center = touch, radius = r,
                        ), radius = r, center = touch,
                    )
                }
            }
        )
        if (sheen && enabled) Sheen(sheenOffsetMs)
        Box(Modifier.matchParentSize().border(1.dp, P.glassEdge.copy(alpha = P.glassEdge.alpha * (if (enabled) 1f else .6f)), shape))
        Box(Modifier.matchParentSize().drawBehind { if (!enabled) drawRect(Color.Black.copy(alpha = .25f)) })
        content()
    }
}

@Composable
private fun BoxScope.Sheen(offsetMs: Int) {
    val inf = rememberInfiniteTransition(label = "sheen")
    val p by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart, androidx.compose.animation.core.StartOffset(offsetMs)),
        label = "sheenP",
    )
    Box(
        Modifier.matchParentSize().drawBehind {
            if (p < .72f) return@drawBehind
            val k = (p - .72f) / .28f
            val e = k * k * (3 - 2 * k)
            val w = size.width
            val cx = -.7f * w + (2.1f * w) * e
            val bw = .19f * w
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, .5f to Color.White.copy(alpha = .2f), 1f to Color.Transparent,
                    start = Offset(cx - bw, -size.height * .3f), end = Offset(cx + bw, size.height * .3f),
                )
            )
        }
    )
}
