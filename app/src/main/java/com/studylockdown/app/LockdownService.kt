package com.studylockdown.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Watches TYPE_WINDOW_STATE_CHANGED events to see which app just came to the
 * foreground. If Study Lockdown is actively enforcing and the package is not
 * on the allowlist (and isn't a system/launcher package that must never be
 * blocked), it immediately sends the user Home and shows a toast.
 *
 * This service only *reacts* to foreground changes; all the actual state
 * (on/off, timer, emergency passes) lives in [LockdownManager] so the
 * Activity and the Quick Settings tile stay in sync with it.
 */
class LockdownService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Simple debounce so a single app-switch doesn't trigger repeated
    // home-intents/toasts from multiple window-state events firing in quick
    // succession for the same package.
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis: Long = 0L
    private val debounceMillis = 1500L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        handleForegroundPackage(packageName)
    }

    private fun handleForegroundPackage(packageName: String) {
        if (!LockdownManager.isEnforcementActive(applicationContext)) return
        if (packageName in LockdownManager.ALLOWED_PACKAGES) return
        if (packageName in LockdownManager.SYSTEM_EXEMPT_PACKAGES) return

        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockedAtMillis < debounceMillis) return
        lastBlockedPackage = packageName
        lastBlockedAtMillis = now

        goHome()
        showBlockedToast()
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    private fun showBlockedToast() {
        mainHandler.post {
            Toast.makeText(
                applicationContext,
                "Focus Mode Active: App Restricted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onInterrupt() {
        // Required override; nothing to clean up.
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Toast.makeText(
            applicationContext,
            "Study Lockdown: blocking service connected",
            Toast.LENGTH_SHORT
        ).show()
    }
}
