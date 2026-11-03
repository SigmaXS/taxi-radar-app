package com.example.taxiradar

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * «Помощь на дороге»: своя заявка и кто откликнулся, форма «Попросить помощь»,
 * заявки водителей рядом с кнопкой «Помогу». С extra "danger" сразу открывает «Опасность».
 */
class RoadHelpActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private lateinit var mineBox: LinearLayout
    private lateinit var nearbyBox: LinearLayout
    private var refreshJob: Job? = null
    private val accepted = mutableMapOf<Long, RoadHelp.Request>()

    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    private fun dp(n: Int) = DriverUi.dp(this, n)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.tr_bg)) }
        val header = LinearLayout(this).apply { setPadding(dp(18), dp(12), dp(18), 0) }
        DriverUi.header(this, header, t("Помощь на дороге", "Ajutor pe drum"))
        root.addView(header)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(24)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        dangerButton()
        mineBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mineBox)
        askCard()
        val near = DriverUi.card(this, body, t("Рядом просят помощь", "Cer ajutor în apropiere"),
            t("Заявки водителей Taxi Radar в 5 км (через 3 минуты без ответа — в 10 км). Номер и точку покажем, когда нажмёте «Помогу». Деньги — между вами, приложение их не трогает.",
                "Cereri ale șoferilor Taxi Radar în 5 km (după 3 minute fără răspuns — 10 km). Numărul și locul apar după «Ajut». Banii — între voi, aplicația nu îi atinge."), R.drawable.ic_location_on)
        nearbyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        near.addView(nearbyBox)
        DriverUi.toggle(this, near, t("Сообщать о заявках рядом", "Anunță cererile din apropiere"),
            t("Пока радар включён, раз в 30 секунд проверяем заявки рядом. На заказе уведомление приходит без звука, «Опасность» — всегда со звуком.",
                "Cât radarul e pornit, verificăm cererile la 30 de secunde. În cursă notificarea vine fără sunet, «Pericol» — mereu cu sunet."),
            DriverPreferences.flag(this, "help_alerts", true)) { DriverPreferences.set(this, "help_alerts", it) }
        if (intent.getBooleanExtra("danger", false)) danger()
    }

    override fun onResume() {
        super.onResume()
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch { while (isActive) { refresh(); delay(30_000) } }
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
    }

    // ---------- опасность ----------

    private fun dangerButton() {
        DriverUi.button(this, body, t("🚨 Опасность — позвать водителей рядом", "🚨 Pericol — cheamă șoferii din apropiere")) { danger() }.apply {
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.tr_danger)); strokeWidth = 0
            setTextColor(getColor(android.R.color.white)); textSize = 16f
        }
    }

    private fun danger() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        box.addView(DriverUi.text(this, t("Водители Taxi Radar в 10 км сразу получат громкое уведомление с вашей точкой и номером. Если угрожает жизни — сначала звоните 112.",
            "Șoferii Taxi Radar în 10 km primesc imediat o notificare sonoră cu locul și numărul dvs. Dacă viața e în pericol — sunați întâi la 112."), 15f))
        val phone = DriverUi.field(this, box, t("Ваш телефон", "Telefonul dvs."), RoadHelp.myPhone(this), false).apply { inputType = InputType.TYPE_CLASS_PHONE }
        val note = DriverUi.field(this, box, t("Что случилось (можно пусто)", "Ce s-a întâmplat (opțional)"), "", false)
        val d = MaterialAlertDialogBuilder(this)
            .setTitle(t("🚨 Опасность", "🚨 Pericol"))
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(t("Позвать водителей", "Cheamă șoferii"), null)
            .setNeutralButton(t("Позвонить 112", "Sună la 112")) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) } catch (_: Exception) {}
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            send(RoadHelp.DANGER.key, 0, phone, note.text.toString()) { d.dismiss() }
        }
    }

    // ---------- своя заявка ----------

    private fun askCard() {
        val card = DriverUi.card(this, body, t("Попросить помощь", "Cere ajutor"),
            t("Заявка уйдёт водителям рядом, у кого включён радар. Укажите, сколько готовы заплатить, — так быстрее откликнутся. Заявка живёт час, одна на водителя.",
                "Cererea ajunge la șoferii din apropiere cu radarul pornit. Indicați cât plătiți — răspund mai repede. Cererea e activă o oră, una per șofer."), R.drawable.ic_help)
        val group = RadioGroup(this)
        RoadHelp.KINDS.forEachIndexed { i, k ->
            group.addView(RadioButton(this).apply { id = View.generateViewId(); text = "${k.emoji}  ${t(k.ru, k.ro)}"; textSize = 16f; minHeight = dp(48); tag = k.key; isChecked = i == 0 })
        }
        card.addView(group)
        val price = DriverUi.field(this, card, t("Готов заплатить, лей (0 — за спасибо)", "Plătesc, lei (0 — pe gratis)"), "100", false).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val note = DriverUi.field(this, card, t("Подробнее (марка, где стою)", "Detalii (marca, unde stau)"), "", false)
        val phone = DriverUi.field(this, card, t("Ваш телефон", "Telefonul dvs."), RoadHelp.myPhone(this), false).apply { inputType = InputType.TYPE_CLASS_PHONE }
        DriverUi.button(this, card, t("Отправить водителям рядом", "Trimite șoferilor din apropiere")) {
            val kind = group.findViewById<RadioButton>(group.checkedRadioButtonId)?.tag as? String ?: "other"
            send(kind, price.text.toString().toIntOrNull() ?: 0, phone, note.text.toString()) {}
        }
    }

    private fun send(kind: String, price: Int, phoneField: android.widget.EditText, note: String, done: () -> Unit) {
        val phone = phoneField.text.toString().trim()
        if (phone.count { it.isDigit() } < 8) { phoneField.error = t("Введите номер", "Introduceți numărul"); return }
        Toast.makeText(this, t("Определяем место…", "Determinăm locul…"), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val loc = RoadHelp.location(this@RoadHelpActivity)
            if (loc == null) {
                Toast.makeText(this@RoadHelpActivity, t("Нет точки GPS — включите геолокацию", "Fără GPS — porniți localizarea"), Toast.LENGTH_LONG).show()
                return@launch
            }
            val (ok, message) = RoadHelp.create(this@RoadHelpActivity, kind, price, phone, note.trim(), loc.latitude, loc.longitude)
            Toast.makeText(this@RoadHelpActivity, if (ok) t("Отправлено. Когда кто-то откликнется — придёт уведомление.", "Trimis. Veți primi o notificare când cineva răspunde.") else message.ifBlank { getString(R.string.srv_no_connection) }, Toast.LENGTH_LONG).show()
            if (ok) { done(); refresh() }
        }
    }

    // ---------- обновление ----------

    private suspend fun refresh() {
        RoadHelp.mine(this)?.let { renderMine(it) }
        val loc = RoadHelp.location(this)
        if (loc == null) {
            nearbyBox.removeAllViews()
            nearbyBox.addView(DriverUi.text(this, t("Включите геолокацию, чтобы видеть заявки рядом.", "Porniți localizarea pentru cererile din apropiere."), 14f, true))
            return
        }
        val list = RoadHelp.nearby(this, loc.latitude, loc.longitude) ?: return
        renderNearby(list, loc.latitude, loc.longitude)
    }

    private fun renderMine(m: RoadHelp.Mine) {
        mineBox.removeAllViews()
        if (m.id == 0L) return
        val card = DriverUi.card(this, mineBox, t("Ваша заявка", "Cererea dvs."), t("Видна водителям рядом час. Закройте её, когда помощь пришла.", "E vizibilă o oră. Închideți-o când ați primit ajutor."), R.drawable.ic_help)
        card.addView(DriverUi.text(this, RoadHelp.label(this, m.kind) + if (m.kind != RoadHelp.DANGER.key) " · " + (if (m.price > 0) "${m.price} L" else t("за спасибо", "pe gratis")) else "", 17f))
        if (m.helpers.isEmpty()) card.addView(DriverUi.text(this, t("⏳ Пока никто не откликнулся", "⏳ Încă nu a răspuns nimeni"), 14f, true))
        m.helpers.forEach { h ->
            val who = h.nickname.ifBlank { t("Водитель", "Șofer") } + (h.dist?.let { " · " + RoadHelp.distance(this, it) } ?: "")
            card.addView(DriverUi.text(this, "🙌 $who", 15f))
            if (h.phone.isNotBlank()) DriverUi.button(this, card, t("Позвонить ${h.phone}", "Sună ${h.phone}")) { RoadHelp.call(this, h.phone) }
        }
        DriverUi.button(this, card, t("✓ Помощь пришла — закрыть", "✓ Am primit ajutor — închide")) {
            lifecycleScope.launch { if (RoadHelp.close(this@RoadHelpActivity)) refresh() }
        }
    }

    private fun renderNearby(list: List<RoadHelp.Request>, lat: Double, lon: Double) {
        nearbyBox.removeAllViews()
        if (list.isEmpty()) {
            nearbyBox.addView(DriverUi.text(this, t("Сейчас рядом никто не просит помощь.", "Acum nimeni nu cere ajutor în apropiere."), 14f, true))
            return
        }
        val highlight = intent.getLongExtra("request", 0)
        list.sortedByDescending { it.id == highlight }.forEach { r ->
            val open = accepted[r.id] ?: r.takeIf { it.phone != null }
            nearbyBox.addView(DriverUi.text(this, "${RoadHelp.label(this, r.kind)} · ${RoadHelp.distance(this, r.dist)}", 17f))
            nearbyBox.addView(DriverUi.text(this, listOf(RoadHelp.priceText(this, r), r.note, if (r.helpers > 0) t("уже едут: ${r.helpers}", "vin deja: ${r.helpers}") else "")
                .filter { it.isNotBlank() }.joinToString(" · "), 14f, true))
            if (open?.phone != null) {
                DriverUi.button(this, nearbyBox, t("Позвонить ${open.phone}", "Sună ${open.phone}")) { RoadHelp.call(this, open.phone) }
                if (open.lat != null && open.lon != null) DriverUi.button(this, nearbyBox, t("Маршрут до места", "Traseu până la loc")) { RoadHelp.navigate(this, open.lat, open.lon) }
            }
            if (!r.accepted && r.id !in accepted) DriverUi.button(this, nearbyBox, t("🙌 Помогу", "🙌 Ajut")) {
                lifecycleScope.launch {
                    val got = RoadHelp.accept(this@RoadHelpActivity, r.id, lat, lon)
                    if (got == null) Toast.makeText(this@RoadHelpActivity, t("Заявка уже закрыта", "Cererea s-a închis"), Toast.LENGTH_SHORT).show()
                    else { accepted[r.id] = got; renderNearby(list, lat, lon) }
                }
            }
            nearbyBox.addView(View(this).apply { setBackgroundColor(getColor(R.color.tr_console_border)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(12) })
        }
    }
}
