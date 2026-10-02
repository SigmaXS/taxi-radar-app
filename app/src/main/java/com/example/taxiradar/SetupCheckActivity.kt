package com.example.taxiradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * «Проверка настроек»: всё, без чего радар молча не работает, одним списком.
 * Каждый пункт проверяется сам и ведёт прямо в нужный экран настроек.
 * То, что телефон проверить не даёт (автозапуск MIUI и т.п.), помечено «?».
 */
class SetupCheckActivity : AppCompatActivity() {

    private enum class State { OK, FAIL, MANUAL, CHECKING }

    private class Check(
        val title: String,
        val state: State,
        val hint: String,
        val action: String? = null,
        val onAction: (() -> Unit)? = null
    )

    private lateinit var layoutChecks: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Ключ Яндекса проверяется запросом в сеть — результат держим между перерисовками.
    private var keyState = State.CHECKING
    private var keyHint = ""

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // Если запрос больше не показывается («не спрашивать снова») — ведём в настройки приложения.
        if (granted.values.none { it }) openAppDetails()
        render()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) openNotificationSettings()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_check)
        layoutChecks = findViewById(R.id.layoutChecks)
        findViewById<View>(R.id.btnSetupBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSetupRecheck).setOnClickListener {
            checkYandexKey()
            Toast.makeText(this, getString(R.string.setup_checked), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Вернулись из настроек — всё могло поменяться.
        checkYandexKey()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun checkYandexKey() {
        val key = YandexApiKey.get(this)
        if (key == null && AppConfig.load(this).sharedGeocoder) {
            keyState = State.OK
            keyHint = getString(R.string.chk_key_shared)
            render()
            return
        }
        if (key == null) {
            keyState = State.FAIL
            keyHint = getString(R.string.chk_key_missing)
            render()
            return
        }
        keyState = State.CHECKING
        keyHint = getString(R.string.chk_key_checking)
        render()
        scope.launch {
            when (RouteFareCalculator.checkYandexKey(key)) {
                RouteFareCalculator.KeyCheck.OK -> {
                    keyState = State.OK
                    keyHint = getString(R.string.chk_key_ok)
                }
                RouteFareCalculator.KeyCheck.REJECTED -> {
                    keyState = State.FAIL
                    keyHint = getString(R.string.chk_key_rejected)
                }
                RouteFareCalculator.KeyCheck.NETWORK_ERROR -> {
                    keyState = State.FAIL
                    keyHint = getString(R.string.chk_key_no_network)
                }
            }
            render()
        }
    }

    private fun buildChecks(): List<Check> {
        val list = mutableListOf<Check>()

        // 1. Чтение заказов. Тумблер может быть включён, а служба — нет (после обновления).
        val accEnabled = isAccessibilityEnabledInSettings()
        val accConnected = OrderAccessibilityService.isConnected
        list += when {
            accConnected -> Check(getString(R.string.chk_acc), State.OK, getString(R.string.chk_acc_ok))
            accEnabled -> Check(
                getString(R.string.chk_acc), State.FAIL,
                getString(R.string.chk_acc_stuck),
                getString(R.string.chk_acc_open)
            ) { openAccessibilitySettings() }
            else -> Check(
                getString(R.string.chk_acc), State.FAIL,
                buildString {
                    append(getString(if (isXiaomi()) R.string.chk_acc_path_xiaomi else R.string.chk_acc_path))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        append(getString(R.string.chk_acc_restricted_tip))
                    }
                },
                getString(R.string.chk_acc_open)
            ) { openAccessibilitySettings() }
        }
        if (!accEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Check(
                getString(R.string.chk_restricted), State.MANUAL,
                getString(R.string.chk_restricted_hint),
                getString(R.string.chk_app_info)
            ) { openAppDetails() }
        }

        // 2. Окно поверх Яндекс Про.
        list += if (Settings.canDrawOverlays(this)) {
            Check(getString(R.string.chk_overlay), State.OK, getString(R.string.chk_overlay_ok))
        } else {
            Check(getString(R.string.chk_overlay), State.FAIL, getString(R.string.chk_overlay_fail), getString(R.string.setup_allow)) {
                openFirst(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    appDetailsIntent()
                )
            }
        }

        // 3. Батарея.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        list += if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Check(getString(R.string.chk_battery), State.OK, getString(R.string.chk_battery_ok))
        } else {
            Check(getString(R.string.chk_battery), State.FAIL, getString(R.string.chk_battery_fail), getString(R.string.setup_allow)) {
                requestIgnoreBattery()
            }
        }

        // 4. Геолокация — надбавка считается там, где вы стоите.
        list += if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        ) {
            Check(getString(R.string.chk_location), State.OK, getString(R.string.chk_location_ok))
        } else {
            Check(getString(R.string.chk_location), State.MANUAL, getString(R.string.chk_location_optional), getString(R.string.setup_allow)) {
                locationLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }

        // 5. Уведомления (Android 13+): без них фоновая служба радара хуже держится.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                Check(getString(R.string.chk_notif), State.OK, getString(R.string.chk_notif_ok))
            } else {
                Check(getString(R.string.chk_notif), State.FAIL, getString(R.string.chk_notif_fail), getString(R.string.setup_allow)) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        // 6. Ключ Яндекса.
        list += Check(
            getString(R.string.chk_key), keyState, keyHint,
            if (keyState == State.FAIL) getString(R.string.yandex_connect) else null
        ) { finish() }

        // 7. Подписка.
        list += if (LicenseManager(this).isLicensed()) {
            Check(getString(R.string.chk_license), State.OK, getString(R.string.chk_license_ok))
        } else {
            Check(getString(R.string.chk_license), State.FAIL, getString(R.string.chk_license_fail), getString(R.string.chk_license_go)) { finish() }
        }

        // 8. Прошивки, которые сами убивают фоновые приложения. Проверить из
        // приложения нельзя — только открыть нужный экран.
        if (isXiaomi()) {
            list += Check(
                getString(R.string.chk_autostart), State.MANUAL,
                getString(R.string.chk_autostart_hint), getString(R.string.setup_open)
            ) {
                openFirst(
                    Intent().setComponent(
                        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    ),
                    appDetailsIntent()
                )
            }
            list += Check(
                getString(R.string.chk_activity), State.MANUAL,
                getString(R.string.chk_activity_hint), getString(R.string.setup_open)
            ) {
                openFirst(
                    Intent().setComponent(
                        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    ).putExtra("package_name", packageName).putExtra("package_label", "Taxi Radar"),
                    appDetailsIntent()
                )
            }
        }
        list += Check(
            getString(R.string.chk_lock), State.MANUAL,
            getString(R.string.chk_lock_hint)
        )

        return list
    }

    private fun render() {
        val checks = buildChecks()
        layoutChecks.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (c in checks) {
            val row = inflater.inflate(R.layout.item_setup_check, layoutChecks, false)
            val (icon, tint) = style(c.state)
            row.findViewById<ImageView>(R.id.ivCheckStatus).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(getColor(tint))
            }
            row.findViewById<TextView>(R.id.tvCheckTitle).text = c.title
            row.findViewById<TextView>(R.id.tvCheckHint).text = c.hint
            val btn = row.findViewById<MaterialButton>(R.id.btnCheckFix)
            if (c.action != null && c.onAction != null) {
                btn.text = c.action
                btn.setOnClickListener { c.onAction.invoke() }
                btn.visibility = View.VISIBLE
            } else {
                btn.visibility = View.GONE
            }
            layoutChecks.addView(row)
        }

        val fails = checks.count { it.state == State.FAIL }
        val checking = checks.any { it.state == State.CHECKING }
        val summary = findViewById<TextView>(R.id.tvSetupSummary)
        val summaryHint = findViewById<TextView>(R.id.tvSetupSummaryHint)
        val summaryIcon = findViewById<ImageView>(R.id.ivSetupSummary)
        val summaryState = when {
            fails > 0 -> State.FAIL
            checking -> State.CHECKING
            else -> State.OK
        }
        when (summaryState) {
            State.FAIL -> {
                summary.text = getString(R.string.setup_fix_count, fails)
                summaryHint.text = getString(R.string.setup_fix_hint)
            }
            State.CHECKING -> {
                summary.text = getString(R.string.yandex_checking)
                summaryHint.text = ""
            }
            else -> {
                summary.text = getString(R.string.setup_all_ok)
                summaryHint.text = getString(R.string.setup_all_ok_hint)
            }
        }
        val (icon, tint) = style(summaryState)
        summaryIcon.setImageResource(icon)
        summaryIcon.imageTintList = ColorStateList.valueOf(getColor(tint))
    }

    /** Иконка и её цвет для состояния пункта. */
    private fun style(state: State): Pair<Int, Int> = when (state) {
        State.OK -> R.drawable.ic_check_circle to R.color.tr_success
        State.FAIL -> R.drawable.ic_error to R.color.tr_danger
        State.MANUAL -> R.drawable.ic_help to R.color.tr_warning
        State.CHECKING -> R.drawable.ic_help to R.color.tr_text_muted
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val component = ComponentName(this, OrderAccessibilityService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    private fun isXiaomi(): Boolean {
        val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { brand.contains(it) }
    }

    private fun appDetailsIntent() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun openAppDetails() = openFirst(appDetailsIntent())

    private fun openAccessibilitySettings() = openFirst(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openNotificationSettings() = openFirst(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        appDetailsIntent()
    )

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() = openFirst(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetailsIntent()
    )

    /** Открывает первый экран, который есть на этой прошивке. */
    private fun openFirst(vararg intents: Intent) {
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
