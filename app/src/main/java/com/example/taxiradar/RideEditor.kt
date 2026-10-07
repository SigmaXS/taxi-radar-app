package com.example.taxiradar

import android.app.Activity
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/** Подтвердить или исправить сумму поездки, удалить ошибочную — из смены и из «Моих поездок». */
object RideEditor {
    private fun fmt(n: Double) = String.format(Locale.getDefault(), "%.1f", n)

    fun show(a: Activity, ride: DriverJournal.Ride?, done: () -> Unit) {
        fun t(ru: String, ro: String) = DriverUi.t(a, ru, ro)
        val o = if (ride == null) OrderPreview.current() else null
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(a, 20), 0, DriverUi.dp(a, 20), 0) }
        box.addView(DriverUi.text(a, t("Укажите фактически полученную сумму. Добавление подтверждает, что поездка состоялась.", "Introduceți suma primită efectiv. Salvarea confirmă că această cursă a avut loc."), 14f, true))
        val price = DriverUi.field(a, box, t("Фактическая оплата, L", "Plata reală, L"), (ride?.price ?: o?.price)?.toString().orEmpty())
        val km = DriverUi.field(a, box, t("Километры поездки", "Kilometrii cursei"), fmt(ride?.km ?: o?.km ?: 0.0))
        val pickup = DriverUi.field(a, box, t("Подача, км", "Preluare, km"), fmt(ride?.pickup ?: o?.pickup ?: 0.0))
        val minutes = DriverUi.field(a, box, t("Время поездки, мин", "Durata cursei, min"), (ride?.minutes ?: o?.minutes ?: 0).toString())
        val area = DriverUi.field(a, box, t("Район (необязательно)", "Zonă (opțional)"), ride?.area.orEmpty(), false)
        val builder = MaterialAlertDialogBuilder(a).setTitle(t("Подтвердить поездку", "Confirmă cursa")).setView(ScrollView(a).apply { addView(box) }).setPositiveButton(R.string.clients_save, null).setNegativeButton(R.string.cancel, null)
        if (ride != null) builder.setNeutralButton(t("Удалить", "Șterge")) { _, _ ->
            MaterialAlertDialogBuilder(a).setMessage(t("Удалить эту запись из истории?", "Ștergeți această înregistrare?"))
                .setPositiveButton(t("Удалить", "Șterge")) { _, _ -> DriverJournal.remove(a, ride.id); done() }.setNegativeButton(R.string.cancel, null).show()
        }
        val dialog = builder.create()
        dialog.setOnShowListener { dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val p = price.text.toString().replace(',', '.').toDoubleOrNull()
            val distance = km.text.toString().replace(',', '.').toDoubleOrNull()
            val pickupKm = pickup.text.toString().replace(',', '.').toDoubleOrNull()
            val duration = minutes.text.toString().toIntOrNull()
            if (p == null || p !in 1.0..100000.0) { price.error = t("Введите сумму от 1 до 100000 L", "Introduceți 1–100000 L"); return@setOnClickListener }
            if (distance == null || distance !in 0.0..3000.0) { km.error = t("Проверьте километры", "Verificați distanța"); return@setOnClickListener }
            if (pickupKm == null || pickupKm !in 0.0..1000.0) { pickup.error = t("Проверьте подачу", "Verificați preluarea"); return@setOnClickListener }
            if (duration == null || duration !in 1..1440) { minutes.error = t("От 1 до 1440 минут", "De la 1 la 1440 minute"); return@setOnClickListener }
            val amount = kotlin.math.round(p).toInt()
            val district = area.text.toString().trim().take(60)
            if (ride != null) DriverJournal.confirm(a, ride.id, amount, distance, pickupKm, duration, district) else DriverJournal.add(a, amount, o?.price ?: 0, distance, pickupKm, duration, district, true)
            dialog.dismiss(); done()
        } }
        dialog.show()
    }
}
