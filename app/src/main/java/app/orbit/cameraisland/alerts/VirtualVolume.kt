package app.orbit.cameraisland.alerts

/**
 * The phone's volume has few real steps (15 for media), but the island shows and steps volume in clean 5% increments.
 * This remembers the 0-100% value behind the last key press so the number keeps counting 5, 10, 15, ... even when
 * two neighbouring percentages land on the same real step. If the volume is changed some other way (the system
 * slider, the island slider) the remembered value no longer matches and the number is rounded from the real level.
 */
object VirtualVolume {
    private var stream = -1
    private var level = -1
    private var pct = 0

    /** Set by [SystemAlerts]: re-shows the island's volume banner (needed when a press changes the number but not the real level). */
    @Volatile var refresh: ((Int) -> Unit)? = null

    fun round5(level: Int, max: Int) = if (max <= 0) 0 else Math.round(level * 100f / max / 5f) * 5

    /** The percentage to display for [level] of [max] on [stream]. */
    fun shown(stream: Int, level: Int, max: Int): Int =
        if (stream == this.stream && level == this.level) pct else round5(level, max)

    fun remember(stream: Int, level: Int, pct: Int) { this.stream = stream; this.level = level; this.pct = pct }
}
