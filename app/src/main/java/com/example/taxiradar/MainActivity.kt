package com.example.taxiradar

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    companion object {
        private const val STATE_TAB = "tab"
    }

    private lateinit var licenseManager: LicenseManager

    private lateinit var scrollMain: ScrollView
    private lateinit var viewLicenseDot: View
    private lateinit var tvLicenseStatus: TextView
    private lateinit var layoutActivation: View
    private lateinit var etLicenseKey: EditText
    private lateinit var btnActivate: MaterialButton
    private lateinit var btnLaunchWidget: MaterialButton
    private lateinit var tvLaunchHint: TextView
    private lateinit var btnRenew: MaterialButton
    private lateinit var tileContact: View
    private lateinit var tileGroup: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var pages: Map<Int, View>
    private var mapController: SurgeMapController? = null
    private var currentTab = R.id.nav_radar

    private lateinit var switchNet: MaterialSwitch
    private lateinit var layoutNetFields: View
    private lateinit var etFuelConsumption: EditText
    private lateinit var etFuelPrice: EditText
    private lateinit var etCommission: EditText
    private lateinit var tvNetExample: TextView

    private lateinit var cardReferral: View
    private lateinit var tvReferralCaption: TextView
    private lateinit var tvReferralCode: TextView
    private lateinit var tvReferralStats: TextView
    private lateinit var btnReferralShare: MaterialButton
    private lateinit var btnReferralEnter: MaterialButton

    private lateinit var cardYandex: View
    private lateinit var tvYandexStatus: TextView
    private lateinit var layoutYandexSetup: View
    private lateinit var layoutYandexConnected: View
    private lateinit var tvYandexKeyMasked: TextView
    private lateinit var tilYandexKey: TextInputLayout
    private lateinit var etYandexKey: EditText
    private lateinit var btnYandexGetKey: MaterialButton
    private lateinit var btnYandexPaste: MaterialButton
    private lateinit var btnYandexSave: MaterialButton
    private lateinit var btnYandexDisconnect: MaterialButton

    private lateinit var cbEconom: CheckBox
    private lateinit var cbComfort: CheckBox
    private lateinit var cbComfortPlus: CheckBox


    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            verifyAccessibilityBeforeStart()
        } else {
            Toast.makeText(this, getString(R.string.overlay_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkOverlayAndProceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Первый запуск — мастер настройки шаг за шагом.
        if (!SetupWizardActivity.isDone(this)) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }

        licenseManager = LicenseManager(this)

        scrollMain = findViewById(R.id.scrollMain)
        viewLicenseDot = findViewById(R.id.viewLicenseDot)
        tvLicenseStatus = findViewById(R.id.tvLicenseStatus)
        layoutActivation = findViewById(R.id.layoutActivation)
        etLicenseKey = findViewById(R.id.etLicenseKey)
        btnActivate = findViewById(R.id.btnActivate)
        btnLaunchWidget = findViewById(R.id.btnLaunchWidget)
        tvLaunchHint = findViewById(R.id.tvLaunchHint)
        btnRenew = findViewById(R.id.btnRenew)
        tileContact = findViewById(R.id.tileContact)
        tileGroup = findViewById(R.id.tileGroup)
        bottomNav = findViewById(R.id.bottomNav)
        pages = mapOf(
            R.id.nav_radar to findViewById(R.id.pageRadar),
            R.id.nav_map to findViewById(R.id.pageMap),
            R.id.nav_useful to findViewById(R.id.pageUseful),
            R.id.nav_profile to findViewById(R.id.pageProfile)
        )

        switchNet = findViewById(R.id.switchNet)
        layoutNetFields = findViewById(R.id.layoutNetFields)
        etFuelConsumption = findViewById(R.id.etFuelConsumption)
        etFuelPrice = findViewById(R.id.etFuelPrice)
        etCommission = findViewById(R.id.etCommission)
        tvNetExample = findViewById(R.id.tvNetExample)

        cardReferral = findViewById(R.id.cardReferral)
        tvReferralCaption = findViewById(R.id.tvReferralCaption)
        tvReferralCode = findViewById(R.id.tvReferralCode)
        tvReferralStats = findViewById(R.id.tvReferralStats)
        btnReferralShare = findViewById(R.id.btnReferralShare)
        btnReferralEnter = findViewById(R.id.btnReferralEnter)

        cardYandex = findViewById(R.id.cardYandex)
        tvYandexStatus = findViewById(R.id.tvYandexStatus)
        layoutYandexSetup = findViewById(R.id.layoutYandexSetup)
        layoutYandexConnected = findViewById(R.id.layoutYandexConnected)
        tvYandexKeyMasked = findViewById(R.id.tvYandexKeyMasked)
        tilYandexKey = findViewById(R.id.tilYandexKey)
        etYandexKey = findViewById(R.id.etYandexKey)
        btnYandexGetKey = findViewById(R.id.btnYandexGetKey)
        btnYandexPaste = findViewById(R.id.btnYandexPaste)
        btnYandexSave = findViewById(R.id.btnYandexSave)
        btnYandexDisconnect = findViewById(R.id.btnYandexDisconnect)

        cbEconom = findViewById(R.id.cbEconom)
        cbComfort = findViewById(R.id.cbComfort)
        cbComfortPlus = findViewById(R.id.cbComfortPlus)

        loadSelectedTariffs()
        checkIfAppUpdated()
        setupYandexCard()
        setupNetCard()
        setupContacts()
        setupReferralCard()
        setupWidgetSize()
        findViewById<View>(R.id.btnBell).setOnClickListener { showNotifications() }

        setupTabs(savedInstanceState?.getInt(STATE_TAB) ?: R.id.nav_radar)
        setupUsefulTiles()
        findViewById<View>(R.id.cardSetupWarning).setOnClickListener {
            startActivity(Intent(this, SetupCheckActivity::class.java))
        }
        findViewById<TextView>(R.id.tvVersion).text = getString(
            R.string.app_version, packageManager.getPackageInfo(packageName, 0).versionName
        )
        findViewById<View>(R.id.btnSetupCheck).setOnClickListener {
            startActivity(Intent(this, SetupCheckActivity::class.java))
        }
        findViewById<View>(R.id.btnLanguage).setOnClickListener { showLanguageChooser() }

        val saveCheckboxes = {
            if (!cbEconom.isChecked && !cbComfort.isChecked && !cbComfortPlus.isChecked) {
                cbEconom.isChecked = true
            }
            getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("show_econom", cbEconom.isChecked)
                .putBoolean("show_comfort", cbComfort.isChecked)
                .putBoolean("show_comfortplus", cbComfortPlus.isChecked)
                .apply()
        }

        cbEconom.setOnCheckedChangeListener { _, _ -> saveCheckboxes() }
        cbComfort.setOnCheckedChangeListener { _, _ -> saveCheckboxes() }
        cbComfortPlus.setOnCheckedChangeListener { _, _ -> saveCheckboxes() }

        btnActivate.setOnClickListener {
            val key = etLicenseKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, getString(R.string.license_enter_key), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnActivate.isEnabled = false
            btnActivate.text = getString(R.string.license_checking)

            CoroutineScope(Dispatchers.Main).launch {
                val result = licenseManager.activateKey(key)
                btnActivate.isEnabled = true
                btnActivate.text = getString(R.string.license_activate)

                Toast.makeText(this@MainActivity, result.second, Toast.LENGTH_SHORT).show()
                if (result.first) {
                    etLicenseKey.text.clear()
                    syncWithServer()
                }
            }
        }

        btnLaunchWidget.setOnClickListener {
            if (FloatingWidgetService.isRunning) {
                stopService(Intent(this, FloatingWidgetService::class.java))
                FloatingWidgetService.isRunning = false
                updateWidgetButtonState()
                Toast.makeText(this, getString(R.string.radar_stopped), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!YandexApiKey.ready(this)) {
                tilYandexKey.error = getString(R.string.yandex_need_key_to_start)
                showYandexCard()
                return@setOnClickListener
            }

            if (!licenseManager.isLicensed()) {
                Toast.makeText(this, getString(R.string.license_closed), Toast.LENGTH_SHORT).show()
                syncWithServer()
                return@setOnClickListener
            }

            // Запрашиваем отключение оптимизации батареи перед запуском служб
            requestIgnoreBatteryOptimizations()

            requestBasePermissions()
        }
    }

    private fun setupTabs(initial: Int) {
        bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        bottomNav.selectedItemId = initial
        showTab(initial)
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        pages.forEach { (id, page) -> page.visibility = if (id == tab) View.VISIBLE else View.GONE }
        if (tab == R.id.nav_map) {
            // Карту создаём только когда её впервые открыли — она тяжёлая.
            val controller = mapController ?: SurgeMapController(this, pages.getValue(R.id.nav_map)).also {
                mapController = it
                setupRoadAlertsChip()
            }
            controller.onShow()
            val prefs = getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("map_intro_shown", false)) {
                // Первый вход: короткая инструкция, потом — геолокация.
                prefs.edit().putBoolean("map_intro_shown", true).apply()
                showMapIntro { askMapLocation() }
            } else {
                askMapLocation()
            }
        } else {
            mapController?.onHide()
        }
    }

    private fun askMapLocation() {
        if (!hasLocation() && !mapLocationAsked) {
            mapLocationAsked = true
            askLocation(R.string.loc_map_why, onGranted = { mapController?.onLocationGranted() })
        }
    }

    /** Что умеет карта — со значками, как в мастере настройки. */
    private fun showMapIntro(then: () -> Unit) {
        val rows = listOf(
            "🔥" to R.string.intro_map_surge,
            "📍" to R.string.intro_map_here,
            "➕" to R.string.intro_map_add,
            "👆" to R.string.intro_map_tap,
            "🔔" to R.string.intro_map_alerts,
            "🗂" to R.string.intro_map_layers
        )
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * d).toInt(), (8 * d).toInt(), (22 * d).toInt(), 0)
        }
        for ((emoji, text) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                // Без этого строки по базовой линии обрезают многострочный текст.
                isBaselineAligned = false
                setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
            }
            row.addView(TextView(this).apply {
                this.text = emoji
                textSize = 22f
                layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                setText(text)
                setTextColor(color(R.color.tr_text))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            box.addView(row)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.intro_map_title)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.intro_got_it) { _, _ -> then() }
            .setOnCancelListener { then() }
            .show()
    }

    // ---------- геолокация — только по желанию водителя ----------

    private var mapLocationAsked = false
    private var afterLocation: ((Boolean) -> Unit)? = null

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        afterLocation?.invoke(result.values.any { it })
        afterLocation = null
    }

    private fun hasLocation() =
        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Объясняем, зачем геолокация, и только потом спрашиваем систему. */
    private fun askLocation(why: Int, onGranted: () -> Unit, onDenied: () -> Unit = {}) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.loc_title)
            .setMessage(why)
            .setPositiveButton(R.string.setup_allow) { _, _ ->
                afterLocation = { granted -> if (granted) onGranted() else onDenied() }
                locationLauncher.launch(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            .setNegativeButton(R.string.loc_not_now) { _, _ -> onDenied() }
            .setOnCancelListener { onDenied() }
            .show()
    }

    /** «Предупреждать в дороге»: включает постоянный GPS в радаре (по умолчанию выкл). */
    private fun setupRoadAlertsChip() {
        val chip = findViewById<com.google.android.material.chip.Chip>(R.id.chipRoadAlerts)
        chip.isChecked = RoadReports.alertsEnabled(this) && hasLocation()
        chip.setOnCheckedChangeListener { _, checked ->
            if (!checked) {
                RoadReports.setAlertsEnabled(this, false)
                FloatingWidgetService.setRoadAlerts()
            } else if (hasLocation()) {
                RoadReports.setAlertsEnabled(this, true)
                FloatingWidgetService.setRoadAlerts()
                Toast.makeText(this, getString(R.string.loc_alerts_on), Toast.LENGTH_SHORT).show()
            } else {
                askLocation(R.string.loc_alerts_why, onGranted = {
                    RoadReports.setAlertsEnabled(this, true)
                    FloatingWidgetService.setRoadAlerts()
                    mapController?.onLocationGranted()
                }, onDenied = { chip.isChecked = false })
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Смена языка пересоздаёт экран — остаёмся на той же вкладке.
        outState.putInt(STATE_TAB, currentTab)
    }

    override fun onPause() {
        mapController?.onHide()
        super.onPause()
    }

    override fun onDestroy() {
        mapController?.destroy()
        super.onDestroy()
    }

    private fun setupUsefulTiles() {
        findViewById<View>(R.id.tileChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
        // Клиенты и Аэропорт — на вкладке «Радар».
        // Клиенты и аэропорт — и на «Радаре», чтобы не искать.
        findViewById<View>(R.id.rowClients).setOnClickListener {
            startActivity(Intent(this, ClientsActivity::class.java))
        }
        findViewById<View>(R.id.rowAirport).setOnClickListener {
            startActivity(Intent(this, AirportActivity::class.java))
        }
        findViewById<View>(R.id.tileHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    /**
     * Плашка на вкладке «Радар», если без чего-то радар молча не работает.
     * Подробно (и с кнопками в нужные настройки) — в «Проверке настроек».
     */
    private fun renderSetupWarning() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val problems = listOf(
            !OrderAccessibilityService.isConnected,
            !Settings.canDrawOverlays(this),
            !pm.isIgnoringBatteryOptimizations(packageName)
        ).count { it }
        findViewById<View>(R.id.cardSetupWarning).visibility = if (problems > 0) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvSetupSub).apply {
            text = if (problems > 0) getString(R.string.setup_fix_count, problems) else getString(R.string.setup_all_ok)
            setTextColor(getColor(if (problems > 0) R.color.tr_warning else R.color.tr_success))
        }
        findViewById<TextView>(R.id.tvLanguageSub).text = when (AppLanguage.current()) {
            "ru" -> "Русский"
            "ro" -> "Română"
            else -> getString(R.string.lang_system)
        }
    }

    private fun renderTraffic() {
        val s = TrafficModel.stats(this)
        val factor = String.format(java.util.Locale.US, "%.2f", s.factorNow.value)
        val lines = mutableListOf(
            if (s.count > 0) getString(R.string.traffic_stats, s.count, factor)
            else getString(R.string.traffic_default, factor)
        )
        if (s.errorBefore != null && s.errorAfter != null && s.count >= 5) {
            lines += getString(R.string.traffic_error, Math.round(s.errorBefore).toInt(), Math.round(s.errorAfter).toInt())
        }
        findViewById<TextView>(R.id.tvTrafficStats).text = lines.joinToString("\n")
    }

    private fun showLanguageChooser() {
        // Названия языков — каждое на своём языке, чтобы его нашёл любой водитель.
        val tags = listOf("ru", "ro", "")
        val names = arrayOf("Русский", "Română", getString(R.string.lang_system))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.lang_title))
            .setSingleChoiceItems(names, tags.indexOf(AppLanguage.current()).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                // Если радар запущен — перезапускаем, чтобы виджет заговорил на новом языке.
                val restartWidget = FloatingWidgetService.isRunning
                if (restartWidget) stopService(Intent(this, FloatingWidgetService::class.java))
                AppLanguage.set(tags[which])
                if (restartWidget) {
                    val intent = Intent(this, FloatingWidgetService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                }
            }
            .show()
    }

    private fun setupNetCard() {
        val s = NetEarnings.load(this)
        switchNet.isChecked = s.enabled
        etFuelConsumption.setText(formatNumber(s.consumptionPer100Km))
        if (s.fuelPrice > 0) etFuelPrice.setText(formatNumber(s.fuelPrice))
        etCommission.setText(formatNumber(s.commissionPercent))
        renderNetVisibility(s.enabled)

        val save = {
            NetEarnings.save(
                this,
                NetEarnings.Settings(
                    enabled = switchNet.isChecked,
                    consumptionPer100Km = parseNumber(etFuelConsumption),
                    fuelPrice = parseNumber(etFuelPrice),
                    commissionPercent = parseNumber(etCommission).coerceIn(0.0, 100.0)
                )
            )
            updateNetExample()
        }
        switchNet.setOnCheckedChangeListener { _, checked ->
            renderNetVisibility(checked)
            save()
        }
        listOf(etFuelConsumption, etFuelPrice, etCommission).forEach { it.doAfterTextChanged { save() } }
        updateNetExample()
    }

    private fun renderNetVisibility(enabled: Boolean) {
        layoutNetFields.visibility = if (enabled) View.VISIBLE else View.GONE
        tvNetExample.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun updateNetExample() {
        val net = NetEarnings.compute(this, price = 100, tripKm = 10.0, pickupKm = 1.0)
        tvNetExample.text = if (net == null) {
            getString(R.string.net_need_fuel_price)
        } else {
            getString(R.string.net_example, net)
        }
    }

    private fun parseNumber(field: EditText): Double =
        field.text.toString().replace(',', '.').trim().toDoubleOrNull() ?: 0.0

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun setupContacts() {
        tileContact.setOnClickListener {
            showContactChooser(getString(R.string.msg_question, licenseManager.deviceId))
        }
        btnRenew.setOnClickListener { openSubscription() }
        findViewById<View>(R.id.btnBuySub).setOnClickListener { openSubscription() }
        tileGroup.setOnClickListener {
            AppConfig.load(this).groupUrl.takeIf { it.isNotBlank() }?.let { openUrl(it) }
        }
        renderContacts()
    }

    private fun renderContacts() {
        val visibility = if (AppConfig.load(this).groupUrl.isNotBlank()) View.VISIBLE else View.GONE
        tileGroup.visibility = if (visibility == View.VISIBLE) View.VISIBLE else View.INVISIBLE
    }

    private fun showContactChooser(message: String) {
        val cfg = AppConfig.load(this)
        val text = URLEncoder.encode(message, "UTF-8")
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (cfg.telegram.isNotBlank()) options += "Telegram" to { openUrl("https://t.me/${cfg.telegram}?text=$text") }
        if (cfg.whatsapp.isNotBlank()) options += "WhatsApp" to { openUrl("https://wa.me/${digitsOnly(cfg.whatsapp)}?text=$text") }
        if (cfg.viber.isNotBlank()) options += "Viber" to { openUrl("viber://chat?number=%2B${digitsOnly(cfg.viber)}") }
        if (cfg.phone.isNotBlank()) options += getString(R.string.contact_call, cfg.phone) to {
            openIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${cfg.phone}")))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.contact_title))
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun digitsOnly(value: String) = value.filter { it.isDigit() }

    private fun openUrl(url: String) = openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.app_not_installed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAppConfig() {
        CoroutineScope(Dispatchers.Main).launch {
            licenseManager.fetchAppConfig()?.let {
                AppConfig.save(this@MainActivity, it)
                renderContacts()
                renderYandexState()
                renderBell()
            }
        }
    }

    private fun setupReferralCard() {
        btnReferralShare.setOnClickListener {
            val code = tvReferralCode.text.toString()
            if (code.isBlank()) return@setOnClickListener
            val group = AppConfig.load(this).groupUrl
            val text = buildString {
                append(getString(R.string.ref_share_text, code))
                if (group.isNotBlank()) append(getString(R.string.ref_share_group, group))
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.ref_share_title)))
        }
        btnReferralEnter.setOnClickListener { showEnterReferralDialog() }
        findViewById<View>(R.id.btnReferralHow).setOnClickListener {
            val bonus = AppConfig.load(this).referralBonusDays
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ref_how_title)
                .setMessage(getString(R.string.ref_how_text, bonus))
                .setPositiveButton(R.string.intro_got_it, null)
                .show()
        }
    }

    private fun loadReferral() {
        CoroutineScope(Dispatchers.Main).launch {
            val info = licenseManager.referralInfo()
            if (info == null) {
                cardReferral.visibility = View.GONE
                return@launch
            }
            val bonus = info.optInt("bonus_days", AppConfig.load(this@MainActivity).referralBonusDays)
            tvReferralCode.text = info.optString("code")
            tvReferralCaption.text = getString(R.string.ref_caption, bonus)
            tvReferralStats.text = getString(R.string.ref_stats, info.optInt("invited"), info.optInt("rewarded"))

            val referredBy = if (info.isNull("referred_by")) null else info.optString("referred_by")
            if (referredBy.isNullOrBlank()) {
                btnReferralEnter.isEnabled = true
                btnReferralEnter.text = getString(R.string.ref_have_code)
            } else {
                btnReferralEnter.isEnabled = false
                btnReferralEnter.text = getString(R.string.ref_came_by, referredBy)
            }
            cardReferral.visibility = View.VISIBLE
        }
    }

    private fun showEnterReferralDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.ref_code_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            isSingleLine = true
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ref_code_hint))
            .setView(container)
            .setPositiveButton(getString(R.string.ref_apply)) { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val (ok, message) = licenseManager.applyReferralCode(input.text.toString())
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    if (ok) loadReferral()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // Ключ из кабинета Яндекса — UUID: 8-4-4-4-12 шестнадцатеричных символов.
    private val yandexKeyRegex = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
    private var lastAutoPastedKey: String? = null

    private val yandexKeyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        renderYandexState()
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, getString(R.string.yandex_key_ok), Toast.LENGTH_SHORT).show()
        }
    }

    /** Кабинет Яндекса внутри приложения — ключ оттуда забирается сам. */
    private fun openYandexConsole() {
        // Водитель пошёл за ключом — когда вернётся, заглянем в буфер обмена.
        expectKeyInClipboard = true
        yandexKeyLauncher.launch(Intent(this, YandexKeyActivity::class.java))
    }

    // Буфер читаем только после похода за ключом: иначе Android 12+ при каждом
    // открытии приложения пишет «Taxi Radar вставил из буфера обмена».
    private var expectKeyInClipboard = false

    private var pendingCheckAt = 0L

    /**
     * Ключ забрали из кабинета, но Яндекс его ещё не включил. Проверяем при
     * каждом открытии приложения (не чаще раза в минуту) и подключаем сами.
     */
    private fun checkPendingKeys() {
        if (YandexApiKey.get(this) != null) return
        val pending = YandexApiKey.getPending(this)
        if (pending.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - pendingCheckAt < 60_000) return
        pendingCheckAt = now
        CoroutineScope(Dispatchers.Main).launch {
            for (key in pending) {
                if (RouteFareCalculator.checkYandexKey(key) == RouteFareCalculator.KeyCheck.OK) {
                    YandexApiKey.save(this@MainActivity, key)
                    renderYandexState()
                    Toast.makeText(this@MainActivity, getString(R.string.yandex_pending_ok), Toast.LENGTH_LONG).show()
                    return@launch
                }
            }
        }
    }

    private fun readClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.trim()
    }

    /**
     * Водитель скопировал ключ в браузере и вернулся — подставляем и проверяем
     * сами, без «Вставить» → «Подключить». Буфер читаем только в фокусе окна:
     * с Android 10 иначе он пустой.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !expectKeyInClipboard || YandexApiKey.get(this) != null) return
        if (layoutYandexSetup.visibility != View.VISIBLE || !btnYandexSave.isEnabled) return
        expectKeyInClipboard = false
        val key = readClipboard()?.let { yandexKeyRegex.find(it)?.value } ?: return
        if (key == lastAutoPastedKey) return
        lastAutoPastedKey = key
        etYandexKey.setText(key)
        tilYandexKey.error = null
        showYandexCard()
        Toast.makeText(this, getString(R.string.yandex_key_found), Toast.LENGTH_SHORT).show()
        btnYandexSave.performClick()
    }

    private fun showYandexGuide() {
        val steps = listOf(
            getString(R.string.guide_step1_title) to
                getString(R.string.guide_step1_text),
            getString(R.string.guide_step2_title) to
                getString(R.string.guide_step2_text),
            getString(R.string.guide_step3_title) to
                getString(R.string.guide_step3_text),
            getString(R.string.guide_step4_title) to
                getString(R.string.guide_step4_text),
            getString(R.string.guide_step5_title) to
                getString(R.string.guide_step5_text),
            getString(R.string.guide_step6_title) to
                getString(R.string.guide_step6_text)
        )

        val pad = (22 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val inflater = LayoutInflater.from(this)
        steps.forEachIndexed { i, (title, text) ->
            val row = inflater.inflate(R.layout.item_guide_step, container, false)
            row.findViewById<TextView>(R.id.tvStepNumber).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.tvStepTitle).text = title
            row.findViewById<TextView>(R.id.tvStepText).text = text
            container.addView(row)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.guide_title))
            .setView(ScrollView(this).apply { addView(container) })
            .setPositiveButton(getString(R.string.guide_open)) { _, _ -> openYandexConsole() }
            .setNeutralButton(getString(R.string.guide_help)) { _, _ ->
                showContactChooser(getString(R.string.msg_yandex_help, licenseManager.deviceId))
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    /** Карточка ключа живёт в «Профиле» — переходим туда и прокручиваем к ней. */
    private fun showYandexCard() {
        bottomNav.selectedItemId = R.id.nav_profile
        val page = findViewById<ScrollView>(R.id.pageProfile)
        page.post { page.smoothScrollTo(0, (cardYandex.parent as View).top + cardYandex.top) }
    }

    private fun setupYandexCard() {
        btnYandexGetKey.setOnClickListener { openYandexConsole() }
        findViewById<View>(R.id.btnYandexGuide).setOnClickListener { showYandexGuide() }

        btnYandexPaste.setOnClickListener {
            val text = readClipboard()
            if (text.isNullOrEmpty()) {
                Toast.makeText(this, getString(R.string.yandex_clipboard_empty), Toast.LENGTH_SHORT).show()
            } else {
                // Скопировали вместе с лишним текстом — берём только сам ключ.
                etYandexKey.setText(yandexKeyRegex.find(text)?.value ?: text)
                tilYandexKey.error = null
            }
        }

        btnYandexSave.setOnClickListener {
            val key = etYandexKey.text.toString().replace(Regex("\\s"), "")
            if (key.isEmpty()) {
                tilYandexKey.error = getString(R.string.yandex_key_empty)
                return@setOnClickListener
            }
            tilYandexKey.error = null
            btnYandexSave.isEnabled = false
            btnYandexSave.text = getString(R.string.yandex_checking)

            CoroutineScope(Dispatchers.Main).launch {
                val result = RouteFareCalculator.checkYandexKey(key)
                btnYandexSave.isEnabled = true
                btnYandexSave.text = getString(R.string.yandex_connect)

                when (result) {
                    RouteFareCalculator.KeyCheck.OK -> {
                        YandexApiKey.save(this@MainActivity, key)
                        etYandexKey.setText("")
                        hideKeyboard()
                        renderYandexState()
                        Toast.makeText(this@MainActivity, getString(R.string.yandex_key_ok), Toast.LENGTH_SHORT).show()
                    }
                    RouteFareCalculator.KeyCheck.REJECTED -> {
                        tilYandexKey.error = getString(R.string.yandex_key_rejected)
                    }
                    RouteFareCalculator.KeyCheck.NETWORK_ERROR -> {
                        tilYandexKey.error = getString(R.string.yandex_no_network)
                    }
                }
            }
        }

        // «Изменить» не стирает рабочий ключ: он остаётся, пока новый не пройдёт проверку.
        btnYandexDisconnect.setOnClickListener {
            layoutYandexConnected.visibility = View.GONE
            layoutYandexSetup.visibility = View.VISIBLE
            etYandexKey.setText(YandexApiKey.get(this).orEmpty())
        }

        renderYandexState()
    }

    private fun renderYandexState() {
        val key = YandexApiKey.get(this)
        // Адреса ищет сервер — свой ключ нужен только как запасной.
        findViewById<TextView>(R.id.tvOwnKeyHint).setText(
            if (AppConfig.load(this).sharedGeocoder) R.string.own_key_hint else R.string.own_key_required
        )
        if (key != null) {
            tvYandexStatus.text = getString(R.string.yandex_connected)
            tvYandexStatus.setTextColor(color(R.color.tr_success))
            tvYandexKeyMasked.text = YandexApiKey.masked(key)
            layoutYandexSetup.visibility = View.GONE
            layoutYandexConnected.visibility = View.VISIBLE
            tilYandexKey.error = null
        } else {
            val waiting = YandexApiKey.getPending(this).isNotEmpty()
            tvYandexStatus.text = getString(if (waiting) R.string.yandex_pending_status else R.string.yandex_not_connected)
            tvYandexStatus.setTextColor(color(R.color.tr_warning))
            layoutYandexSetup.visibility = View.VISIBLE
            layoutYandexConnected.visibility = View.GONE
        }
        updateWidgetButtonState()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etYandexKey.windowToken, 0)
        etYandexKey.clearFocus()
    }

    // Метод для запроса отключения оптимизации батареи
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    private fun checkIfAppUpdated() {
        val prefs = getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_app_version", 0)

        val currentVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }

        if (currentVersion > lastVersion) {
            prefs.edit().putInt("last_app_version", currentVersion).apply()
            // Первая установка — это не обновление: тумблер ещё не включали, настроит мастер.
            if (lastVersion == 0) return
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.updated_title))
                .setMessage(getString(R.string.updated_message))
                .setPositiveButton(getString(R.string.updated_go)) { _, _ ->
                    openAccessibilitySettings()
                }
                .setNegativeButton(getString(R.string.got_it), null)
                .show()
        }
    }

    private fun loadSelectedTariffs() {
        val prefs = getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        cbEconom.isChecked = prefs.getBoolean("show_econom", true)
        cbComfort.isChecked = prefs.getBoolean("show_comfort", false)
        cbComfortPlus.isChecked = prefs.getBoolean("show_comfortplus", false)
    }

    override fun onResume() {
        super.onResume()
        renderYandexState()
        renderTraffic()
        renderSetupWarning()
        if (currentTab == R.id.nav_map) mapController?.onShow()
        checkPendingKeys()
        syncWithServer()
        refreshAppConfig()
    }

    private fun updateWidgetButtonState() {
        when {
            FloatingWidgetService.isRunning -> {
                styleLaunchButton(getString(R.string.radar_stop), R.drawable.ic_stop, R.color.tr_danger, R.color.white)
                tvLaunchHint.text = getString(R.string.radar_running_hint)
                tvLaunchHint.visibility = View.VISIBLE
            }
            !YandexApiKey.ready(this) -> {
                styleLaunchButton(getString(R.string.radar_start), R.drawable.ic_play, R.color.tr_surface_high, R.color.tr_text_muted)
                tvLaunchHint.text = getString(R.string.radar_need_yandex)
                tvLaunchHint.visibility = View.VISIBLE
            }
            else -> {
                styleLaunchButton(getString(R.string.radar_start), R.drawable.ic_play, R.color.tr_accent, R.color.tr_on_accent)
                tvLaunchHint.visibility = View.GONE
            }
        }
        if (btnLaunchWidget.visibility != View.VISIBLE) tvLaunchHint.visibility = View.GONE
    }

    private fun styleLaunchButton(text: String, icon: Int, @ColorRes background: Int, @ColorRes content: Int) {
        btnLaunchWidget.text = text
        btnLaunchWidget.setIconResource(icon)
        btnLaunchWidget.backgroundTintList = ColorStateList.valueOf(color(background))
        btnLaunchWidget.setTextColor(color(content))
        btnLaunchWidget.iconTint = ColorStateList.valueOf(color(content))
    }

    private fun setLicenseStatus(text: String, @ColorRes dotColor: Int) {
        tvLicenseStatus.text = text
        viewLicenseDot.backgroundTintList = ColorStateList.valueOf(color(dotColor))
        // Дни подписки — ещё и в шапке «Радара», рядом с названием.
        val days = licenseManager.getRemainingDays()
        findViewById<TextView>(R.id.tvHeaderDays).apply {
            visibility = if (days > 0) View.VISIBLE else View.GONE
            this.text = getString(R.string.header_days, days)
            setTextColor(color(if (days <= 3) R.color.tr_warning else R.color.tr_success))
            setOnClickListener { bottomNav.selectedItemId = R.id.nav_profile }
        }
    }

    private fun color(@ColorRes res: Int): Int = getColor(res)

    private fun requestBasePermissions() {
        // Надбавка за спрос — там, где стоит водитель: без геолокации её не узнать.
        // Спрашиваем не чаще раза в сутки; хватает и «приблизительной».
        val prefs = getSharedPreferences("taxi_radar_prefs", Context.MODE_PRIVATE)
        val askedAt = prefs.getLong("loc_surge_asked_at", 0L)
        if (!hasLocation() && System.currentTimeMillis() - askedAt > 24 * 3600_000L) {
            prefs.edit().putLong("loc_surge_asked_at", System.currentTimeMillis()).apply()
            askLocation(R.string.loc_surge_why, onGranted = { requestNotificationsAndProceed() }, onDenied = { requestNotificationsAndProceed() })
            return
        }
        requestNotificationsAndProceed()
    }

    private fun requestNotificationsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
        } else {
            checkOverlayAndProceed()
        }
    }

    private fun checkOverlayAndProceed() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } else {
            verifyAccessibilityBeforeStart()
        }
    }

    private fun isAccessibilityServiceRunning(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name.contains("OrderAccessibilityService")
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun verifyAccessibilityBeforeStart() {
        if (!isAccessibilityServiceRunning()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.acc_off_title))
                .setMessage(getString(R.string.acc_off_message))
                .setPositiveButton(getString(R.string.acc_off_enable)) { _, _ ->
                    openAccessibilitySettings()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .setCancelable(false)
                .show()
        } else {
            startOverlayService()
        }
    }

    private fun syncWithServer() {
        setLicenseStatus(getString(R.string.main_syncing_server), R.color.tr_warning)

        CoroutineScope(Dispatchers.Main).launch {
            val isServerValid = licenseManager.checkDeviceStatus()
            val days = licenseManager.getRemainingDays()

            if (isServerValid && days > 0) {
                setLicenseStatus(getString(R.string.license_active_days, days), R.color.tr_success)
                layoutActivation.visibility = View.GONE
                btnLaunchWidget.visibility = View.VISIBLE
            } else {
                if (!licenseManager.trialAlreadyUsed) {
                    // Триал выдаёт сервер, один раз на устройство. Отметку «использован»
                    // ставит LicenseManager, только если сервер реально ответил.
                    val trialResult = licenseManager.checkOrStartTrial()
                    if (trialResult.first) {
                        val trialDays = licenseManager.getRemainingDays()
                        setLicenseStatus(getString(R.string.license_trial_days, trialDays), R.color.tr_success)
                        layoutActivation.visibility = View.GONE
                        btnLaunchWidget.visibility = View.VISIBLE
                    } else {
                        // Ответ сервера уже переведён (ServerText) — сверяем и оригинал, и перевод.
                        val isBanned = trialResult.second.contains("БАН", ignoreCase = true) ||
                                trialResult.second == getString(R.string.srv_banned)
                        if (isBanned) {
                            setLicenseStatus(getString(R.string.license_banned), R.color.tr_danger)
                        } else {
                            setLicenseStatus(trialResult.second.ifEmpty { getString(R.string.license_expired_enter_key) }, R.color.tr_warning)
                        }
                        layoutActivation.visibility = View.VISIBLE
                        btnLaunchWidget.visibility = View.GONE
                    }
                } else {
                    // Триал уже был использован ранее: жестко блокируем
                    setLicenseStatus(getString(R.string.license_trial_over), R.color.tr_warning)
                    layoutActivation.visibility = View.VISIBLE
                    btnLaunchWidget.visibility = View.GONE
                }
            }
            updateWidgetButtonState()
            loadReferral()
            renderBell()
        }
    }

    private fun openSubscription() {
        startActivity(Intent(this, SubscriptionActivity::class.java))
    }

    // ---------- размер виджета ----------

    private fun setupWidgetSize() {
        val slider = findViewById<Slider>(R.id.sliderWidgetSize)
        val label = findViewById<TextView>(R.id.tvWidgetSize)
        val percent = WidgetSize.percent(this)
        slider.value = (percent / 10 * 10).toFloat().coerceIn(slider.valueFrom, slider.valueTo)
        label.text = getString(R.string.widget_size_value, slider.value.toInt())
        slider.addOnChangeListener { _, value, fromUser ->
            label.text = getString(R.string.widget_size_value, value.toInt())
            if (fromUser) {
                WidgetSize.save(this, value.toInt())
                FloatingWidgetService.applyWidgetScale()
            }
        }
    }

    // ---------- колокольчик ----------

    private fun currentVersionCode(): Int = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }

    private fun updateAvailable(cfg: AppConfig): Boolean =
        cfg.latestVersionCode > currentVersionCode() && cfg.updateUrl.isNotBlank()

    /** Подписка кончается (≤ 2 дней) или уже закрыта — тоже повод для точки. */
    private fun subscriptionAlert(): Boolean {
        val days = licenseManager.getRemainingDays()
        return licenseManager.trialAlreadyUsed && days <= 2
    }

    private fun renderBell() {
        val cfg = AppConfig.load(this)
        findViewById<View>(R.id.viewBellBadge).visibility =
            if (updateAvailable(cfg) || subscriptionAlert()) View.VISIBLE else View.GONE
    }

    private fun showNotifications() {
        val cfg = AppConfig.load(this)
        val days = licenseManager.getRemainingDays()
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        when {
            updateAvailable(cfg) -> {
                val text = listOf(cfg.updateNotes.trim(), getString(R.string.bell_update_text))
                    .filter { it.isNotEmpty() }.joinToString("\n\n")
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.bell_update_title, cfg.latestVersionName.ifBlank { cfg.latestVersionCode.toString() }))
                    .setMessage(text)
                    .setPositiveButton(R.string.bell_update_go) { _, _ -> openUrl(cfg.updateUrl) }
                    .setNegativeButton(R.string.bell_later, null)
                    .show()
            }
            subscriptionAlert() -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.bell_empty_title)
                    .setMessage(if (days > 0) getString(R.string.bell_sub_ending, days) else getString(R.string.bell_sub_over))
                    .setPositiveButton(R.string.bell_sub_go) { _, _ -> openSubscription() }
                    .setNegativeButton(R.string.bell_later, null)
                    .show()
            }
            else -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.bell_empty_title)
                .setMessage(getString(R.string.bell_empty_text, versionName))
                .setPositiveButton(R.string.got_it, null)
                .show()
        }
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, FloatingWidgetService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        updateWidgetButtonState()
        Toast.makeText(this, getString(R.string.radar_started), Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
