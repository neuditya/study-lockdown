package com.studylockdown.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.studylockdown.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var uiTicker: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggleLockdown.setOnClickListener { onToggleLockdown() }
        binding.btnStartTimer.setOnClickListener { onStartTimer() }
        binding.btnEmergencyUnlock.setOnClickListener { showEmergencyDialog() }
        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        startUiTicker()
    }

    override fun onPause() {
        super.onPause()
        uiTicker?.cancel()
    }

    // -----------------------------------------------------------------
    // Actions
    // -----------------------------------------------------------------

    private fun onToggleLockdown() {
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable the Accessibility Service first", Toast.LENGTH_LONG).show()
            refreshUi()
            return
        }
        val turnOn = !LockdownManager.isLockdownEnabled(this)
        LockdownManager.setLockdownEnabled(this, turnOn)
        refreshUi()
    }

    private fun onStartTimer() {
        val minutes = binding.editTimerMinutes.text?.toString()?.trim()?.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            Toast.makeText(this, "Enter a valid number of minutes", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Enable the Accessibility Service first", Toast.LENGTH_LONG).show()
            refreshUi()
            return
        }
        LockdownManager.startTimer(this, minutes)
        Toast.makeText(this, "Locked for $minutes minute(s)", Toast.LENGTH_SHORT).show()
        refreshUi()
    }

    private fun showEmergencyDialog() {
        val remaining = LockdownManager.getRemainingPasses(this)
        if (remaining <= 0) {
            Toast.makeText(this, "No emergency passes left today", Toast.LENGTH_LONG).show()
            return
        }

        val input = EditText(this).apply {
            hint = "Reason for emergency unlock"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Emergency unlock ($remaining left today)")
            .setMessage("This pauses lockdown for 15 minutes. You must give a reason:")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                val reason = input.text?.toString()?.trim().orEmpty()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "A reason is required", Toast.LENGTH_SHORT).show()
                } else {
                    val granted = LockdownManager.useEmergencyPass(this, reason)
                    Toast.makeText(
                        this,
                        if (granted) "Unlocked for 15 minutes" else "No emergency passes left today",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                refreshUi()
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    // -----------------------------------------------------------------
    // UI state
    // -----------------------------------------------------------------

    /** Ticks once a second while the Activity is visible to keep the countdown live. */
    private fun startUiTicker() {
        uiTicker?.cancel()
        uiTicker = object : CountDownTimer(Long.MAX_VALUE, 1000L) {
            override fun onTick(millisUntilFinished: Long) = refreshUi()
            override fun onFinish() = Unit
        }.start()
    }

    private fun refreshUi() {
        val enabled = LockdownManager.isLockdownEnabled(this)
        val enforcing = LockdownManager.isEnforcementActive(this)
        val remainingPasses = LockdownManager.getRemainingPasses(this)
        val accessibilityOn = isAccessibilityServiceEnabled()

        binding.btnToggleLockdown.text = if (enabled) "STOP\nLOCKDOWN" else "START\nLOCKDOWN"

        binding.tvStatus.text = when {
            !accessibilityOn -> "STATUS: ACCESSIBILITY DISABLED"
            enforcing -> "STATUS: LOCKED"
            enabled -> "STATUS: PAUSED (EMERGENCY)"
            else -> "STATUS: FREE"
        }

        binding.tvPasses.text =
            "Passes left today: $remainingPasses/${LockdownManager.MAX_PASSES_PER_WINDOW}"

        val remainingTimerMillis = LockdownManager.getRemainingTimerMillis(this)
        binding.tvTimer.text = if (enabled && remainingTimerMillis > 0) {
            val totalSeconds = remainingTimerMillis / 1000
            val mins = totalSeconds / 60
            val secs = totalSeconds % 60
            String.format("Time remaining: %02d:%02d", mins, secs)
        } else {
            "Time remaining: --:--"
        }

        binding.btnEnableAccessibility.visibility = if (accessibilityOn) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
