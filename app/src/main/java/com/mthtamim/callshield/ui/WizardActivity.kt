package com.mthtamim.callshield.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.mthtamim.callshield.R

/**
 * First-run setup presented as one polished checklist instead of a
 * sequence of separate wizard pages. Android still owns each permission
 * prompt/settings screen; returning here updates the same checklist.
 */
class WizardActivity : BaseActivity() {

    private enum class Grant {
        ROLE, CONTACTS, CALL_LOG, PHONE_STATE, SMS_READ, NOTIFICATIONS,
        NOTIFICATION_ACCESS, BATTERY, EXACT_ALARM, SEND_SMS, CALENDAR
    }

    private data class Access(
        val grant: Grant,
        val titleRes: Int,
        val bodyRes: Int,
        val optional: Boolean = false
    )

    private val requiredAccess = listOf(
        Access(Grant.ROLE, R.string.w2_title, R.string.w2_body),
        Access(Grant.CONTACTS, R.string.w3_title, R.string.w3_body),
        Access(Grant.CALL_LOG, R.string.w4_title, R.string.w4_body),
        Access(Grant.NOTIFICATIONS, R.string.w5_title, R.string.w5_body),
        Access(Grant.NOTIFICATION_ACCESS, R.string.w10_title, R.string.w10_body),
        Access(Grant.BATTERY, R.string.w6_title, R.string.w6_body),
        Access(Grant.EXACT_ALARM, R.string.w11_title, R.string.w11_body)
    )

    private val optionalAccess = listOf(
        Access(Grant.PHONE_STATE, R.string.w8_title, R.string.w8_body, true),
        Access(Grant.SMS_READ, R.string.w9_title, R.string.w9_body, true),
        Access(Grant.SEND_SMS, R.string.w12_title, R.string.w12_body, true),
        Access(Grant.CALENDAR, R.string.w13_title, R.string.w13_body, true)
    )

    private lateinit var textProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var textTitle: TextView
    private lateinit var textBody: TextView
    private lateinit var textStatus: TextView
    private lateinit var requiredContainer: LinearLayout
    private lateinit var optionalContainer: LinearLayout
    private lateinit var btnAction: Button
    private lateinit var btnContinue: Button
    private var pendingGrant: Grant? = null
    private val deferredGrants = mutableSetOf<Grant>()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingGrant?.let { grant ->
                if (granted) deferredGrants.remove(grant) else deferredGrants.add(grant)
            }
            pendingGrant = null
            refreshSetup()
        }

    private val requestRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val grant = pendingGrant
            if (grant != null) {
                if (statusFor(grant)) deferredGrants.remove(grant) else deferredGrants.add(grant)
            }
            pendingGrant = null
            refreshSetup()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wizard)

        textProgress = findViewById(R.id.textWizardProgress)
        progressBar = findViewById(R.id.progressWizard)
        textTitle = findViewById(R.id.textWizardStepTitle)
        textBody = findViewById(R.id.textWizardBody)
        textStatus = findViewById(R.id.textWizardStatus)
        requiredContainer = findViewById(R.id.layoutWizardRequired)
        optionalContainer = findViewById(R.id.layoutWizardOptional)
        btnAction = findViewById(R.id.btnWizardAction)
        btnContinue = findViewById(R.id.btnWizardNext)

        textTitle.setText(R.string.wizard_setup_title)
        textBody.setText(R.string.wizard_setup_body)
        btnAction.setOnClickListener { grantNextRequired() }
        btnContinue.setOnClickListener {
            // Explicitly finish onboarding once. Users can manage individual
            // and optional grants later from Settings > Permissions.
            store().setWizardDone()
            finish()
        }

        refreshSetup()
    }

    private fun statusFor(grant: Grant): Boolean = when (grant) {
        Grant.ROLE -> Perms.roleHeld(this)
        Grant.CONTACTS -> Perms.hasContacts(this)
        Grant.CALL_LOG -> Perms.hasCallLog(this)
        Grant.PHONE_STATE -> Perms.hasPhoneState(this)
        Grant.SMS_READ -> Perms.hasSms(this)
        Grant.NOTIFICATIONS -> Perms.hasNotifications(this)
        Grant.NOTIFICATION_ACCESS -> Perms.notificationAccess(this)
        Grant.BATTERY -> Perms.batteryExempt(this)
        Grant.EXACT_ALARM -> Perms.exactAlarmAllowed(this)
        Grant.SEND_SMS -> Perms.hasSendSms(this)
        Grant.CALENDAR -> Perms.hasCalendar(this)
    }

    private fun refreshSetup() {
        requiredContainer.removeAllViews()
        optionalContainer.removeAllViews()

        requiredAccess.forEach { addAccessRow(requiredContainer, it) }
        optionalAccess.forEach { addAccessRow(optionalContainer, it) }

        val complete = requiredAccess.count { statusFor(it.grant) }
        val missing = requiredAccess.size - complete
        progressBar.max = requiredAccess.size
        progressBar.progress = complete
        textProgress.text = getString(R.string.wizard_setup_progress, complete, requiredAccess.size)
        textStatus.text = if (missing == 0) {
            getString(R.string.wizard_setup_complete)
        } else {
            getString(R.string.wizard_setup_remaining, missing)
        }

        btnAction.isEnabled = missing > 0
        btnAction.alpha = if (missing > 0) 1f else 0.58f
        btnAction.setText(
            if (missing > 0) R.string.wizard_setup_next else R.string.wizard_setup_ready
        )
        btnContinue.setText(R.string.wizard_setup_continue)
    }

    private fun addAccessRow(parent: LinearLayout, access: Access) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = getDrawable(R.drawable.bg_row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        val title = TextView(this).apply {
            setText(access.titleRes)
            setTextColor(getColor(R.color.cs_text))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val description = TextView(this).apply {
            setText(access.bodyRes)
            setTextColor(getColor(R.color.cs_text_secondary))
            textSize = 13f
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(5) }
        }
        val status = TextView(this).apply {
            text = getString(
                if (statusFor(access.grant)) R.string.perm_granted
                else R.string.perm_not_granted
            )
            setTextColor(
                getColor(if (statusFor(access.grant)) R.color.cs_accent_dark else R.color.cs_warning)
            )
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        row.addView(title)
        row.addView(description)
        row.addView(status)
        parent.addView(row)
    }

    private fun grantNextRequired() {
        val missing = requiredAccess.filterNot { statusFor(it.grant) }
        val next = missing.firstOrNull { it.grant !in deferredGrants }
            ?: missing.firstOrNull() ?: run {
            refreshSetup()
            return
        }
        when (next.grant) {
            Grant.ROLE -> {
                val intent = Perms.roleRequestIntent(this)
                if (intent != null) {
                    pendingGrant = next.grant
                    requestRole.launch(intent)
                } else {
                    deferredGrants.add(next.grant)
                    refreshSetup()
                }
            }
            Grant.CONTACTS -> launchPermission(next.grant, Manifest.permission.READ_CONTACTS)
            Grant.CALL_LOG -> launchPermission(next.grant, Manifest.permission.READ_CALL_LOG)
            Grant.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    launchPermission(next.grant, Manifest.permission.POST_NOTIFICATIONS)
                } else refreshSetup()
            }
            Grant.NOTIFICATION_ACCESS -> openSystemScreen(next.grant) { Perms.notificationAccessIntent() }
            Grant.BATTERY -> openSystemScreen(next.grant) { Perms.batteryRequestIntent(this) }
            Grant.EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    openSystemScreen(next.grant) { Perms.exactAlarmIntent(this) }
                } else refreshSetup()
            }
            // Optional grants are intentionally not auto-requested during
            // onboarding; they remain available in the Permissions screen.
            Grant.PHONE_STATE, Grant.SMS_READ, Grant.SEND_SMS, Grant.CALENDAR -> refreshSetup()
        }
    }

    private fun launchPermission(grant: Grant, permission: String) {
        pendingGrant = grant
        requestPermission.launch(permission)
    }

    private fun openSystemScreen(grant: Grant, intentProvider: () -> android.content.Intent) {
        pendingGrant = grant
        try {
            startActivity(intentProvider())
        } catch (_: ActivityNotFoundException) {
            deferredGrants.add(grant)
            pendingGrant = null
            refreshSetup()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onResume() {
        super.onResume()
        if (::requiredContainer.isInitialized) {
            val grant = pendingGrant
            if (grant != null) {
                if (statusFor(grant)) deferredGrants.remove(grant) else deferredGrants.add(grant)
                pendingGrant = null
            }
            refreshSetup()
        }
    }
}
