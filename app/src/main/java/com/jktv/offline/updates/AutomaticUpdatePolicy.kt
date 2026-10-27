package com.jktv.offline.updates

/** Monotonic polling cooldown, plus a wall-clock snooze persisted by the owner. */
class AutomaticUpdatePolicy {
    private var nextCheckMs = 0L
    fun canCheck(elapsedMs: Long): Boolean = elapsedMs >= nextCheckMs
    fun attempted(elapsedMs: Long) { nextCheckMs = elapsedMs + CHECK_INTERVAL_MS }
    fun failed(elapsedMs: Long) { nextCheckMs = elapsedMs + RETRY_INTERVAL_MS }
    fun interrupted() { nextCheckMs = 0L }
    fun shouldNotify(identity: String, dismissedIdentity: String?, dismissedAtMs: Long, nowMs: Long): Boolean =
        identity != dismissedIdentity || nowMs < dismissedAtMs || nowMs - dismissedAtMs >= SNOOZE_MS

    companion object {
        const val CHECK_INTERVAL_MS = 15 * 60_000L
        const val RETRY_INTERVAL_MS = 5 * 60_000L
        const val SNOOZE_MS = 24 * 60 * 60_000L
        fun identity(update: GitHubAppUpdate) = "${update.versionName}:${update.versionCode}:${update.sha256}"
    }
}
