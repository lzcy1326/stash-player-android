package gomeng.dev.stashplayer.core.player

/**
 * Remote seek behaviour for the TV build, following the patterns used by Kodi, SmartTube and the
 * Jellyfin web player:
 *
 * - one press steps a fixed amount,
 * - quick repeated presses escalate the step (10s → 20s → 30s → 60s),
 * - holding a key scrubs the timeline, and the scrub rate keeps growing with the hold time so a
 *   two-hour film can be crossed in a few seconds.
 *
 * Repeat handling is time based on purpose: some remotes send repeated ACTION_DOWN between 50ms and
 * 200ms apart, so [android.view.KeyEvent.repeatCount] is not a reliable clock.
 */
const val TV_REPEAT_WINDOW_MS = 800L
const val TV_HOLD_THRESHOLD_MS = 400L
private val TV_SEEK_STEPS_MS = longArrayOf(10_000L, 20_000L, 30_000L, 60_000L)

/** Seek step for a press, growing with the number of presses inside [TV_REPEAT_WINDOW_MS]. */
fun tvSeekStepMs(consecutivePresses: Int): Long =
    TV_SEEK_STEPS_MS[(consecutivePresses - 1).coerceIn(0, TV_SEEK_STEPS_MS.lastIndex)]

/**
 * Distance covered by one scrub tick while a direction key is held. The rate grows from 1× to at
 * most 3× of the base step, then stays there — holding longer keeps dragging at a predictable speed
 * instead of accelerating without limit.
 */
private const val TV_SCRUB_BASE_STEP_MS = 20_000L
const val TV_SCRUB_MAX_RATE = 3f

fun tvScrubRate(heldMs: Long): Float = when {
    heldMs >= 4_000L -> 3f
    heldMs >= 1_500L -> 2f
    else -> 1f
}

fun tvScrubStepMs(heldMs: Long): Long =
    (TV_SCRUB_BASE_STEP_MS * tvScrubRate(heldMs)).toLong()
