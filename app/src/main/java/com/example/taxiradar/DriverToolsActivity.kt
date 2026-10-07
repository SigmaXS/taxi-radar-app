package com.example.taxiradar

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriverToolsActivity : AppCompatActivity() {
    private lateinit var body: LinearLayout
    private var mode = "settings"
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))
    private fun t(ru: String, ro: String) = DriverUi.t(this, ru, ro)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode") ?: "settings"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.tr_bg)) }
        val header = LinearLayout(this).apply { setPadding(DriverUi.dp(this@DriverToolsActivity, 18), DriverUi.dp(this@DriverToolsActivity, 12), DriverUi.dp(this@DriverToolsActivity, 18), 0) }
        DriverUi.button(this, header, t("‹  Назад", "‹  Înapoi")) { finish() }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tr_surface_high))
            setTextColor(getColor(R.color.tr_text))
        }
        root.addView(header)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(this@DriverToolsActivity, 18), DriverUi.dp(this@DriverToolsActivity, 12), DriverUi.dp(this@DriverToolsActivity, 18), DriverUi.dp(this@DriverToolsActivity, 24)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }
    private fun render() { body.removeAllViews(); when (mode) { "shift" -> shift(); "archive" -> archive(); else -> settings() }; DriverUi.arrangeCards(this, body) }

    // ---------- архив смен: календарь ----------

    private var archiveDay: Long = System.currentTimeMillis()

    // Файлы копии и таблицы: система спрашивает, куда сохранить / что открыть.
    private var pendingCsv: String = ""
    private val saveBackup = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeFile(uri, Backup.export(this), t("Копия сохранена. Перенесите файл на новый телефон и нажмите там «Восстановить».", "Copia e salvată. Mutați fișierul pe telefonul nou și apăsați «Restaurează»."))
    }
    private val saveCsv = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) writeFile(uri, pendingCsv, t("Таблица сохранена — откройте её в Excel или Google Таблицах.", "Tabelul e salvat — deschideți-l în Excel sau Google Sheets."))
    }
    private val openBackup = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = try { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
        val n = text?.let { Backup.restore(this, it) }
        Toast.makeText(this, if (n == null) t("Это не копия Taxi Radar", "Nu e o copie Taxi Radar") else t("Восстановлено. Поездок: $n", "Restaurat. Curse: $n"), Toast.LENGTH_LONG).show()
        render()
    }

    private fun writeFile(uri: android.net.Uri, text: String, done: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, done, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, t("Не удалось сохранить файл", "Fișierul nu a putut fi salvat"), Toast.LENGTH_LONG).show()
        }
    }

    private fun backupCard() {
        val box = card("Копия и выгрузка", "Copie și export", "Копия — все поездки, смены, расходы и настройки радара в одном файле: сохраните его в Telegram «Избранное» или на Google Диск и восстановите на новом телефоне. Подписка в копию не входит — она привязана к телефону, перенос делает администратор.\nТаблица — доходы за месяц для Excel.",
            "Copia — toate cursele, turele, cheltuielile și setările într-un fișier: salvați-l în Telegram sau Google Drive și restaurați pe telefonul nou. Abonamentul nu intră în copie.\nTabelul — veniturile pe lună pentru Excel.", R.drawable.ic_payments)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        DriverUi.button(this, box, t("Сохранить копию", "Salvează copia")) { saveBackup.launch("TaxiRadar-copy-$day.json") }
        DriverUi.button(this, box, t("Восстановить из копии", "Restaurează din copie")) {
            MaterialAlertDialogBuilder(this).setTitle(t("Восстановить из копии?", "Restaurați din copie?"))
                .setMessage(t("Поездки, смены и настройки на этом телефоне заменятся данными из файла.", "Cursele, turele și setările de pe acest telefon vor fi înlocuite cu cele din fișier."))
                .setPositiveButton(t("Выбрать файл", "Alege fișierul")) { _, _ -> openBackup.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
                .setNegativeButton(R.string.cancel, null).show()
        }
        DriverUi.button(this, box, t("Выгрузить месяц в таблицу", "Exportă luna în tabel")) {
            val months = Backup.months(this)
            if (months.isEmpty()) { Toast.makeText(this, t("Поездок пока нет", "Încă nu sunt curse"), Toast.LENGTH_SHORT).show(); return@button }
            val names = months.map { (y, m) -> SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(java.util.Calendar.getInstance().apply { clear(); set(y, m, 1) }.time) }
            MaterialAlertDialogBuilder(this).setTitle(t("Какой месяц?", "Ce lună?"))
                .setItems(names.toTypedArray()) { _, i ->
                    val (y, m) = months[i]
                    pendingCsv = Backup.monthCsv(this, y, m)
                    saveCsv.launch("TaxiRadar-%04d-%02d.csv".format(y, m + 1))
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun archive() {
        body.addView(DriverUi.text(this, t("Архив смен", "Arhiva turelor"), 28f))
        body.addView(DriverUi.text(this, t("Выберите день — покажем доход, расходы, пробег и часы. Все смены хранятся на этом телефоне.",
            "Alegeți ziua — arătăm venitul, cheltuielile, kilometrajul și orele. Turele rămân pe acest telefon."), 14f, true))
        val shifts = DriverJournal.shifts(this)
        val rides = DriverJournal.rides(this)
        val cal = card("Календарь", "Calendar", "Нажмите на день. Смена относится к дню, когда она началась.", "Apăsați pe o zi. Tura aparține zilei în care a început.", R.drawable.ic_payments)
        val view = android.widget.CalendarView(this).apply {
            date = archiveDay
            maxDate = System.currentTimeMillis()
            shifts.minOfOrNull { it.start }?.let { minDate = minOf(it, System.currentTimeMillis()) - 24 * 3600_000L }
        }
        cal.addView(view, LinearLayout.LayoutParams(-1, -2))
        val dayBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cal.addView(dayBox)
        fun showDay(dayStart: Long) {
            dayBox.removeAllViews()
            val dayEnd = dayStart + 24 * 3600_000L
            val that = shifts.filter { it.start in dayStart until dayEnd }
            val title = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(dayStart))
            dayBox.addView(DriverUi.text(this, title, 18f).apply { setTypeface(null, android.graphics.Typeface.BOLD) })
            if (that.isEmpty()) { dayBox.addView(DriverUi.text(this, t("В этот день смены не было.", "În această zi nu a fost tură."), 14f, true)); return }
            that.forEach { s -> shiftSummary(dayBox, DriverJournal.totals(this, s, rides)) }
        }
        val start = java.util.Calendar.getInstance().apply { timeInMillis = archiveDay; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
        showDay(start.timeInMillis)
        view.setOnDateChangeListener { _, y, m, d ->
            val day = java.util.Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis
            archiveDay = day
            showDay(day)
        }
        val recent = card("Последние смены", "Ultimele ture", "Новые сверху. Незакрытая смена отмечена «идёт сейчас».", "Cele noi sus. Tura deschisă e marcată «în curs».", R.drawable.ic_payments)
        if (shifts.isEmpty()) recent.addView(DriverUi.text(this, t("Смен пока нет — нажмите «Начать смену» или примите заказ.", "Încă nu sunt ture — apăsați «Începe tura» sau acceptați o comandă."), 14f, true))
        shifts.take(14).forEach { shiftSummary(recent, DriverJournal.totals(this, it, rides)) }
        backupCard()
    }

    /** Блок одной смены: когда, часы, оплата, чистыми и расходы, пробег, поездки. */
    private fun shiftSummary(parent: LinearLayout, s: DriverJournal.ShiftTotals) {
        val f = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val h = SimpleDateFormat("HH:mm", Locale.getDefault())
        val sh = s.shift
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(this@DriverToolsActivity, 12), DriverUi.dp(this@DriverToolsActivity, 10), DriverUi.dp(this@DriverToolsActivity, 12), DriverUi.dp(this@DriverToolsActivity, 10))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = DriverUi.dp(this@DriverToolsActivity, 14).toFloat(); setColor(getColor(R.color.tr_surface_high)) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = DriverUi.dp(this@DriverToolsActivity, 8) }
        }
        box.addView(DriverUi.text(this, f.format(Date(sh.start)) + " – " + (if (sh.end > 0) h.format(Date(sh.end)) else t("идёт сейчас", "în curs")) +
            " · " + t("${sh.minutes / 60} ч ${sh.minutes % 60} мин", "${sh.minutes / 60} h ${sh.minutes % 60} min"), 14f, true))
        box.addView(DriverUi.text(this, t("Оплата: ${s.gross} L · заказов: ${s.rides}", "Plata: ${s.gross} L · curse: ${s.rides}"), 18f))
        if (s.net != null) {
            box.addView(DriverUi.text(this, t("Чистыми: ~${s.net} L · расходы: ~${s.gross - s.net} L", "Net: ~${s.net} L · cheltuieli: ~${s.gross - s.net} L")))
            if (sh.minutes > 0) box.addView(DriverUi.text(this, t("~${s.net * 60 / sh.minutes} L/час смены", "~${s.net * 60 / sh.minutes} L/oră de tură") +
                (if (sh.pausedMin > 0 && sh.workMinutes > 0) t(" · ~${s.net * 60 / sh.workMinutes} L/час работы (перерывы ${sh.pausedMin} мин)", " · ~${s.net * 60 / sh.workMinutes} L/oră de lucru (pauze ${sh.pausedMin} min)") else ""), 14f, true))
        } else box.addView(DriverUi.text(this, t("Чистыми — заполните расходы машины в настройках радара", "Net — completați cheltuielile mașinii în setări"), 13f, true))
        box.addView(DriverUi.text(this, t("С пассажиром: ${fmt(s.paidKm)} км", "Cu pasager: ${fmt(s.paidKm)} km") +
            (if (sh.odometerKm > 0) t(" · всего: ${fmt(sh.odometerKm)} км", " · total: ${fmt(sh.odometerKm)} km") else "") +
            (if (sh.rent > 0) t(" · аренда ${sh.rent.toInt()} L", " · chirie ${sh.rent.toInt()} L") else ""), 14f, true))
        if (s.toCheck > 0) box.addView(DriverUi.text(this, t("Ждут проверки: ${s.toCheck} — откройте «Мои поездки»", "De verificat: ${s.toCheck} — deschideți «Cursele mele»"), 13f, true).apply { setTextColor(getColor(R.color.tr_warning)) })
        parent.addView(box)
    }
    private fun card(ru: String, ro: String, helpRu: String, helpRo: String, icon: Int = R.drawable.ic_help) = DriverUi.card(this, body, t(ru, ro), t(helpRu, helpRo), icon)
    private fun flag(box: LinearLayout, key: String, ru: String, ro: String, helpRu: String, helpRo: String, default: Boolean = false) {
        DriverUi.toggle(this, box, t(ru, ro), t(helpRu, helpRo), DriverPreferences.flag(this, key, default)) {
            DriverPreferences.set(this, key, it); FloatingWidgetService.refreshSurge()
        }
    }
    private fun number(box: LinearLayout, key: String, ru: String, ro: String, default: Double, min: Double, max: Double) {
        val field = DriverUi.field(this, box, t(ru, ro), fmt(DriverPreferences.number(this, key, default)))
        field.doAfterTextChanged {
            val n = it.toString().replace(',', '.').toDoubleOrNull()
            field.error = if (n == null || !n.isFinite() || n !in min..max) t("От $min до $max", "De la $min la $max") else null
            if (field.error == null) DriverPreferences.set(this, key, n!!)
        }
    }
    private fun settings() {
        body.addView(DriverUi.text(this, t("Ваш радар", "Radarul dvs."), 28f))
        body.addView(DriverUi.text(this, t("Оставьте только то, что помогает вам на смене. Настройки сохраняются сразу.", "Păstrați doar informațiile utile în tură. Setările se salvează imediat."), 14f, true))
        DriverUi.guide(this, body, t("Что здесь настраивается", "Ce se setează aici"), if (t("ru", "ro") == "ru") listOf(
            "«Цена на виджете» — какие строки показывать на карточке заказа: километры, минуты, подачу. Меньше строк — проще читать на ходу.",
            "«Спрос между заказами» — какой тариф смотреть для надбавки, когда заказа нет.",
            "«Моя машина и расходы» — расход, цена топлива, комиссия. Нужны, чтобы радар считал «чистыми» и выгодность заказа.",
            "«Выгодность заказа» — по желанию покажет на виджете, сколько останется чистыми, за км и за час.",
            "Остальное (дорога домой, аэропорт) включайте, только если пользуетесь. Всё сохраняется сразу, кнопки «Сохранить» нет."
        ) else listOf(
            "«Prețul în widget» — ce rânduri apar pe ofertă: kilometri, minute, preluare. Mai puține rânduri — mai ușor de citit în mers.",
            "«Cererea între curse» — ce categorie urmărim pentru supliment când nu aveți ofertă.",
            "«Mașina și cheltuielile» — consum, preț carburant, comision. Necesare ca radarul să calculeze «net» și rentabilitatea.",
            "«Rentabilitatea cursei» — opțional arată în widget cât rămâne net, pe km și pe oră.",
            "Restul (drumul spre casă, aeroport) activați doar dacă le folosiți. Totul se salvează imediat, fără buton «Salvează»."
        ))
        val widget = card("Цена на виджете", "Prețul în widget", "Можно оставить только примерную цену. Дополнительные строки включаются отдельно.", "Puteți păstra doar prețul estimat. Rândurile suplimentare sunt opționale.", R.drawable.ic_visibility)
        flag(widget, "distance", "Километры", "Kilometri", "Длина поездки. Подача включается отдельно.", "Distanța cursei. Preluarea se activează separat.", true)
        flag(widget, "minutes", "Минуты", "Minute", "Прогноз времени поездки, не время ожидания пассажира.", "Durata estimată a cursei, fără așteptarea pasagerului.", true)
        flag(widget, "pickup", "Расстояние подачи", "Distanța până la pasager", "Расстояние до клиента, если оно прочитано с карточки.", "Distanța până la client, dacă apare în ofertă.")
        flag(widget, "stops", "Заезды", "Opriri", "Количество распознанных заездов.", "Numărul opririlor recunoscute.", true)
        flag(widget, "bonus", "Надбавка отдельно", "Supliment separat", "Надбавка и платная подача уже входят в цену. Эта строка только объясняет сумму.", "Suplimentul și preluarea plătită sunt deja incluse în preț.")
        flag(widget, "source", "Источник расчёта", "Sursa calculului", "Расчётная цена или расчёт по км и минутам Яндекса. Это не гарантия окончательной оплаты.", "Preț estimat sau calcul după km și minute Yandex. Nu garantează suma finală.", true)
        flag(widget, "range", "Показывать диапазон", "Afișează un interval", "Вместо ~100 L показываем, например, 90–110 L. Это выбранный вами запас, а не доказанная точность. Надбавка остаётся в сумме.", "În loc de ~100 L afișăm, de exemplu, 90–110 L. Marja este aleasă de dvs., nu o precizie garantată.")
        number(widget, "range_percent", "Запас диапазона, %", "Marja intervalului, %", 10.0, 1.0, 30.0)
        val demand = card("Спрос между заказами", "Cererea între curse", "Надбавка относится к выбранному тарифу и вашей точке. Ошибка связи не означает нулевой спрос.", "Suplimentul corespunde categoriei și poziției dvs. Lipsa conexiunii nu înseamnă cerere zero.", R.drawable.ic_location_on)
        TariffSelector.add(this, demand)
        flag(demand, "surge", "Показывать надбавку", "Afișează suplimentul", "Если выключить, между заказами будет надпись «Радар». Цена заказов продолжит появляться.", "Dacă dezactivați, între curse apare «Radar». Prețurile ofertelor rămân active.", true)
        flag(demand, "destination", "Надбавка в точке Б", "Supliment la destinație", "Показывает надбавку здесь и у адреса Б на телефоне, планшете и магнитоле. Адрес читается с принятого заказа Яндекс Про. Если адрес ещё не распознан или не найден, вместо значения будет «Б ?».", "Arată suplimentul aici și la B pe telefon, tabletă și unitate auto. Adresa se citește din cursa acceptată în Yandex Pro. Dacă nu este recunoscută sau găsită, apare «B ?».", true)
        number(demand, "refresh", "Обновлять каждые, секунд", "Actualizare la fiecare, secunde", 60.0, 30.0, 300.0)
        flag(demand, "surge_alert", "Сообщать о надбавке рядом", "Anunță suplimentul din apropiere", "Пока работает радар, проверяем вашу точку и 4 точки вокруг не чаще раза в 3 минуты. Это выборка, не вся карта. Уведомление — не чаще раза в 10 минут. Спрос может исчезнуть до вашего приезда.", "Cu radarul pornit verificăm poziția și 4 puncte din jur la 3 minute. Este un eșantion, nu întreaga hartă. Notificări cel mult la 10 minute. Cererea se poate schimba.")
        number(demand, "surge_threshold", "Надбавка от, L", "Supliment de la, L", 35.0, 1.0, 500.0)
        number(demand, "surge_radius", "Радиус проверки, км", "Raza verificării, km", 2.0, 0.5, 5.0)
        val profit = card("Выгодность заказа", "Rentabilitatea cursei", "Чистыми = цена минус общая комиссия, энергия и обслуживание за километры поездки и подачи. Например: 100 L, комиссия 20%, поездка 10 км + подача 2 км, расход 8 л/100 км по 25 L: остаётся 56 L без обслуживания. За километр: 56 / 12 ≈ 4,7 L. Если поездка 20 минут, а подача 2 км при 25 км/ч — ещё 4,8 минуты, прогноз ≈ 135 L/час. Выгодность сравнивает его с вашей целью. Простой, чаевые, ожидание и аренда не входят в прогноз одного заказа; аренда вычитается в итогах смены. Это ориентир, а не обещание заработка. Сначала заполните «Моя машина и расходы».", "Net = preț minus comision, energie și întreținere pentru cursă și preluare. Exemplu: 100 L, comision 20%, cursă 10 km + preluare 2 km, consum 8 l/100 km la 25 L: rămân 56 L fără întreținere. Pe kilometru: 56 / 12 ≈ 4,7 L. Cursă 20 min plus preluare 4,8 min la 25 km/h: ≈ 135 L/oră. Evaluarea compară rezultatul cu ținta dvs. Așteptarea dintre curse, bacșișurile și chiria nu intră în estimarea unei curse; chiria se scade din totalul turei. Este orientativ. Completați întâi «Mașina și cheltuielile».", R.drawable.ic_payments)
        flag(profit, "profit", "Оценка выгодности на виджете", "Rentabilitate în widget", "Оценка сравнивается с вашей целью. Не принимает и не отклоняет заказы.", "Evaluarea se compară cu ținta dvs. Nu acceptă și nu refuză curse.")
        flag(profit, "per_km", "Чистыми за километр", "Net pe kilometru", "Делим прогноз чистого дохода на поездку плюс подачу.", "Împărțim venitul net estimat la distanța cursei și preluării.")
        flag(profit, "per_hour", "Прогноз чистыми за час", "Net estimat pe oră", "Делим доход на время поездки и ориентировочной подачи. Простой между заказами сюда не входит.", "Raportăm venitul la durata cursei și preluării. Așteptarea dintre curse nu este inclusă.")
        profit.addView(DriverUi.text(this, t("Мои желаемые показатели — заказ, который им не дотягивает, виджет отметит «ниже цели». 0 — не проверять.",
            "Indicatorii doriți — oferta sub ei e marcată «sub țintă». 0 — nu verifica."), 13f, true))
        number(profit, "hour_target", "Чистыми в час — от, L", "Net pe oră — de la, L", 120.0, 0.0, 2000.0)
        number(profit, "order_min_net", "Чистыми за заказ — от, L", "Net pe cursă — de la, L", 0.0, 0.0, 2000.0)
        number(profit, "pickup_max_km", "Подача — не дальше, км", "Preluare — maxim, km", 0.0, 0.0, 50.0)
        number(profit, "pickup_speed", "Средняя скорость подачи, км/ч", "Viteza medie de preluare, km/h", 25.0, 5.0, 90.0)
        val costs = card("Моя машина и расходы", "Mașina și cheltuielile", "Эти значения нужны для прогноза. Расход можно указать в литрах или кВт·ч на 100 км. Здесь находятся все настройки чистого дохода; второй карточки с теми же параметрами больше нет.", "Valorile sunt necesare pentru estimare. Consumul poate fi în litri sau kWh/100 km. Toate setările venitului net se află aici, fără un al doilea formular duplicat.", R.drawable.ic_payments)
        val types = arrayOf(t("Бензин", "Benzină"), t("Дизель", "Diesel"), t("Газ", "Gaz"), t("Электричество", "Electricitate"))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        spinner.setSelection(DriverPreferences.number(this, "energy_type").toInt().coerceIn(0, 3))
        costs.addView(spinner)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { DriverPreferences.set(this@DriverToolsActivity, "energy_type", position.toDouble()) }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        val s = NetEarnings.load(this)
        val consumption = DriverUi.field(this, costs, t("Расход на 100 км (л / кВт·ч)", "Consum la 100 km (l / kWh)"), fmt(s.consumptionPer100Km))
        val energy = DriverUi.field(this, costs, t("Цена литра / кВт·ч, L", "Preț litru / kWh, L"), fmt(s.fuelPrice))
        val commission = DriverUi.field(this, costs, t("Общая комиссия, %", "Comision total, %"), fmt(s.commissionPercent))
        listOf(consumption, energy, commission).forEach { field -> field.doAfterTextChanged {
            val values = listOf(consumption, energy, commission).map { it.text.toString().replace(',', '.').toDoubleOrNull() }
            val valid = values.all { it != null && it.isFinite() && it >= 0 } && (values[0] ?: 1001.0) <= 1000 && (values[1] ?: 10001.0) <= 10000 && (values[2] ?: 101.0) <= 100
            field.error = if (!valid) t("Проверьте значения (комиссия 0–100%)", "Verificați valorile (comision 0–100%)") else null
            if (valid) NetEarnings.save(this, NetEarnings.load(this).copy(consumptionPer100Km = values[0]!!, fuelPrice = values[1]!!, commissionPercent = values[2]!!))
        } }
        flag(costs, "net", "Показывать чистыми", "Afișează net", "После комиссии и переменных расходов, включая подачу. Аренда за смену вычитается только в итогах смены.", "După comision și costurile variabile, inclusiv preluarea. Chiria se scade doar în totalul turei.", NetEarnings.load(this).enabled)
        number(costs, "maintenance", "Обслуживание, L/км", "Întreținere, L/km", 0.0, 0.0, 50.0)
        number(costs, "rent", "Аренда и фиксированные расходы за смену, L", "Chirie și costuri fixe pe tură, L", 0.0, 0.0, 5000.0)
        number(costs, "daily_target", "Цель смены чистыми, L", "Ținta netă a turei, L", 600.0, 0.0, 20000.0)
        val home = card("Возврат и дорога домой", "Întoarcere și drumul spre casă", "Для последнего заказа можно посчитать пустой возврат. Сохранённый адрес помогает оценить направление, но не меняет выдачу Яндекс Про.", "Puteți calcula întoarcerea fără pasager pentru ultima ofertă. Adresa salvată ajută la evaluarea direcției, fără a schimba ofertele Yandex.", R.drawable.ic_location_on)
        flag(home, "home", "Подсказывать направление домой", "Indică direcția spre casă", "Сравниваем расстояние по прямой от начала и конца заказа до дома. Это подсказка по направлению, не дорожный маршрут.", "Comparăm distanța în linie dreaptă de la începutul și sfârșitul cursei până acasă. Este orientare, nu traseu rutier.")
        val address = DriverUi.field(this, home, t("Дом или точка возвращения", "Acasă sau punctul de întoarcere"), DriverPreferences.text(this, "home_address"), false)
        val status = DriverUi.text(this, if (DriverPreferences.text(this, "home_address").isEmpty()) t("Адрес ещё не сохранён", "Adresa nu este salvată") else DriverPreferences.text(this, "home_address"), 13f, true)
        home.addView(status)
        val saveHome = DriverUi.button(this, home, t("Найти и сохранить точку", "Găsește și salvează punctul")) { }
        saveHome.setOnClickListener {
            if (address.text.toString().trim().length < 4) { address.error = t("Введите полный адрес", "Introduceți adresa completă"); return@setOnClickListener }
            saveHome.isEnabled = false; status.text = getString(R.string.setup_checking)
            val q = address.text.toString().trim()
            lifecycleScope.launch {
                val point = withContext(Dispatchers.IO) { RouteFareCalculator.locate(this@DriverToolsActivity, q) }
                saveHome.isEnabled = true
                if (point == null) status.text = t("Адрес не найден. Уточните улицу, дом и населённый пункт.", "Adresa nu a fost găsită. Precizați strada, numărul și localitatea.")
                else { DriverPreferences.set(this@DriverToolsActivity, "home_address", q); DriverPreferences.set(this@DriverToolsActivity, "home_lat", point.first); DriverPreferences.set(this@DriverToolsActivity, "home_lon", point.second); status.text = t("Точка сохранена: ", "Punct salvat: ") + q }
            }
        }
        DriverUi.button(this, home, t("Удалить точку", "Șterge punctul")) { DriverPreferences.set(this, "home_address", ""); DriverPreferences.set(this, "home", false); address.setText(""); status.text = t("Точка удалена", "Punct șters") }
        val airport = card("Аэропорт", "Aeroport", "Сообщаем о новых статусах «приземлился», пока радар работает. Это табло рейсов, а не гарантия заказов.", "Anunțăm noile stări «aterizat» cu radarul pornit. Este orarul zborurilor, nu o garanție a curselor.", R.drawable.ic_flight)
        flag(airport, "share_queue", "Делиться очередью аэропорта", "Partajează coada aeroportului", "Когда вы в аэропорту открываете «Ожидание в очереди» в Яндекс Про, радар отправляет только цифры (тариф, место, ожидание) — без имени и номера. Так все видят загруженность очереди.", "Când deschideți «Așteptare în coadă» în Yandex Pro, radarul trimite doar cifrele (categorie, loc, așteptare) — fără nume. Așa toți văd cât de încărcată e coada.", true)
        flag(airport, "airport_alert", "Уведомления о прилётах", "Notificări despre aterizări", "Первый список запоминаем без уведомлений. Затем проверяем раз в 3 минуты. Можно выбрать номер рейса; пустое поле — все рейсы.", "Prima listă se memorează fără notificări. Apoi verificăm la 3 minute. Puteți alege un număr de zbor; gol înseamnă toate zborurile.")
        val flight = DriverUi.field(this, airport, t("Номер рейса (необязательно)", "Număr de zbor (opțional)"), DriverPreferences.text(this, "flight"), false)
        flight.doAfterTextChanged { DriverPreferences.set(this, "flight", it.toString().trim()) }
    }

    private fun shift() {
        body.addView(DriverUi.text(this, t("Моя смена", "Tura mea"), 28f))
        DriverUi.guide(this, body, t("Как это работает", "Cum funcționează"),
            if (t("ru", "ro") == "ru") listOf(
            "Перед первой сменой откройте «Настройки радара» → «Моя машина и расходы» и впишите расход на 100 км, цену топлива и комиссию. Без этого видна только сумма, без «чистыми».",
            "Смена начнётся сама, когда вы примете первый заказ (или нажмите «Начать смену» вручную).",
            "Работайте как обычно. Когда поездка заканчивается в Яндекс Про, радар сам записывает её в «Историю» с ценой с экрана и пометкой «проверить».",
            "Нажмите на поездку в «Истории», впишите сумму, которую реально получили, и сохраните. В итог попадают только подтверждённые поездки.",
            "Поездка не записалась (например, радар был выключен)? Нажмите «Добавить поездку» и впишите её вручную.",
            "В конце смены по желанию впишите общий пробег по одометру — так видно, сколько км вы проехали пустым. Нажмите «Закончить смену»."
        ) else listOf(
            "Înainte de prima tură deschideți «Setările radarului» → «Mașina și cheltuielile» și completați consumul la 100 km, prețul carburantului și comisionul. Fără ele vedeți doar suma, fără «net».",
            "Tura pornește singură la prima ofertă acceptată (sau apăsați «Începe tura» manual).",
            "Lucrați ca de obicei. Când cursa se termină în Yandex Pro, radarul o scrie singur în «Istoric» cu prețul de pe ecran și eticheta «verificați».",
            "Apăsați cursa în «Istoric», introduceți suma primită efectiv și salvați. Doar cursele confirmate intră în total.",
            "Cursa nu s-a înregistrat (de ex. radarul era oprit)? Apăsați «Adaugă cursă» și introduceți-o manual.",
            "La sfârșitul turei, opțional, introduceți kilometrajul total de pe odometru — vedeți câți km ați mers gol. Apăsați «Încheie tura»."
        ),
            t("«Подтверждённая оплата» — сколько вы получили. «После расходов» — минус комиссия, топливо, обслуживание и аренда. «L/час» — чистыми за час смены. «До цели» — сколько осталось до цели смены из настроек. Всё хранится только на этом телефоне.", "«Plata confirmată» — cât ați încasat. «După cheltuieli» — minus comision, carburant, întreținere și chirie. «L/oră» — net pe oră de tură. «Până la țintă» — cât rămâne până la ținta din setări. Totul rămâne doar pe acest telefon."))
        val summary = card("Результат смены", "Rezultatul turei", "Здесь итог вашей смены. Считаются только поездки, которые вы подтвердили в «Истории» (нажали на поездку и сохранили сумму). Поездки с пометкой «проверить» радар записал сам — проверьте их, и они добавятся.\n\nЧистыми = оплата минус комиссия, топливо, обслуживание и аренда из «Моя машина и расходы».\n\nПробег по одометру необязателен: если впишете, увидите, сколько км проехали без пассажира.", "Totalul include doar sumele confirmate. Verificați înregistrările citite din Yandex. Kilometrajul total se introduce de pe odometru; diferența față de curse este rulaj fără pasager.", R.drawable.ic_payments)
        val rides = DriverJournal.rides(this)
        val current = rides.filter { it.shift == DriverJournal.selected(this) && it.shift.isNotEmpty() }
        val confirmed = current.filter { it.confirmed }
        val gross = confirmed.sumOf { it.price }
        val totalTravel = DriverPreferences.number(this, "shift_km")
        val net = confirmed.sumOf { it.net } - DriverJournal.rent(this) - (totalTravel - confirmed.sumOf { it.km + it.pickup }).coerceAtLeast(0.0) * DriverPreferences.costs(this).perKm
        val elapsed = DriverJournal.elapsedMinutes(this)
        summary.addView(DriverUi.text(this, t("Подтверждённая оплата", "Plata confirmată"), 14f, true))
        summary.addView(DriverUi.text(this, "$gross L", 30f))
        summary.addView(DriverUi.text(this, t("Заказов: ${confirmed.size} · ждут проверки: ${current.count { !it.confirmed }}", "Curse: ${confirmed.size} · de verificat: ${current.count { !it.confirmed }}")))
        val pausedMin = DriverJournal.pausedMinutes(this)
        val workMin = (elapsed - pausedMin).coerceAtLeast(0)
        summary.addView(DriverUi.text(this, t("Время смены: ${elapsed / 60} ч ${elapsed % 60} мин", "Durata turei: ${elapsed / 60} h ${elapsed % 60} min") +
            (if (pausedMin > 0) t(" · перерывы ${pausedMin} мин", " · pauze ${pausedMin} min") else "") +
            (if (DriverJournal.paused(this)) t(" · сейчас перерыв", " · acum pauză") else "")))
        if (DriverPreferences.costsReady(this) && confirmed.all { it.costsReady }) {
            summary.addView(DriverUi.text(this, t("После заданных расходов: ~${net.toInt()} L", "După cheltuielile setate: ~${net.toInt()} L")))
            if (elapsed > 0) {
                summary.addView(DriverUi.text(this, t("За всю смену: ~${(net * 60 / elapsed).toInt()} L/час", "Pe toată tura: ~${(net * 60 / elapsed).toInt()} L/oră")))
                if (pausedMin > 0 && workMin > 0) {
                    summary.addView(DriverUi.text(this, t("За рабочее время: ~${(net * 60 / workMin).toInt()} L/час", "Pe timpul de lucru: ~${(net * 60 / workMin).toInt()} L/oră")))
                    summary.addView(DriverUi.text(this, t("Первое — доход за весь день вместе с перерывами. Второе — сколько вы зарабатываете, пока реально работаете: так честнее сравнивать смены.",
                        "Primul — venitul pe toată ziua cu pauze. Al doilea — cât câștigați cât lucrați efectiv: așa comparați corect turele."), 13f, true))
                }
            }
            val remaining = (DriverPreferences.number(this, "daily_target", 600.0) - net).coerceAtLeast(0.0).toInt()
            summary.addView(DriverUi.text(this, t("До цели: $remaining L", "Până la țintă: $remaining L")))
        } else summary.addView(DriverUi.text(this, t("Для чистого дохода заполните расходы машины. Старые записи сохраняют расчёт на момент подтверждения.", "Pentru venitul net completați cheltuielile. Înregistrările păstrează calculul din momentul confirmării."), 13f, true))
        val paidKm = confirmed.sumOf { it.km }
        val totalKm = DriverPreferences.number(this, "shift_km")
        summary.addView(DriverUi.text(this, t("С пассажиром: ${fmt(paidKm)} км", "Cu pasager: ${fmt(paidKm)} km")))
        if (totalKm >= paidKm && totalKm > 0) {
            summary.addView(DriverUi.text(this, t("Общий пробег: ${fmt(totalKm)} км · без пассажира: ${fmt(totalKm - paidKm)} км", "Total: ${fmt(totalKm)} km · fără pasager: ${fmt(totalKm - paidKm)} km")))
        }
        if (DriverJournal.active(this).isNotEmpty()) DriverUi.button(this, summary, if (DriverJournal.paused(this)) t("▶ Продолжить работу", "▶ Continuă lucrul") else t("⏸ Перерыв", "⏸ Pauză")) {
            if (DriverJournal.paused(this)) DriverJournal.resume(this) else DriverJournal.pause(this)
            render()
        }
        DriverUi.button(this, summary, if (DriverJournal.active(this).isEmpty()) t("Начать смену", "Începe tura") else t("Закончить смену", "Încheie tura")) {
            val starting = DriverJournal.active(this).isEmpty()
            MaterialAlertDialogBuilder(this).setTitle(if (starting) t("Начать новую смену?", "Începeți o tură nouă?") else t("Закончить смену?", "Încheiați tura?"))
                .setMessage(t("История поездок сохраняется на этом устройстве. Проверьте расходы перед началом смены.", "Istoricul curselor rămâne pe acest dispozitiv. Verificați cheltuielile înainte de tură."))
                .setPositiveButton(R.string.got_it) { _, _ -> if (starting) { DriverJournal.start(this); DriverPreferences.set(this, "shift_km", 0.0) } else DriverJournal.stop(this); render() }
                .setNegativeButton(R.string.cancel, null).show()
        }
        DriverUi.toggle(this, summary, t("Начинать смену автоматически", "Pornește tura automat"),
            t("Смена откроется сама, когда вы примете первый заказ. Если забыли закрыть смену вчера, она закроется сама и начнётся новая.",
                "Tura pornește singură la prima ofertă acceptată. Dacă ați uitat să închideți tura de ieri, se închide singură și începe una nouă."),
            DriverPreferences.flag(this, "auto_shift", true)) { DriverPreferences.set(this, "auto_shift", it) }
        number(summary, "shift_km", "Общий пробег смены по одометру, км", "Kilometraj total al turei, km", 0.0, 0.0, 3000.0)
        DriverUi.button(this, summary, t("Обновить итоги", "Actualizează totalul")) { render() }
        DriverUi.button(this, summary, t("Добавить поездку / исправить цену", "Adaugă cursă / corectează prețul")) { RideEditor.show(this, null) { render() } }
        DriverUi.button(this, summary, t("Архив смен и календарь", "Arhiva turelor și calendar")) {
            startActivity(android.content.Intent(this, DriverToolsActivity::class.java).putExtra("mode", "archive"))
        }
        DriverUi.button(this, summary, t("Отправить итог смены", "Trimite totalul turei")) {
            // Обычный «Поделиться»: водитель сам выбирает Telegram, WhatsApp, Viber или заметки.
            val lines = mutableListOf(
                t("Taxi Radar · итог смены", "Taxi Radar · totalul turei") + " " + SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
                t("Время: ${elapsed / 60} ч ${elapsed % 60} мин", "Durata: ${elapsed / 60} h ${elapsed % 60} min"),
                t("Заказов: ${confirmed.size}", "Curse: ${confirmed.size}"),
                t("Оплата: $gross L", "Plata: $gross L")
            )
            if (DriverPreferences.costsReady(this) && confirmed.all { it.costsReady }) {
                lines += t("Чистыми: ~${net.toInt()} L", "Net: ~${net.toInt()} L")
                if (elapsed > 0) lines += t("В час: ~${(net * 60 / elapsed).toInt()} L", "Pe oră: ~${(net * 60 / elapsed).toInt()} L")
            }
            lines += t("С пассажиром: ${fmt(paidKm)} км", "Cu pasager: ${fmt(paidKm)} km")
            if (totalKm >= paidKm && totalKm > 0) lines += t("Общий пробег: ${fmt(totalKm)} км", "Kilometraj total: ${fmt(totalKm)} km")
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, lines.joinToString("\n"))
            startActivity(android.content.Intent.createChooser(send, t("Отправить итог смены", "Trimite totalul turei")))
        }
        offer()
        // История одна — в «Моих поездках»: там и расчёт, и итог Яндекса, и подтверждение суммы.
        val history = card("Мои поездки", "Cursele mele", "Все поездки в одном месте: маршрут, расчёт радара, цена Яндекса, сколько вы получили, расходы и почему цена отличалась. Поездки с пометкой «проверить» радар записал сам — подтвердите сумму, и они попадут в итог смены.", "Toate cursele într-un loc: traseul, estimarea radarului, prețul Yandex, cât ați încasat, cheltuielile și de ce a diferit prețul. Cursele «de verificat» le-a scris radarul — confirmați suma și intră în total.", R.drawable.ic_payments)
        val toCheck = rides.count { !it.confirmed }
        history.addView(DriverUi.text(this, if (toCheck > 0) t("Ждут проверки: $toCheck", "De verificat: $toCheck") else t("Все поездки проверены", "Toate cursele sunt verificate"), 15f, toCheck == 0))
        DriverUi.button(this, history, t("Открыть мои поездки", "Deschide cursele mele")) {
            startActivity(android.content.Intent(this, MyTripsActivity::class.java))
        }
        val stats = card("Когда и где выгоднее", "Când și unde este mai rentabil", "Ваша личная статистика: в какие часы и в каких районах у вас была лучшая оплата. Район вы пишете сами при подтверждении поездки (например, «Ботаника»). Строка появляется, когда набирается хотя бы 3 подтверждённые поездки. Это ваша история, а не прогноз спроса.", "Statistici personale din curse confirmate. Zona este indicată de dvs. Afișăm grupuri de minimum 3 curse. Venitul pe oră se referă la timpul curselor, nu al turei; este istoric, nu prognoză de cerere.", R.drawable.ic_location_on)
        val all = rides.filter { it.confirmed }
        val groups = all.filter { it.area.isNotBlank() }.groupBy { it.area.trim().lowercase() }
        val hours = all.groupBy { java.util.Calendar.getInstance().apply { timeInMillis = it.at }.get(java.util.Calendar.HOUR_OF_DAY) }
        fun stat(label: String, list: List<DriverJournal.Ride>) {
            if (list.size < 3) return
            val minutes = list.sumOf { it.minutes }
            stats.addView(DriverUi.text(this, "$label · ${list.size} ${t("поездок", "curse")} · ${t("средняя оплата", "plata medie")} ${list.sumOf { it.price } / list.size} L${if (list.all { it.costsReady } && minutes > 0) " · ~${list.sumOf { it.net } * 60 / minutes} L/${t("час поездок", "oră de curse")}" else ""}", 14f))
        }
        groups.toSortedMap().forEach { (label, list) -> stat(label, list) }
        hours.toSortedMap().forEach { (hour, list) -> stat("$hour:00–${hour + 1}:00", list) }
        if (groups.values.none { it.size >= 3 } && hours.values.none { it.size >= 3 }) stats.addView(DriverUi.text(this, t("Нужно минимум 3 подтверждённые поездки в районе или в одном часе.", "Sunt necesare minimum 3 curse confirmate într-o zonă sau la aceeași oră."), 14f, true))
    }

    private fun offer() {
        val o = OrderPreview.current() ?: return
        val box = card("Последний заказ", "Ultima ofertă", "Последний заказ, который вы видели в Яндекс Про. Впишите, сколько км придётся ехать пустым обратно, — радар покажет, сколько останется чистыми и сколько это в час. Это прогноз, а не заработанные деньги.", "Este o ofertă, nu un venit încasat. Costul întoarcerii se adaugă doar acestei estimări.", R.drawable.ic_location_on)
        box.addView(DriverUi.text(this, "~${o.price} L · ${fmt(o.km)} ${t("км", "km")} · ${o.minutes} ${t("мин", "min")}", 22f))
        box.addView(DriverUi.text(this, o.route.joinToString(" → "), 14f, true))
        val back = DriverUi.field(this, box, t("Пустой возврат, км", "Întoarcere fără pasager, km"), "0")
        box.addView(DriverUi.text(this, t("Для времени возврата используем условную скорость 30 км/ч. Реальные пробки и ожидание могут изменить результат.", "Pentru întoarcere folosim o viteză estimată de 30 km/h. Traficul și așteptarea pot modifica rezultatul."), 13f, true))
        val answer = DriverUi.text(this, "", 16f); box.addView(answer)
        val calculate = {
            val returnKm = back.text.toString().replace(',', '.').toDoubleOrNull()
            if (returnKm == null || returnKm !in 0.0..1000.0) back.error = t("От 0 до 1000 км", "De la 0 la 1000 km")
            else {
                back.error = null
                if (!DriverPreferences.costsReady(this)) answer.text = t("Сначала заполните расходы машины в настройках радара.", "Completați întâi cheltuielile mașinii în setările radarului.")
                else {
                    val r = OrderEconomics.calculate(o.price, o.km, o.pickup, o.minutes, o.pickup / DriverPreferences.number(this, "pickup_speed", 25.0) * 60, DriverPreferences.costs(this), returnKm)
                    answer.text = t("После расходов и возврата: ~${r.net} L\nПрогноз: ${r.perHour ?: 0} L/час", "După costuri și întoarcere: ~${r.net} L\nEstimare: ${r.perHour ?: 0} L/oră")
                }
            }
        }
        back.doAfterTextChanged { calculate() }; calculate()
        if (DriverPreferences.flag(this, "home") && DriverPreferences.text(this, "home_address").isNotEmpty()) {
            val btn = DriverUi.button(this, box, t("Заказ в сторону дома?", "Oferta merge spre casă?")) { }
            btn.setOnClickListener {
                btn.isEnabled = false
                lifecycleScope.launch {
                    val points = withContext(Dispatchers.IO) { o.route.firstOrNull()?.let { RouteFareCalculator.locate(this@DriverToolsActivity, it) } to o.route.lastOrNull()?.let { RouteFareCalculator.locate(this@DriverToolsActivity, it) } }
                    btn.isEnabled = true
                    val a = points.first; val b = points.second
                    if (a == null || b == null) answer.text = t("Не удалось найти адреса. Проверьте связь.", "Adresele nu au fost găsite. Verificați conexiunea.")
                    else {
                        val lat = DriverPreferences.number(this@DriverToolsActivity, "home_lat"); val lon = DriverPreferences.number(this@DriverToolsActivity, "home_lon")
                        val da = RouteFareCalculator.distanceKm(a.first, a.second, lat, lon); val db = RouteFareCalculator.distanceKm(b.first, b.second, lat, lon)
                        answer.text = (if (db < da - 0.5) t("Приближает к дому", "Vă apropie de casă") else t("Не приближает к дому", "Nu vă apropie de casă")) + t("\nПосле высадки ~${fmt(db)} км по прямой до дома.", "\nDupă cursă ~${fmt(db)} km în linie dreaptă până acasă.")
                    }
                }
            }
        }
    }

    private fun fmt(n: Double) = String.format(Locale.getDefault(), "%.1f", n)
}
