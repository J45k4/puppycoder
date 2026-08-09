package com.puppycoder.relay.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/**
 * Provides an in-app back gesture for devices that do not use Android's system
 * gesture navigation. Starting at the left edge prevents horizontal code-block
 * scrolling and ordinary message interaction from accidentally closing a chat.
 */
internal fun Modifier.edgeSwipeBack(onBack: () -> Unit): Modifier = pointerInput(onBack) {
    val edgeWidth = 56.dp.toPx()
    val triggerDistance = 96.dp.toPx()
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.position.x > edgeWidth) return@awaitEachGesture

        var movement = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.positionChange()
            movement += delta

            val horizontal = movement.x
            val vertical = abs(movement.y)
            if (horizontal > touchSlop && horizontal > vertical) {
                change.consume()
            }
            if (horizontal >= triggerDistance && horizontal > vertical * 1.5f) {
                onBack()
                break
            }
            if (!change.pressed || horizontal < -touchSlop || vertical > max(touchSlop, abs(horizontal))) {
                break
            }
        }
    }
}
