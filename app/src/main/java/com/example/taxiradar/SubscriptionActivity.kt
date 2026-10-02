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
 * открывает Telegram продавца с готовым сообщением. Ключ после оплаты вводится тут же.
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

    private fun pay() {
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
        val tg = config.telegram.ifBlank { "sigmalxl" }
        val url = "https://t.me/$tg?text=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.app_not_installed, Toast.LENGTH_SHORT).show()
        }
    }
}
