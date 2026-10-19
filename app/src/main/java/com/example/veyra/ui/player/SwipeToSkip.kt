package com.example.veyra.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Share of the width the content must travel to count as a swipe. */
private const val DISTANCE_THRESHOLD = 0.3f

/**
 * Horizontal swipe to change track: left = next, right = previous.
 *
 * The content follows the finger and springs back unless it travelled [DISTANCE_THRESHOLD] of
 * its width or was flung faster than ~800 dp/s. Only horizontal drags are claimed (after the
 * touch slop), so vertical scrolling and taps inside keep working; keep sliders outside of it.
 * TalkBack users get "next" / "previous" as custom accessibility actions instead.
 */
@Composable
fun SwipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    nextLabel: String,
    previousLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val flingVelocity = with(LocalDensity.current) { 800.dp.toPx() }

    Box(
        modifier
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) { next(); true },
                    CustomAccessibilityAction(previousLabel) { previous(); true },
                )
            }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    onDragEnd = {
                        val direction = swipeDirection(
                            distance = offset.value,
                            velocity = tracker.calculateVelocity().x,
                            width = width.toFloat(),
                            flingVelocity = flingVelocity,
                        )
                        scope.launch {
                            if (direction == 0) {
                                offset.animateTo(0f)
                            } else {
                                val out = direction * width.toFloat()
                                offset.animateTo(out, tween(120))
                                if (direction < 0) next() else previous()
                                // The new track slides in from the opposite side.
                                offset.snapTo(-out)
                                offset.animateTo(0f, tween(180))
                            }
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + dragAmount) }
                }
            }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.6f
            },
    ) {
        content()
    }
}

/**
 * Decides a swipe: -1 = left (next), 1 = right (previous), 0 = spring back.
 * Far enough counts on its own; a fast fling counts if it goes the same way as the drag.
 */
fun swipeDirection(distance: Float, velocity: Float, width: Float, flingVelocity: Float): Int = when {
    abs(distance) > width * DISTANCE_THRESHOLD -> if (distance < 0) -1 else 1
    abs(velocity) > flingVelocity && (distance == 0f || (distance < 0) == (velocity < 0)) -> if (velocity < 0) -1 else 1
    else -> 0
}
