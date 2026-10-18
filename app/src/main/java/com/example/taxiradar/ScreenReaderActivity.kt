package com.example.taxiradar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged

class ScreenReaderActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    override fun onCreate(state: Bundle?) { super.onCreate(state); enableDurationDefault(); render() }
    private fun enableDurationDefault() {
        if (!DriverPreferences.flag(this, "bolt_duration_default_v2")) {
            DriverPreferences.set(this, "bolt_time", true)
            DriverPreferences.set(this, "bolt_duration_default_v2", true)
        }
    }
    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(this@ScreenReaderActivity, 18), DriverUi.dp(this@ScreenReaderActivity, 24), DriverUi.dp(this@ScreenReaderActivity, 18), DriverUi.dp(this@ScreenReaderActivity, 24))
            setBackgroundColor(getColor(R.color.tr_bg))
        }
        setContentView(ScrollView(this).apply { addView(root) })
        DriverUi.button(this, root, t("‹ Назад", "‹ Înapoi")) { finish() }
        root.addView(DriverUi.text(this, t("Настройки Bolt", "Setări Bolt"), 28f))
        root.addView(DriverUi.text(this, t("Цена из Bolt, сумма после комиссии и леи за километр. Если цена скрыта, показываем успешный расчёт с ≈. Если расчёт не удался, карточку не открываем. В OCR дополнительно доступны карта и общее время.", "Prețul din Bolt, suma netă și lei/km. Dacă prețul este ascuns, afișăm estimarea reușită cu ≈. Fără estimare nu deschidem oferta. În OCR sunt disponibile și harta și durata totală."), 15f))
        if (!RadarServices.bolt(this)) DriverUi.button(this, root, t("Включить Bolt вместе с Яндексом", "Activează Bolt împreună cu Yandex")) { RadarServices.select(this, "both"); render() }
        if (intent.getBooleanExtra("widget_only", false)) {
            widgetSettings(root)
            return
        }
        DriverUi.navigationCard(this, root, t("Вид виджета Bolt", "Aspectul widgetului Bolt"),
            t("Размер, прозрачность, цена и кнопка «+»", "Mărime, opacitate, preț și butonul «+»")) {
            startActivity(Intent(this, ScreenReaderActivity::class.java).putExtra("widget_only", true))
        }
        val reader = DriverUi.card(this, root, t("Как читать заказы", "Cum citim ofertele"),
            t("Сначала попробуйте специальные возможности: текст читается без снимков. Если Bolt не отдаёт текст, выберите OCR и разрешите захват экрана. Одновременно работает только один способ Bolt; Яндекс от этого не отключается.", "Încercați întâi accesibilitatea: citește textul fără capturi. Dacă Bolt nu oferă text, alegeți OCR și permiteți captura. Funcționează o singură metodă Bolt; Yandex rămâne activ."))
        val methods = RadioGroup(this)
        listOf("accessibility" to t("Специальные возможности — рекомендуется", "Accesibilitate — recomandat"), "ocr" to t("Чтение экрана — OCR", "Captură de ecran — OCR")).forEach { (key, label) ->
            methods.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = label; textSize = 16f; minHeight = DriverUi.dp(this@ScreenReaderActivity, 52)
                isChecked = RadarServices.method(this@ScreenReaderActivity) == key
                setOnCheckedChangeListener { _, checked -> if (checked) { RadarServices.method(this@ScreenReaderActivity, key); TripLog.init(this@ScreenReaderActivity); TripLog.d("Bolt: способ чтения $key") } }
            })
        }
        reader.addView(methods)
        DriverUi.button(this, reader, t("Открыть специальные возможности", "Deschide accesibilitatea")) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        DriverUi.button(this, reader, t("Запустить OCR с разрешением Android", "Pornește OCR cu permisiunea Android")) {
            if (!RadarServices.bolt(this)) { Toast.makeText(this, t("Сначала включите Bolt", "Activați întâi Bolt"), Toast.LENGTH_LONG).show(); return@button }
            if (RadarServices.method(this) != "ocr") { Toast.makeText(this, t("Выберите способ «Чтение экрана — OCR»", "Selectați metoda OCR"), Toast.LENGTH_LONG).show(); return@button }
            startCapture()
        }
        DriverUi.button(this, reader, t("Остановить OCR", "Oprește OCR")) { stopService(Intent(this, ScreenReaderService::class.java)) }
        fun flag(parent: LinearLayout, key: String, ru: String, ro: String, helpRu: String, helpRo: String, default: Boolean) {
            DriverUi.toggle(this, parent, t(ru, ro), t(helpRu, helpRo), DriverPreferences.flag(this, key, default)) { enabled ->
                DriverPreferences.set(this, key, enabled)
                if (key == "ocr_with_costs" && enabled) startActivity(Intent(this, DriverToolsActivity::class.java).putExtra("mode", "costs"))
            }
        }
        fun numeric(parent: LinearLayout, key: String, ru: String, ro: String, helpRu: String, helpRo: String, default: Double, min: Double, max: Double) {
            val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(DriverUi.text(this, t(ru, ro), 16f), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(DriverUi.info(this, t(ru, ro), t(helpRu, helpRo))); parent.addView(row)
            val field = DriverUi.field(this, parent, t(ru, ro), DriverPreferences.number(this, key, default).toString())
            field.doAfterTextChanged { value ->
                val n = value.toString().replace(',', '.').toDoubleOrNull()
                if (n != null && n.isFinite() && n in min..max) { field.error = null; DriverPreferences.set(this, key, n) }
                else field.error = "$min–$max"
            }
        }
        val card = DriverUi.card(this, root, t("Что показывать", "Ce afișăm"), t("Настройки относятся только к Bolt. Цена остаётся основной строкой, остальные показатели можно убрать.", "Setări doar pentru Bolt. Prețul rămâne principal; puteți ascunde restul."))
        flag(card, "bolt_per_km", "Леи за километр", "Lei pe kilometru", "Доход после комиссии, делённый на подачу и поездку. Расходы машины вычитаются только при включённой настройке ниже.", "Venit după comision împărțit la km de preluare și cursă. Costurile mașinii se scad doar dacă activați opțiunea.", true)
        flag(card, "bolt_time", "Общая длительность заказа · OCR", "Durata totală a comenzii · OCR", "Подача + поездка + ваш запас на посадку. До ответа маршрутизатора показаны минуты из Bolt с пометкой Bolt; затем — прогноз с пробками. На скриншотах включите режим теста, иначе подача считается от вашего текущего GPS. Спецвозможности показывают только цену, остаток и L/km.", "Preluare + cursă + îmbarcare. Până la răspuns afișăm minutele Bolt, apoi estimarea cu trafic. Pentru capturi activați modul de test; altfel preluarea se calculează din GPS-ul actual. Accesibilitatea arată doar preț, net și L/km.", true)
        flag(card, "ocr_with_costs", "Учитывать расходы машины", "Include costurile mașinii", "Из дохода вычитаются топливо и обслуживание из общих настроек машины. Не меняет цену заказа.", "Scade combustibilul și întreținerea din venitul calculat, nu din prețul comenzii.", false)
        flag(card, "bolt_show_net", "Показывать сумму после комиссии", "Arată suma după comision", "Основная цена — из Bolt. Красная цифра ниже — остаток после комиссии. Расходы вычитаем только при включённой опции.", "Prețul principal este din Bolt. Numărul roșu de mai jos este suma netă. Costurile se scad doar dacă sunt activate.", true)
        flag(card, "ocr_net_fare", "Видимая цена Bolt уже после комиссии", "Prețul Bolt este deja după comision", "Только для видимой цены из Bolt. Включайте, если она уже после удержаний. Основная цена не меняется; остаток показан отдельно.", "Activați doar dacă suma este deja după rețineri: comisionul nu se scade de două ori.", false)
        numeric(card, "ocr_commission", "Комиссия Bolt, %", "Comision Bolt, %", "Используется только для расчёта дохода, если комиссия ещё не вычтена из цены карточки.", "Se aplică venitului doar dacă prețul ofertei nu este deja net.", DriverPreferences.costs(this).commission, 0.0, 50.0)
        mapSettings(root)
        val delay = DriverUi.card(this, root, t("Скрытие карточки", "Ascunderea ofertei"), t("Пока предложение видно — расчёт остаётся. После исчезновения или принятия ждём выбранное время и возвращаем радар. «Свернуть» сохраняется до следующего предложения.", "Cât timp oferta este vizibilă, calculul rămâne. După dispariție sau acceptare revenim la radar după timpul ales. Ascunderea manuală se păstrează până la oferta următoare."))
        val seconds = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf(6, 8, 10).forEach { n -> seconds.addView(RadioButton(this).apply {
            id = View.generateViewId(); text = "$n " + t("сек", "sec"); minHeight = DriverUi.dp(this@ScreenReaderActivity, 48)
            isChecked = RadarServices.hideMs(this@ScreenReaderActivity) / 1000 == n.toLong()
            setOnCheckedChangeListener { _, checked -> if (checked) DriverPreferences.set(this@ScreenReaderActivity, "bolt_hide_seconds", n.toDouble()) }
        }) }; delay.addView(seconds)
        val extraToggle = DriverUi.button(this, root, t("Дополнительно: OCR и тестирование ▾", "Avansat: OCR și testare ▾")) {}
        val extra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }; root.addView(extra)
        extraToggle.setOnClickListener { extra.visibility = if (extra.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        val config = DriverUi.card(this, extra, t("Область чтения и посадка", "Zona citită și îmbarcarea"), t("Не меняйте без необходимости. Эти параметры не исправляют адреса и не гарантируют цену.", "Modificați doar dacă este necesar. Acești parametri nu corectează adresele și nu garantează prețul."))
        numeric(config, "ocr_top", "Начало чтения, % от верха", "Începutul citirii, % de sus", "10% — верхняя десятая часть экрана исключена. Оставьте 10%, если карточка читается. Слишком большое значение отрежет цену и адреса. Само окно радара исключается автоматически.", "10% exclude zecimea de sus. Păstrați 10% dacă oferta se citește. O valoare prea mare taie prețul și adresele. Panoul propriu se exclude automat.", 10.0, 8.0, 80.0)
        numeric(config, "ocr_extra", "Запас на посадку, мин", "Timp de îmbarcare, min", "Необязательный запас между прибытием к клиенту и началом поездки. По умолчанию 0. Увеличивает общую занятость и уменьшает L/час, но не добавляется автоматически к цене.", "Timp opțional între sosire și plecare. Implicit 0. Crește timpul total și reduce L/oră, fără a mări automat prețul.", 0.0, 0.0, 30.0)
        flag(config, "ocr_screenshot_test", "Тест на сохранённом скриншоте", "Test pe captură salvată", "Только для галереи: подача берётся с картинки, маршрутизатор считает А→Б. Для реальных заказов выключите.", "Doar pentru galerie: preluarea se ia din imagine, se calculează A→B. Dezactivați pentru oferte reale.", false)
        config.addView(DriverUi.text(this, t("Исправления OCR обновляются разработчиком централизованно. Если адрес или цена не прочитаны, отправьте журнал — словарь вручную заполнять не нужно.", "Corecțiile OCR sunt gestionate central de dezvoltator. Dacă adresa sau prețul nu se citesc, trimiteți jurnalul; nu completați un dicționar manual."), 14f, true))
        root.addView(DriverUi.text(this, t("Тексты карточек и адреса записываются в диагностический журнал. Снимки не сохраняются. Отправляйте журнал только разработчику.", "Textele ofertelor și adresele intră în jurnalul de diagnostic. Imaginile nu se salvează. Trimiteți jurnalul doar dezvoltatorului."), 13f, true))
        DriverUi.button(this, root, t("Отправить журнал .txt", "Trimite jurnalul .txt")) { TripLog.share(this) }
        styleActions(root)
    }
    private fun styleActions(parent: android.view.ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is com.google.android.material.button.MaterialButton && !child.text.toString().startsWith("‹")) DriverUi.raisedAction(child)
            else if (child is android.view.ViewGroup) styleActions(child)
        }
    }
    private fun mapSettings(root: LinearLayout) {
        val mapOptions = DriverUi.card(this, root, t("Карта и показатели", "Hartă și indicatori"),
            t("Только для OCR. Поверх карты в строку: цена, красная сумма после комиссии, L/km и общее время. Если маршрут не найден, те же цифры остаются без карты. Карта требует интернет, ключ Google не нужен.",
              "Doar OCR. Pe hartă: preț, suma netă în roșu, L/km și durata totală. Fără traseu afișăm aceleași cifre fără hartă. Necesită internet, fără cheie Google."))
        DriverUi.toggle(this, mapOptions, t("Сразу показывать карту", "Arată harta automat"), t("Разворачивает карту при новом предложении. Крестик сразу сворачивает окно. Работает для чтения экрана OCR.", "Deschide harta la o ofertă nouă. Crucea ascunde imediat panoul. Pentru citirea OCR."), DriverPreferences.flag(this, "bolt_map_auto", true)) { DriverPreferences.set(this, "bolt_map_auto", it) }
        DriverUi.toggle(this, mapOptions, t("Светлая карта", "Hartă luminoasă"), t("По умолчанию светлая цветная карта. Изменение применяется при следующем открытии карты.", "Implicit harta este luminoasă, colorată. Se aplică la următoarea deschidere."), DriverPreferences.flag(this, "bolt_map_light", true)) { DriverPreferences.set(this, "bolt_map_light", it) }
        DriverUi.toggle(this, mapOptions, t("Только конечная точка", "Doar destinația"), t("Скрывает значки водителя и подачи. Линия всего маршрута сохраняется.", "Ascunde marcajele șoferului și preluării, păstrează traseul complet."), DriverPreferences.flag(this, "bolt_map_destination_only")) { DriverPreferences.set(this, "bolt_map_destination_only", it) }
        DriverUi.guide(this, mapOptions, t("Как читать карту", "Cum citești harta"), listOf(
            t("Синяя линия показывает рассчитанную дорогу: от водителя к клиенту, затем до конечной точки.", "Linia albastră arată drumul calculat: șofer → client → destinație."),
            t("Зелёная цифра — цена Bolt. Красная — сколько останется после комиссии и выбранных расходов. Ниже — доход за километр и общее время с подачей. Все показатели можно настроить выше.", "Verde: prețul Bolt. Roșu: suma netă după comision și costurile alese. Mai jos: lei/km și durata totală cu preluare. Indicatorii se configurează mai sus."),
            t("Нижняя подпись показывает сумму после комиссии. Крестик сразу сворачивает окно. Верхняя ручка перемещает окно, нижняя меняет высоту. Не размещайте окно поверх карточки заказа. Расчёт времени остаётся прогнозом.", "Textul de jos arată suma după comision. Crucea închide harta; «Ascunde» închide panoul simplu. Durata este o estimare.")))
    }
    private fun widgetSettings(root: LinearLayout) {
        val box = DriverUi.card(this, root, t("Вид виджета Bolt", "Aspectul widgetului Bolt"),
            t("Настройки отдельные от Яндекса. В режиме «Только Bolt» без заказа остаётся «+» для дорожных событий. При появлении заказа разворачивается цена, после исчезновения — снова «+». В OCR остаётся также значок управления чтением.",
              "Setări separate de Yandex. În modul «Doar Bolt», fără ofertă rămâne «+» pentru marcaje. La ofertă apare prețul; apoi revine «+». În OCR rămâne și pictograma de control a citirii."))
        fun slider(key: String, ru: String, ro: String, from: Int, to: Int, default: Int) {
            val label = DriverUi.text(this, "", 16f); box.addView(label)
            val current = DriverPreferences.number(this, key, default.toDouble()).toInt().coerceIn(from, to)
            label.text = t(ru, ro) + ": $current%"
            box.addView(SeekBar(this).apply {
                max = to - from; progress = current - from
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                        label.text = t(ru, ro) + ": ${value + from}%"
                        if (user) { DriverPreferences.set(this@ScreenReaderActivity, key, (value + from).toDouble()); FloatingWidgetService.applyWidgetScale() }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            })
        }
        slider("bolt_widget_size", "Размер виджета", "Mărimea widgetului", 75, 130, 100)
        slider("bolt_widget_alpha", "Непрозрачность фона", "Opacitatea fundalului", 30, 100, 92)
        slider("bolt_widget_price", "Размер цены", "Mărimea prețului", 75, 140, 100)
        DriverUi.toggle(this, box, t("Только «+» между заказами", "Doar «+» între oferte"),
            t("Для режима «Только Bolt». Кнопка открывает дорожные события; цена появляется автоматически при распознанном заказе.", "Pentru «Doar Bolt». Butonul deschide marcajele; prețul apare automat la ofertă."),
            DriverPreferences.flag(this, "bolt_plus_only", true)) { DriverPreferences.set(this, "bolt_plus_only", it); FloatingWidgetService.refreshSurge() }
    }
    private fun startCapture() {
        if (!LicenseManager(this).isLicensed()) { startActivity(Intent(this, SubscriptionActivity::class.java)); return }
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))); return }
        if (ScreenReaderService.running) { Toast.makeText(this, t("OCR уже работает", "OCR este deja activ"), Toast.LENGTH_SHORT).show(); return }
        if (!ScreenReaderLocation.permitted(this)) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 402)
            Toast.makeText(this, t("Разрешите геолокацию и нажмите запуск ещё раз", "Permiteți locația și porniți din nou"), Toast.LENGTH_LONG).show(); return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), 401)
    }
    @Deprecated("Android capture consent result")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != 401 || result != Activity.RESULT_OK || data == null) return
        if (!RadarServices.ocr(this)) return
        val service = Intent(this, ScreenReaderService::class.java).putExtra("result", result).putExtra("capture", data)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
        Toast.makeText(this, t("Откройте Bolt. Остановка — в уведомлении или долгим нажатием значка радара.", "Deschideți Bolt. Opriți din notificare sau prin apăsare lungă pe radar."), Toast.LENGTH_LONG).show()
    }
}
