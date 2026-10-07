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
    private var historyLimit = 30
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
    private fun render() { body.removeAllViews(); if (mode == "shift") shift() else settings() }
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
        val widget = card("Цена на виджете", "Prețul în widget", "Можно оставить только примерную цену. Дополнительные строки включаются отдельно.", "Puteți păstra doar prețul estimat. Rândurile suplimentare sunt opționale.", R.drawable.ic_visibility)
        flag(widget, "distance", "Километры", "Kilometri", "Длина поездки. Подача включается отдельно.", "Distanța cursei. Preluarea se activează separat.", true)
        flag(widget, "minutes", "Минуты", "Minute", "Прогноз времени поездки, не время ожидания пассажира.", "Durata estimată a cursei, fără așteptarea pasagerului.", true)
        flag(widget, "pickup", "Расстояние подачи", "Distanța până la pasager", "Расстояние до клиента, если оно прочитано с карточки.", "Distanța până la client, dacă apare în ofertă.")
        flag(widget, "stops", "Заезды", "Opriri", "Количество распознанных заездов.", "Numărul opririlor recunoscute.", true)
        flag(widget, "bonus", "Надбавка отдельной строкой", "Supliment pe un rând separat", "Надбавка и платная подача уже входят в цену. Эта строка только объясняет сумму.", "Suplimentul și preluarea plătită sunt deja incluse în preț.")
        flag(widget, "source", "Достоверность расчёта", "Sursa calculului", "Расчётная цена или расчёт по км и минутам Яндекса. Это не гарантия окончательной оплаты.", "Preț estimat sau calcul după km și minute Yandex. Nu garantează suma finală.", true)
        flag(widget, "range", "Показывать диапазон", "Afișează un interval", "Вместо ~100 L показываем, например, 90–110 L. Это выбранный вами запас, а не доказанная точность. Надбавка остаётся в сумме.", "În loc de ~100 L afișăm, de exemplu, 90–110 L. Marja este aleasă de dvs., nu o precizie garantată.")
        number(widget, "range_percent", "Запас диапазона, %", "Marja intervalului, %", 10.0, 1.0, 30.0)
        val demand = card("Спрос между заказами", "Cererea între curse", "Надбавка относится к выбранному тарифу и вашей точке. Ошибка связи не означает нулевой спрос.", "Suplimentul corespunde categoriei și poziției dvs. Lipsa conexiunii nu înseamnă cerere zero.", R.drawable.ic_location_on)
        flag(demand, "surge", "Показывать надбавку", "Afișează suplimentul", "Если выключить, между заказами будет надпись «Радар». Цена заказов продолжит появляться.", "Dacă dezactivați, între curse apare «Radar». Prețurile ofertelor rămân active.", true)
        flag(demand, "destination", "Надбавка в точке Б (магнитолы)", "Supliment la destinație (unități auto)", "На магнитолах показывает спрос здесь и у адреса Б. На обычном телефоне эта функция пока не включена.", "Pe unitățile auto arată cererea aici și la B. Funcția nu este activă pe telefoanele obișnuite.", true)
        number(demand, "refresh", "Обновлять каждые, секунд", "Actualizare la fiecare, secunde", 60.0, 30.0, 300.0)
        flag(demand, "surge_alert", "Сообщать о надбавке рядом", "Anunță suplimentul din apropiere", "Пока работает радар, проверяем вашу точку и 4 точки вокруг не чаще раза в 3 минуты. Это выборка, не вся карта. Уведомление — не чаще раза в 10 минут. Спрос может исчезнуть до вашего приезда.", "Cu radarul pornit verificăm poziția și 4 puncte din jur la 3 minute. Este un eșantion, nu întreaga hartă. Notificări cel mult la 10 minute. Cererea se poate schimba.")
        number(demand, "surge_threshold", "Надбавка от, L", "Supliment de la, L", 35.0, 1.0, 500.0)
        number(demand, "surge_radius", "Радиус проверки, км", "Raza verificării, km", 2.0, 0.5, 5.0)
        val profit = card("Выгодность заказа", "Rentabilitatea cursei", "Считаем прогноз после комиссии, энергии и обслуживания. Для прогноза за час добавляем примерное время подачи.", "Estimăm venitul după comision, energie și întreținere. Venitul pe oră include timpul estimat de preluare.", R.drawable.ic_payments)
        flag(profit, "profit", "Оценка выгодности на виджете", "Rentabilitate în widget", "Оценка сравнивается с вашей целью. Не принимает и не отклоняет заказы.", "Evaluarea se compară cu ținta dvs. Nu acceptă și nu refuză curse.")
        flag(profit, "per_km", "Чистыми за километр", "Net pe kilometru", "Делим прогноз чистого дохода на поездку плюс подачу.", "Împărțim venitul net estimat la distanța cursei și preluării.")
        flag(profit, "per_hour", "Прогноз чистыми за час", "Net estimat pe oră", "Делим доход на время поездки и ориентировочной подачи. Простой между заказами сюда не входит.", "Raportăm venitul la durata cursei și preluării. Așteptarea dintre curse nu este inclusă.")
        number(profit, "hour_target", "Моя цель, L/час", "Ținta mea, L/oră", 120.0, 1.0, 2000.0)
        number(profit, "pickup_speed", "Средняя скорость подачи, км/ч", "Viteza medie de preluare, km/h", 25.0, 5.0, 90.0)
        val costs = card("Моя машина и расходы", "Mașina și cheltuielile", "Эти значения нужны для прогноза. Расход можно указать в литрах или кВт·ч на 100 км. Настройки топлива и комиссии общие с карточкой «Чистыми» в профиле.", "Valorile sunt necesare pentru estimare. Consumul poate fi în litri sau kWh/100 km. Energia și comisionul sunt comune cu setările «Net» din profil.", R.drawable.ic_payments)
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
        flag(airport, "airport_alert", "Уведомления о прилётах", "Notificări despre aterizări", "Первый список запоминаем без уведомлений. Затем проверяем раз в 3 минуты. Можно выбрать номер рейса; пустое поле — все рейсы.", "Prima listă se memorează fără notificări. Apoi verificăm la 3 minute. Puteți alege un număr de zbor; gol înseamnă toate zborurile.")
        val flight = DriverUi.field(this, airport, t("Номер рейса (необязательно)", "Număr de zbor (opțional)"), DriverPreferences.text(this, "flight"), false)
        flight.doAfterTextChanged { DriverPreferences.set(this, "flight", it.toString().trim()) }
    }

    private fun shift() {
        body.addView(DriverUi.text(this, t("Моя смена", "Tura mea"), 28f))
        val summary = card("Результат смены", "Rezultatul turei", "В итогах только подтверждённые суммы. Записи с экрана Яндекса сначала нужно проверить. Общий пробег вводится по одометру; пустой пробег — разница с подтверждёнными поездками.", "Totalul include doar sumele confirmate. Verificați înregistrările citite din Yandex. Kilometrajul total se introduce de pe odometru; diferența față de curse este rulaj fără pasager.", R.drawable.ic_payments)
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
        summary.addView(DriverUi.text(this, t("Время смены: $elapsed мин", "Durata turei: $elapsed min")))
        if (DriverPreferences.costsReady(this) && confirmed.all { it.costsReady }) {
            summary.addView(DriverUi.text(this, t("После заданных расходов: ~${net.toInt()} L", "După cheltuielile setate: ~${net.toInt()} L")))
            if (elapsed > 0) summary.addView(DriverUi.text(this, t("В среднем за смену: ~${(net * 60 / elapsed).toInt()} L/час", "Media turei: ~${(net * 60 / elapsed).toInt()} L/oră")))
            val remaining = (DriverPreferences.number(this, "daily_target", 600.0) - net).coerceAtLeast(0.0).toInt()
            summary.addView(DriverUi.text(this, t("До цели: $remaining L", "Până la țintă: $remaining L")))
        } else summary.addView(DriverUi.text(this, t("Для чистого дохода заполните расходы машины. Старые записи сохраняют расчёт на момент подтверждения.", "Pentru venitul net completați cheltuielile. Înregistrările păstrează calculul din momentul confirmării."), 13f, true))
        val paidKm = confirmed.sumOf { it.km }
        val totalKm = DriverPreferences.number(this, "shift_km")
        summary.addView(DriverUi.text(this, t("С пассажиром: ${fmt(paidKm)} км", "Cu pasager: ${fmt(paidKm)} km")))
        if (totalKm >= paidKm && totalKm > 0) {
            summary.addView(DriverUi.text(this, t("Общий пробег: ${fmt(totalKm)} км · без пассажира: ${fmt(totalKm - paidKm)} км", "Total: ${fmt(totalKm)} km · fără pasager: ${fmt(totalKm - paidKm)} km")))
        }
        DriverUi.button(this, summary, if (DriverJournal.active(this).isEmpty()) t("Начать смену", "Începe tura") else t("Закончить смену", "Încheie tura")) {
            val starting = DriverJournal.active(this).isEmpty()
            MaterialAlertDialogBuilder(this).setTitle(if (starting) t("Начать новую смену?", "Începeți o tură nouă?") else t("Закончить смену?", "Încheiați tura?"))
                .setMessage(t("История поездок сохраняется на этом устройстве. Проверьте расходы перед началом смены.", "Istoricul curselor rămâne pe acest dispozitiv. Verificați cheltuielile înainte de tură."))
                .setPositiveButton(R.string.got_it) { _, _ -> if (starting) { DriverJournal.start(this); DriverPreferences.set(this, "shift_km", 0.0) } else DriverJournal.stop(this); render() }
                .setNegativeButton(R.string.cancel, null).show()
        }
        number(summary, "shift_km", "Общий пробег смены по одометру, км", "Kilometraj total al turei, km", 0.0, 0.0, 3000.0)
        DriverUi.button(this, summary, t("Обновить итоги", "Actualizează totalul")) { render() }
        DriverUi.button(this, summary, t("Добавить поездку / исправить цену", "Adaugă cursă / corectează prețul")) { editRide(null) }
        offer()
        val history = card("История и точность", "Istoric și precizie", "Нажмите на поездку, чтобы подтвердить или исправить сумму, либо удалить ошибочную запись. «Цена не совпала» — укажите фактическую оплату. История хранит последние 500 записей.", "Apăsați o cursă pentru a confirma/corecta suma sau a șterge înregistrarea. Pentru un preț diferit introduceți plata reală. Păstrăm ultimele 500 de înregistrări.", R.drawable.ic_payments)
        if (rides.isEmpty()) history.addView(DriverUi.text(this, t("Пока нет поездок. Добавьте первую вручную.", "Nu există curse. Adăugați prima manual."), 14f, true))
        rides.takeLast(historyLimit).reversed().forEach { ride ->
            val date = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ride.at))
            DriverUi.button(this, history, "$date · ${ride.price} L · ${if (ride.confirmed) t("подтверждено", "confirmat") else t("проверить", "verificați")}") { editRide(ride) }
            if (ride.confirmed && ride.estimate > 0) history.addView(DriverUi.text(this, t("Расчёт ${ride.estimate} L · разница ${ride.price - ride.estimate} L", "Estimare ${ride.estimate} L · diferență ${ride.price - ride.estimate} L"), 13f, true))
        }
        if (rides.size > historyLimit) DriverUi.button(this, history, t("Показать ещё 30", "Arată încă 30")) { historyLimit += 30; render() }
        val stats = card("Когда и где выгоднее", "Când și unde este mai rentabil", "Личная статистика по подтверждённым поездкам. Район задаётся вами. Показываем группы от 3 поездок. Доход за час здесь относится к времени поездок, а не всей смены; это история, не прогноз спроса.", "Statistici personale din curse confirmate. Zona este indicată de dvs. Afișăm grupuri de minimum 3 curse. Venitul pe oră se referă la timpul curselor, nu al turei; este istoric, nu prognoză de cerere.", R.drawable.ic_location_on)
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
        val box = card("Последний заказ", "Ultima ofertă", "Это предложение, а не заработанная сумма. Расходы на пустой возврат добавляются только к этому прогнозу.", "Este o ofertă, nu un venit încasat. Costul întoarcerii se adaugă doar acestei estimări.", R.drawable.ic_location_on)
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

    private fun editRide(ride: DriverJournal.Ride?) {
        val o = if (ride == null) OrderPreview.current() else null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(this@DriverToolsActivity, 20), 0, DriverUi.dp(this@DriverToolsActivity, 20), 0) }
        box.addView(DriverUi.text(this, t("Укажите фактически полученную сумму. Добавление подтверждает, что поездка состоялась.", "Introduceți suma primită efectiv. Salvarea confirmă că această cursă a avut loc."), 14f, true))
        val price = DriverUi.field(this, box, t("Фактическая оплата, L", "Plata reală, L"), (ride?.price ?: o?.price)?.toString().orEmpty())
        val km = DriverUi.field(this, box, t("Километры поездки", "Kilometrii cursei"), fmt(ride?.km ?: o?.km ?: 0.0))
        val pickup = DriverUi.field(this, box, t("Подача, км", "Preluare, km"), fmt(ride?.pickup ?: o?.pickup ?: 0.0))
        val minutes = DriverUi.field(this, box, t("Время поездки, мин", "Durata cursei, min"), (ride?.minutes ?: o?.minutes ?: 0).toString())
        val area = DriverUi.field(this, box, t("Район (необязательно)", "Zonă (opțional)"), ride?.area.orEmpty(), false)
        val builder = MaterialAlertDialogBuilder(this).setTitle(t("Подтвердить поездку", "Confirmă cursa")).setView(ScrollView(this).apply { addView(box) }).setPositiveButton(R.string.clients_save, null).setNegativeButton(R.string.cancel, null)
        if (ride != null) builder.setNeutralButton(t("Удалить", "Șterge")) { _, _ ->
            MaterialAlertDialogBuilder(this).setMessage(t("Удалить эту запись из истории?", "Ștergeți această înregistrare?"))
                .setPositiveButton(t("Удалить", "Șterge")) { _, _ -> DriverJournal.remove(this, ride.id); render() }.setNegativeButton(R.string.cancel, null).show()
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
            if (ride != null) DriverJournal.confirm(this, ride.id, amount, distance, pickupKm, duration, district) else DriverJournal.add(this, amount, o?.price ?: 0, distance, pickupKm, duration, district, true)
            dialog.dismiss(); render()
        } }
        dialog.show()
    }
    private fun fmt(n: Double) = String.format(Locale.getDefault(), "%.1f", n)
}
