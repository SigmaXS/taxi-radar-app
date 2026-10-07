package com.example.taxiradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Мастер первой настройки: по одному шагу на экран, с кнопкой прямо в нужную
 * настройку. Выполненный шаг мастер видит сам и переходит к следующему.
 * Для Android 13+ и Xiaomi — подробная ветка про «ограниченные настройки».
 */
class SetupWizardActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "taxi_radar_prefs"
        private const val KEY_DONE = "wizard_done"

        fun isDone(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    }

    private class Step(
        val icon: Int,
        val title: Int,
        val text: () -> String,
        val action: Int,
        val onAction: () -> Unit,
        /** null — шаг не проверить автоматически: водитель жмёт «Готово» сам. */
        val isDone: (() -> Boolean)?,
        val action2: Int? = null,
        val onAction2: (() -> Unit)? = null
    )

    private lateinit var steps: List<Step>
    private var index = 0

    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)
        steps = buildSteps()
        index = savedInstanceState?.getInt("step") ?: 0

        findViewById<View>(R.id.btnWizardAction).setOnClickListener { steps[index].onAction() }
        findViewById<View>(R.id.btnWizardAction2).setOnClickListener { steps[index].onAction2?.invoke() }
        findViewById<View>(R.id.btnWizardNext).setOnClickListener { next() }
        findViewById<View>(R.id.btnWizardSkip).setOnClickListener { finishWizard() }
        findViewById<View>(R.id.btnWizardHelp).setOnClickListener { PermissionGuide.show(this) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("step", index)
    }

    override fun onResume() {
        super.onResume()
        // Вернулись из настроек: шаг выполнен — сразу к следующему.
        val done = steps[index].isDone
        if (done != null && done() && index < steps.lastIndex) next() else render()
    }

    private fun next() {
        if (index < steps.lastIndex) {
            index++
            // Уже выполненные шаги пропускаем.
            while (index < steps.lastIndex && steps[index].isDone?.invoke() == true) index++
            render()
        } else {
            finishWizard()
        }
    }

    private fun finishWizard() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
        finish()
    }

    private fun render() {
        val s = steps[index]
        findViewById<TextView>(R.id.tvWizardStep).text = getString(R.string.wiz_step_of, index + 1, steps.size)
        findViewById<LinearProgressIndicator>(R.id.progressWizard).apply {
            max = steps.size
            progress = index + 1
        }
        findViewById<ImageView>(R.id.ivWizardIcon).setImageResource(s.icon)
        findViewById<TextView>(R.id.tvWizardTitle).setText(s.title)
        findViewById<TextView>(R.id.tvWizardText).text = s.text()
        val done = s.isDone?.invoke() == true
        findViewById<View>(R.id.tvWizardDone).visibility = if (done) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnWizardAction).setText(s.action)
        findViewById<MaterialButton>(R.id.btnWizardAction2).apply {
            visibility = if (s.action2 != null) View.VISIBLE else View.GONE
            s.action2?.let { setText(it) }
        }
        val last = index == steps.lastIndex
        findViewById<View>(R.id.btnWizardSkip).visibility = if (last) View.GONE else View.VISIBLE
        findViewById<MaterialButton>(R.id.btnWizardNext).apply {
            visibility = if (last) View.GONE else View.VISIBLE
            setText(if (s.isDone == null || done) R.string.wiz_next else R.string.wiz_later)
        }
    }

    private fun buildSteps(): List<Step> {
        val list = mutableListOf<Step>()
        val android13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        if (android13) {
            list += Step(
                R.drawable.ic_help, R.string.wiz_notif_title, { getString(R.string.wiz_notif_text) },
                R.string.setup_allow, { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                { has(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }
        list += Step(
            R.drawable.ic_open_in_new, R.string.wiz_overlay_title, { getString(R.string.wiz_overlay_text) },
            R.string.wiz_open_settings,
            { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), appDetails()) },
            { Settings.canDrawOverlays(this) }
        )
        list += Step(
            R.drawable.ic_checklist, R.string.wiz_battery_title, { getString(R.string.wiz_battery_text) },
            R.string.setup_allow, { requestBattery() },
            { (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) }
        )
        // Android 11+: «Приостановить работу, если не используется» через пару
        // недель без открытия отбирает разрешения — радар молча перестаёт работать.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            list += Step(
                R.drawable.ic_checklist, R.string.wiz_unused_title, { getString(R.string.wiz_unused_text) },
                R.string.wiz_open_settings,
                { open(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:$packageName")), appDetails()) },
                { packageManager.isAutoRevokeWhitelisted }
            )
        }
        list += Step(
            R.drawable.ic_nav_radar, R.string.wiz_acc_title,
            {
                val path = getString(if (isXiaomi()) R.string.wiz_acc_path_xiaomi else R.string.wiz_acc_path)
                when {
                    // HyperOS: переключатель внизу «О приложении», а не в меню ⋮.
                    android13 && isXiaomi() -> getString(R.string.wiz_acc_text_13_xiaomi, path)
                    android13 -> getString(R.string.wiz_acc_text_13, path)
                    else -> getString(R.string.wiz_acc_text, path)
                }
            },
            R.string.chk_acc_open, { AccessibilityAccess.open(this) },
            { OrderAccessibilityService.isConnected || accessibilityEnabled() },
            action2 = if (android13) R.string.wiz_acc_restricted else null,
            onAction2 = if (android13) ({ open(appDetails()) }) else null
        )
        if (isXiaomi()) {
            list += Step(
                R.drawable.ic_play, R.string.wiz_autostart_title, { getString(R.string.wiz_autostart_text) },
                R.string.wiz_open_settings,
                {
                    open(
                        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                        appDetails()
                    )
                },
                null
            )
        }
        list += Step(
            R.drawable.ic_check_circle, R.string.wiz_done_title, { getString(R.string.wiz_done_text) },
            R.string.wiz_finish, { finishWizard() }, null
        )
        return list
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val component = ComponentName(this, OrderAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    private fun isXiaomi(): Boolean {
        val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { brand.contains(it) }
    }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    @SuppressLint("BatteryLife")
    private fun requestBattery() = open(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        appDetails()
    )

    /** Открывает первый экран настроек, который есть на этой прошивке. */
    private fun open(vararg intents: Intent) {
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, getString(R.string.setup_open_failed), Toast.LENGTH_LONG).show()
    }
}
