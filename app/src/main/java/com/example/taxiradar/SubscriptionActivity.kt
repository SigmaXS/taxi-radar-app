package com.example.taxiradar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

/**
 * «Подписка»: статус, выбор тарифа (из настроек сервера), промокод и «Оплатить» —
 * перевод MIA на номер продавца и «Я оплатил»: продавцу приходит заявка в Telegram,
 * он подтверждает — подписка продлевается сама, без ключа. Запасной путь — написать
 * продавцу и ввести ключ тут же.
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var licenseManager: LicenseManager
    private lateinit var config: AppConfig
    private lateinit var btnPay: MaterialButton
    private lateinit var etPromo: TextInputEditText
    private val cards = mutableListOf<MaterialCardView>()
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)
        licenseManager = LicenseManager(this)
        config = AppConfig.load(this)

        btnPay = findViewById(R.id.btnPay)
        etPromo = findViewById(R.id.etPromo)
        findViewById<View>(R.id.btnSubBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvSubDeviceId).apply {
            text = getString(R.string.sub_device_id, licenseManager.deviceId)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ID", licenseManager.deviceId))
                Toast.makeText(this@SubscriptionActivity, R.string.sub_id_copied, Toast.LENGTH_SHORT).show()
            }
        }

        buildTariffs()
        btnPay.setOnClickListener { pay() }

        val etKey = findViewById<TextInputEditText>(R.id.etSubKey)
        val btnActivate = findViewById<MaterialButton>(R.id.btnSubActivate)
        btnActivate.setOnClickListener {
            val key = etKey.text?.toString()?.trim().orEmpty()
            if (key.isEmpty()) {
                Toast.makeText(this, R.string.license_enter_key, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnActivate.isEnabled = false
            btnActivate.setText(R.string.license_checking)
            lifecycleScope.launch {
                val (ok, message) = licenseManager.activateKey(key)
                btnActivate.isEnabled = true
                btnActivate.setText(R.string.license_activate)
                Toast.makeText(this@SubscriptionActivity, message, Toast.LENGTH_LONG).show()
                if (ok) {
                    etKey.text?.clear()
                    renderStatus()
                }
            }
        }

        renderStatus()
        // Свежие тарифы с сервера (цены меняются в Railway без обновления приложения).
        lifecycleScope.launch {
            licenseManager.fetchAppConfig()?.let {
                AppConfig.save(this@SubscriptionActivity, it)
                config = AppConfig.load(this@SubscriptionActivity)
                buildTariffs()
            }
        }
    }

    private fun renderStatus() {
        val dot = findViewById<View>(R.id.viewSubDot)
        val tv = findViewById<TextView>(R.id.tvSubStatus)
        lifecycleScope.launch {
            val valid = licenseManager.checkDeviceStatus()
            val days = licenseManager.getRemainingDays()
            if (valid && days > 0) {
                tv.text = getString(R.string.license_active_days, days)
                dot.backgroundTintList = ColorStateList.valueOf(getColor(if (days <= 3) R.color.tr_warning else R.color.tr_success))
            } else {
                tv.setText(R.string.sub_status_none)
                dot.backgroundTintList = ColorStateList.valueOf(getColor(R.color.tr_danger))
            }
        }
    }

    private fun buildTariffs() {
        val box = findViewById<LinearLayout>(R.id.layoutTariffs)
        box.removeAllViews()
        cards.clear()
        val currency = config.currencyLabel(this)
        config.tariffs.forEachIndexed { i, t ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_tariff, box, false) as MaterialCardView
            card.findViewById<TextView>(R.id.tvTariffDays).text = getString(R.string.sub_days, t.days)
            card.findViewById<TextView>(R.id.tvTariffPrice).text = getString(R.string.sub_price, t.price, currency)
            val perDay = String.format(Locale.US, "%.1f", t.price.toDouble() / t.days).removeSuffix(".0")
            card.findViewById<TextView>(R.id.tvTariffPerDay).text = getString(R.string.sub_per_day, perDay, currency)
            card.setOnClickListener { select(i) }
            box.addView(card)
            cards += card
        }
        select(selected.coerceIn(0, (cards.size - 1).coerceAtLeast(0)))
    }

    private fun select(index: Int) {
        selected = index
        cards.forEachIndexed { i, c -> c.isChecked = i == index }
        val t = config.tariffs.getOrNull(index) ?: return
        btnPay.text = getString(R.string.sub_pay, t.price, config.currencyLabel(this))
    }

    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)

    /** Перевод MIA: номер продавца, сумма, свой номер и «Я оплатил». */
    private fun pay() {
        val tariff = config.tariffs.getOrNull(selected) ?: return
        val currency = config.currencyLabel(this)
        val mia = config.miaPhone.ifBlank { config.phone }
        if (mia.isBlank()) { writeSeller(); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(this@SubscriptionActivity, 22), DriverUi.dp(this@SubscriptionActivity, 8), DriverUi.dp(this@SubscriptionActivity, 22), 0) }
        box.addView(DriverUi.text(this, t("1. Переведите ${tariff.price} $currency через MIA на номер:", "1. Transferați ${tariff.price} $currency prin MIA la numărul:"), 15f))
        box.addView(DriverUi.text(this, mia, 26f))
        if (config.miaName.isNotBlank()) box.addView(DriverUi.text(this, t("Получатель: ${config.miaName}", "Destinatar: ${config.miaName}"), 14f, true))
        DriverUi.button(this, box, t("Скопировать номер", "Copiază numărul")) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MIA", mia))
            Toast.makeText(this, t("Номер скопирован", "Numărul a fost copiat"), Toast.LENGTH_SHORT).show()
        }
        box.addView(DriverUi.text(this, t("2. Ваш номер — продавец позвонит, если перевод не найдётся:", "2. Numărul dvs. — vânzătorul sună dacă nu găsește transferul:"), 15f))
        val phone = DriverUi.field(this, box, t("Ваш телефон", "Telefonul dvs."), DriverPreferences.text(this, "my_phone"), false)
        phone.inputType = android.text.InputType.TYPE_CLASS_PHONE
        box.addView(DriverUi.text(this, t("3. Нажмите «Я оплатил». Подписка включится сама, как только продавец увидит перевод.", "3. Apăsați «Am plătit». Abonamentul pornește singur când vânzătorul vede transferul."), 14f, true))
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sub_pay, tariff.price, currency))
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(t("Я оплатил", "Am plătit"), null)
            .setNeutralButton(t("Написать продавцу", "Scrie vânzătorului")) { _, _ -> writeSeller() }
            .setNegativeButton(R.string.cancel, null)
            .show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = phone.text.toString().trim()
            if (number.count { it.isDigit() } < 8) { phone.error = t("Введите номер", "Introduceți numărul"); return@setOnClickListener }
            DriverPreferences.set(this, "my_phone", number)
            it.isEnabled = false
            lifecycleScope.launch {
                val body = org.json.JSONObject().put("days", tariff.days).put("phone", number)
                    .put("promo", etPromo.text?.toString()?.trim()?.uppercase().orEmpty()).put("device_info", licenseManager.deviceInfo)
                val r = CommunityApi.post(this@SubscriptionActivity, "/api/pay/claim", body)
                it.isEnabled = true
                if (r?.optBoolean("ok") == true) {
                    dialog.dismiss()
                    Toast.makeText(this@SubscriptionActivity, t("Заявка отправлена. Подписка включится, как только продавец подтвердит перевод.", "Cererea a fost trimisă. Abonamentul pornește după confirmarea transferului."), Toast.LENGTH_LONG).show()
                    waitForPayment()
                } else Toast.makeText(this@SubscriptionActivity, r?.optString("message")?.ifBlank { null } ?: getString(R.string.srv_no_connection), Toast.LENGTH_LONG).show()
            }
        }
    }

    private var waitJob: kotlinx.coroutines.Job? = null

    /** Пока экран открыт — ждём подтверждения продавца (до 20 минут). */
    private fun waitForPayment() {
        waitJob?.cancel()
        val status = findViewById<TextView>(R.id.tvSubStatus)
        status.text = t("⏳ Ждём подтверждения оплаты…", "⏳ Așteptăm confirmarea plății…")
        waitJob = lifecycleScope.launch {
            repeat(80) {
                kotlinx.coroutines.delay(15_000)
                val claim = CommunityApi.post(this@SubscriptionActivity, "/api/pay/status")?.optJSONObject("claim") ?: return@repeat
                when (claim.optString("status")) {
                    "ok" -> {
                        licenseManager.checkDeviceStatus()
                        renderStatus()
                        Toast.makeText(this@SubscriptionActivity, t("✓ Оплата подтверждена — подписка продлена", "✓ Plata confirmată — abonamentul prelungit"), Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    "no" -> {
                        renderStatus()
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SubscriptionActivity)
                            .setMessage(t("Продавец не нашёл ваш перевод. Проверьте номер и сумму или напишите ему.", "Vânzătorul nu a găsit transferul. Verificați numărul și suma sau scrieți-i."))
                            .setPositiveButton(t("Написать", "Scrie")) { _, _ -> writeSeller() }
                            .setNegativeButton(R.string.cancel, null).show()
                        return@launch
                    }
                }
            }
            renderStatus()
        }
    }

    /** Запасной путь: написать продавцу в Telegram / WhatsApp / Viber и получить ключ. */
    private fun writeSeller() {
        val t = config.tariffs.getOrNull(selected) ?: return
        val currency = config.currencyLabel(this)
        val promo = etPromo.text?.toString()?.trim()?.uppercase().orEmpty()
        val message = getString(
            R.string.sub_pay_message,
            getString(R.string.sub_days, t.days),
            getString(R.string.sub_price, t.price, currency),
            promo.ifEmpty { getString(R.string.sub_no_promo) },
            licenseManager.deviceId
        )
        // Водитель сам выбирает, где ему удобнее написать; текст заявки уже готов.
        val text = URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        val digits = { v: String -> v.filter { it.isDigit() } }
        val options = mutableListOf<Pair<String, () -> Unit>>()
        val tg = config.telegram.ifBlank { "sigmalxl" }
        options += "Telegram" to { open("https://t.me/$tg?text=$text") }
        if (config.whatsapp.isNotBlank()) options += "WhatsApp  ${config.whatsapp}" to { open("https://wa.me/${digits(config.whatsapp)}?text=$text") }
        if (config.viber.isNotBlank()) options += "Viber  ${config.viber}" to {
            // Viber не принимает готовый текст в ссылке — кладём заявку в буфер, водитель вставляет.
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Taxi Radar", message))
            Toast.makeText(this, R.string.sub_pay_copied, Toast.LENGTH_LONG).show()
            open("viber://chat?number=%2B${digits(config.viber)}")
        }
        if (config.phone.isNotBlank()) options += getString(R.string.contact_call, config.phone) to {
            try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${config.phone}"))) } catch (_: Exception) {}
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sub_pay_where)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
        }
    }
}
