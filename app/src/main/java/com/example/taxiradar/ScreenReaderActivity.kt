package com.example.taxiradar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

/** Explicit setup: Android's capture permission is requested on every new session. */
class ScreenReaderActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(this@ScreenReaderActivity, 18), DriverUi.dp(this@ScreenReaderActivity, 24), DriverUi.dp(this@ScreenReaderActivity, 18), DriverUi.dp(this@ScreenReaderActivity, 24))
            setBackgroundColor(getColor(R.color.tr_bg)) }
        setContentView(ScrollView(this).apply { addView(box) })
        box.addView(DriverUi.text(this, t("Распознавание экрана", "Recunoașterea ecranului"), 26f))
        box.addView(DriverUi.text(this, t("Русские и румынские карточки Bolt/Uber, включая «Комфорт» и заказы со скрытой ценой. Пока заказа нет, на экране маленький значок TR. Новый заказ раскрывает карточку; «Свернуть» продолжает чтение.", "Oferte Bolt/Uber în rusă și română, inclusiv Comfort și preț ascuns. Fără ofertă rămâne simbolul TR. O ofertă nouă deschide cardul; «Ascunde» continuă citirea."), 15f))
        box.addView(DriverUi.text(this, t("Снимки обрабатываются на телефоне и не сохраняются. Для каждого заказа адреса и свежая GPS-позиция отправляются через сервер TAXI RADAR в сервис маршрутов (TomTom): считаем подачу и поездку с пробками. Скрытую цену оцениваем по таблице Bolt и множителю спроса. Это прогноз, а не гарантированная цена или время.", "Imaginile sunt procesate local, fără salvare. Pentru fiecare ofertă, adresele și poziția GPS recentă sunt trimise prin serverul TAXI RADAR la serviciul de trasee (TomTom) pentru preluare și cursă cu trafic. Prețul ascuns se estimează după tabelul Bolt și multiplicator. Durata și prețul sunt estimări."), 14f, true))
        val config = DriverUi.card(this, box, t("Область и расчёт", "Zona și calculul"), t("Область над значком и открытая панель исключаются из OCR автоматически. В разрешении Android выберите весь экран. Поворот экрана требует нового запуска.", "Zona deasupra simbolului și panoul deschis se exclud automat din OCR. Alegeți întregul ecran în permisiunea Android. Reporniți după rotire."))
        fun numeric(key: String, ru: String, ro: String, default: Double, min: Double, max: Double) {
            val f = DriverUi.field(this, config, t(ru, ro), DriverPreferences.number(this, key, default).toString())
            f.doAfterTextChanged { value ->
                val n = value.toString().replace(',', '.').toDoubleOrNull()
                if (n == null || !n.isFinite() || n !in min..max) f.error = "$min–$max"
                else { f.error = null; DriverPreferences.set(this, key, n) }
            }
        }
        numeric("ocr_top", "Начало области чтения, % высоты", "Începutul zonei, % din înălțime", 10.0, 8.0, 80.0)
        numeric("ocr_extra", "Дополнительное время на посадку, мин", "Timp suplimentar pentru îmbarcare, min", 0.0, 0.0, 30.0)
        DriverUi.toggle(this, config, t("Цена уже после комиссии", "Prețul este deja după comision"),
            t("Включите, если приложение водителя показывает вашу оплату после удержаний. Явная подпись «после комиссии» также распознаётся автоматически.", "Activați dacă aplicația șoferului arată plata după rețineri. Mențiunea explicită «după comision» este recunoscută automat."), DriverPreferences.flag(this, "ocr_net_fare")) { DriverPreferences.set(this, "ocr_net_fare", it) }
        val dict = DriverUi.card(this, box, t("Исправления OCR", "Corecții OCR"), t("Одна замена на строку: найденный текст => правильный текст. Хранится только на устройстве.", "O înlocuire pe rând: text recunoscut => text corect. Se păstrează doar pe dispozitiv."))
        val words = DriverUi.field(this, dict, t("Словарь замен", "Dicționar de înlocuiri"), DriverPreferences.text(this, "ocr_words"), false)
        words.setSingleLine(false); words.minLines = 3
        words.doAfterTextChanged { DriverPreferences.set(this, "ocr_words", it.toString().take(10000)) }
        DriverUi.button(this, box, t("Запустить чтение экрана", "Pornește citirea ecranului")) {
            if (!LicenseManager(this).isLicensed()) {
                startActivity(Intent(this, SubscriptionActivity::class.java)); return@button
            }
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))); return@button
            }
            if (ScreenReaderService.running) {
                Toast.makeText(this, t("Режим уже работает", "Modul este deja activ"), Toast.LENGTH_SHORT).show(); return@button
            }
            if (!ScreenReaderLocation.permitted(this)) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 402)
                Toast.makeText(this, t("Разрешите геолокацию, затем нажмите запуск ещё раз", "Permiteți locația, apoi apăsați din nou pornirea"), Toast.LENGTH_LONG).show()
                return@button
            }
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), 401)
        }
        DriverUi.button(this, box, t("Остановить чтение", "Oprește citirea")) { stopService(Intent(this, ScreenReaderService::class.java)) }
        DriverUi.button(this, box, t("Назад", "Înapoi")) { finish() }
    }
    @Deprecated("Android capture consent result")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != 401 || result != Activity.RESULT_OK || data == null) return
        val service = Intent(this, ScreenReaderService::class.java).putExtra("result", result).putExtra("capture", data)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
        Toast.makeText(this, t("Откройте приложение водителя. Свернуть — на карточке; остановить — в уведомлении или долгим нажатием TR.", "Deschideți aplicația șoferului. Ascundeți din card; opriți din notificare sau apăsând lung TR."), Toast.LENGTH_LONG).show()
    }
}
