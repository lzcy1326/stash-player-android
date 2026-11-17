package gomeng.dev.stashplayer.core.player

/** Move the preview target with bounded acceleration while a TV remote key is held. */
fun nextTvScrubTarget(positionMs: Long, direction: Int, heldMs: Long, durationMs: Long): Long {
    if (direction != -1 && direction != 1) return coerceSeekRequestPosition(positionMs, durationMs)
    val step = tvScrubStepMs(heldMs)
    val safePosition = coerceSeekRequestPosition(positionMs, durationMs)
    val target = if (direction > 0) {
        if (safePosition > Long.MAX_VALUE - step) Long.MAX_VALUE else safePosition + step
    } else {
        (safePosition - step).coerceAtLeast(0L)
    }
    return coerceSeekRequestPosition(target, durationMs)
}
