package com.studylockdown.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Single source of truth for Study Lockdown's state: whether lockdown is
 * enabled, the active study countdown, and the rolling 24h emergency-pass
 * quota. Backed by SharedPreferences so state survives process death and is
 * readable from the Accessibility Service, the Quick Settings tile, and the
 * Activity alike.
 */
object LockdownManager {

    private const val PREFS_NAME = "study_lockdown_prefs"
    private const val KEY_LOCKDOWN_ENABLED = "lockdown_enabled"
    private const val KEY_EMERGENCY_TIMESTAMPS = "emergency_timestamps"
    private const val KEY_TEMP_UNLOCK_UNTIL = "temp_unlock_until"
    private const val KEY_TIMER_END = "timer_end_millis"
    private const val KEY_LAST_EMERGENCY_REASON = "last_emergency_reason"

    const val MAX_PASSES_PER_WINDOW = 2
    const val WINDOW_MILLIS = 24L * 60 * 60 * 1000
    const val EMERGENCY_UNLOCK_DURATION_MILLIS = 15L * 60 * 1000

    /** Package names permitted to run while lockdown is being enforced. */
    val ALLOWED_PACKAGES: Set<String> = setOf(
        "com.google.android.dialer",        // Phone
        "com.google.android.apps.messaging", // SMS
        "com.nothing.camera",                // Camera
        "com.spotify.music",                 // Spotify
        "com.android.settings",              // System Settings
        "com.studylockdown.app"              // This app
    )

    /**
     * System-level packages that must never be blocked, or the device
     * becomes unusable (the launcher itself, System UI, keyboard, etc.).
     * These are exempted independently of the user-facing allowlist above.
     */
    val SYSTEM_EXEMPT_PACKAGES: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.nothing.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.android.inputmethod.latin"
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------
    // Core lockdown state
    // ---------------------------------------------------------------------

    /** The user-facing "lockdown switch" position, independent of timers/passes. */
    fun isLockdownEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCKDOWN_ENABLED, false)

    fun setLockdownEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCKDOWN_ENABLED, enabled).apply()
        if (!enabled) clearTimer(context)
    }

    /**
     * True only if blocking should be actively enforced right now: the
     * switch is on, the study timer (if set) has not expired, and there is
     * no live emergency-pass unlock window. Call this from the Accessibility
     * Service before intercepting anything -- it also auto-expires a
     * finished timer as a side effect.
     */
    fun isEnforcementActive(context: Context): Boolean {
        if (!isLockdownEnabled(context)) return false

        val timerEnd = prefs(context).getLong(KEY_TIMER_END, 0L)
        if (timerEnd > 0L && System.currentTimeMillis() >= timerEnd) {
            setLockdownEnabled(context, false) // timer ran out; auto turn off
            return false
        }

        if (isTemporarilyUnlocked(context)) return false

        return true
    }

    // ---------------------------------------------------------------------
    // Study countdown timer
    // ---------------------------------------------------------------------

    /** Starts (or restarts) a study countdown and turns lockdown on. */
    fun startTimer(context: Context, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        prefs(context).edit().putLong(KEY_TIMER_END, end).apply()
        setLockdownEnabled(context, true)
    }

    fun clearTimer(context: Context) {
        prefs(context).edit().remove(KEY_TIMER_END).apply()
    }

    fun getTimerEndMillis(context: Context): Long =
        prefs(context).getLong(KEY_TIMER_END, 0L)

    fun getRemainingTimerMillis(context: Context): Long {
        val end = getTimerEndMillis(context)
        if (end <= 0L) return 0L
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    // ---------------------------------------------------------------------
    // Emergency pass quota (max 2 per rolling 24h window)
    // ---------------------------------------------------------------------

    private fun readTimestamps(context: Context): MutableList<Long> {
        val raw = prefs(context).getString(KEY_EMERGENCY_TIMESTAMPS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Long>()
        for (i in 0 until arr.length()) list.add(arr.getLong(i))
        return list
    }

    private fun writeTimestamps(context: Context, timestamps: List<Long>) {
        val arr = JSONArray()
        timestamps.forEach { arr.put(it) }
        prefs(context).edit().putString(KEY_EMERGENCY_TIMESTAMPS, arr.toString()).apply()
    }

    /** Drops timestamps older than the rolling window and persists the result. */
    private fun pruneTimestamps(context: Context): MutableList<Long> {
        val now = System.currentTimeMillis()
        val pruned = readTimestamps(context).filter { now - it < WINDOW_MILLIS }.toMutableList()
        writeTimestamps(context, pruned)
        return pruned
    }

    /** Passes remaining in the current rolling 24h window (0..MAX_PASSES_PER_WINDOW). */
    fun getRemainingPasses(context: Context): Int {
        val used = pruneTimestamps(context).size
        return (MAX_PASSES_PER_WINDOW - used).coerceAtLeast(0)
    }

    fun isTemporarilyUnlocked(context: Context): Boolean =
        prefs(context).getLong(KEY_TEMP_UNLOCK_UNTIL, 0L) > System.currentTimeMillis()

    fun getTempUnlockUntilMillis(context: Context): Long =
        prefs(context).getLong(KEY_TEMP_UNLOCK_UNTIL, 0L)

    fun getLastEmergencyReason(context: Context): String? =
        prefs(context).getString(KEY_LAST_EMERGENCY_REASON, null)

    /**
     * Attempts to consume one emergency pass. [reason] must be non-blank.
     * On success, pauses enforcement for [EMERGENCY_UNLOCK_DURATION_MILLIS]
     * and returns true. Returns false if the reason is blank or the rolling
     * 24h quota is already exhausted -- callers should not decrement UI
     * state unless this returns true.
     */
    fun useEmergencyPass(context: Context, reason: String): Boolean {
        if (reason.isBlank()) return false

        val timestamps = pruneTimestamps(context)
        if (timestamps.size >= MAX_PASSES_PER_WINDOW) return false

        val now = System.currentTimeMillis()
        timestamps.add(now)
        writeTimestamps(context, timestamps)

        prefs(context).edit()
            .putLong(KEY_TEMP_UNLOCK_UNTIL, now + EMERGENCY_UNLOCK_DURATION_MILLIS)
            .putString(KEY_LAST_EMERGENCY_REASON, reason)
            .apply()
        return true
    }
}
