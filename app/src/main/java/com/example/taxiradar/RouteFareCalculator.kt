package com.example.taxiradar

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Цена поездки А→Б — минимальная стоимость (включает первые 2 км), затем
 * ставка за км (своя по городу и за городом) и ставка за минуту в пути.
 * Граница города — не круг и не радиусы вокруг сёл (так было раньше и
 * ошибалось для адресов у границы, например район Рышкановка попадал в
 * радиус соседнего села), а полигон cityZonePolygon, оцифрованный по карте
 * зоны из самого приложения Яндекс.Такси (водитель прислал скриншот) и
 * уточнённый по реальной точке пересечения границы на маршруте до аэропорта.
 *
 * Ставки — СТРОГО официальная тарифная сетка Яндекса (не подгонка): 3.5 L/км
 * по городу у всех тарифов, за городом 5.3 (Эконом) / 7.3 (Комфорт,
 * Комфорт+), 1 L/мин, первые 2 км включены в минимальную стоимость. Подгонки
 * не потребовалось — расхождения с реальными ценами объяснились неточной
 * границей город/загород, а не ставками.
 *
 * Граница зоны пересчитана по 5 реальным маршрутам: для каждого известна
 * настоящая цена (частично — по прямому скриншоту границы, частично — по
 * скриншотам пассажирского приложения), из неё вычислена ТОЧНАЯ точка на
 * маршруте, где официальная ставка даёт точное совпадение, и через эти точки
 * построен полигон (вместо оцифровки скриншота на глаз):
 *  - Чеукарь 6 (11.55 км): 100 L, граница в 11.03 км от А (у самого Б)
 *  - Аэропорт (7.63 км): 93 L, граница в 3.07 км от А
 *  - Сынжера (11.98 км): 114.7 L, граница в 7.28 км от А
 *  - Будешты (21.04 км): 170.7 L, граница в 13.46 км от А
 *  - Колоница (15.97 км): 130.5 L, граница в 12.89 км от А
 * Все 5 сходятся в пределах 2-2.5 L, КРОМЕ Сынжеры (~15 L) — дорога туда и на
 * аэропорт идёт одной трассой первые 3 км, потом расходится, и похоже зона
 * не односвязная (после развилки дорога на Сынжеру, видимо, снова заходит в
 * зону), один простой полигон это не описывает точно. Если наберётся ещё
 * реальная цена в этом направлении (например, другое село по той же дороге
 * дальше Сынжеры) — можно будет уточнить и это.
 */
object RouteFareCalculator {

    // Публичный OSRM demo-сервер — маршрутизация без привязки к своей инфраструктуре.
    private const val OSRM_SERVER_URL = "https://router.project-osrm.org"

    // Публичный Nominatim — 1 запрос/сек достаточно для одного водителя.
    private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org"
    private const val NOMINATIM_USER_AGENT = "TaxiRadarApp/1.0 (single-driver personal use)"
    private const val YANDEX_GEOCODER_URL = "https://geocode-maps.yandex.ru/1.x/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    data class Tariff(val baseFare: Double, val perKmCity: Double, val perKmOutOfCity: Double, val perMinute: Double)

    private val tariffs = mapOf(
        "эконом" to Tariff(baseFare = 30.0, perKmCity = 3.5, perKmOutOfCity = 5.3, perMinute = 1.0),
        "комфорт" to Tariff(baseFare = 45.0, perKmCity = 3.5, perKmOutOfCity = 7.3, perMinute = 1.0),
        "комфорт+" to Tariff(baseFare = 65.0, perKmCity = 3.5, perKmOutOfCity = 7.3, perMinute = 1.0)
    )

    /** Первые 2 км поездки включены в минимальную стоимость (по городской
     *  ставке в первую очередь, остаток — по загородной). */
    private const val FREE_KM = 2.0

    /**
     * Граница городской тарифной зоны — построена по 5 точным точкам
     * пересечения на реальных маршрутах (см. комментарий класса выше);
     * западная/северо-западная часть не проверена реальными поездками —
     * оставлена как в первой оцифровке со скриншота зоны, поправь при
     * появлении реальных маршрутов в ту сторону.
     */
    private val cityZonePolygon = listOf(
        28.83774 to 47.08549,
        28.80156 to 47.07999,
        28.76378 to 47.07457,
        28.76370 to 47.05835,
        28.73516 to 47.03933,
        28.74046 to 47.01677,
        28.76997 to 46.99111,
        28.80372 to 46.97135,
        28.89244 to 46.96319,
        28.93342 to 46.93772,
        28.93020 to 47.04983,
        28.92294 to 47.04881,
        28.84655 to 47.06034
    )

    /** Точка внутри полигона городской зоны — стандартный ray casting. */
    private fun isInsideCity(lat: Double, lon: Double): Boolean {
        var inside = false
        var j = cityZonePolygon.size - 1
        for (i in cityZonePolygon.indices) {
            val (xi, yi) = cityZonePolygon[i]
            val (xj, yj) = cityZonePolygon[j]
            if ((yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    // Условный центр Кишинёва — используется только чтобы среди нескольких
    // кандидатов геокодирования выбрать ближайший к городу (не для тарифов).
    private const val CITY_CENTER_LAT = 47.0105
    private const val CITY_CENTER_LON = 28.8638

    data class FareResult(
        val price: Int,
        val distanceKm: Double,
        /** Минуты с поправкой на пробки — по ним считалась цена. */
        val durationMin: Int,
        /** Минуты OSRM по пустым дорогам — для обучения поправки (TrafficModel). */
        val osrmMin: Double,
        /** Разбивка маршрута — чтобы пересчитать цену по данным навигатора Яндекса. */
        val cityKm: Double,
        val outOfCityKm: Double
    )

    /**
     * Считает цену поездки СТРОГО по маршруту addrFrom → addrTo: реальная
     * геометрия маршрута от OSRM делится на городские/загородные отрезки по
     * cityZonePolygon, каждая часть — по своей ставке. Если геокодирование
     * или OSRM не сработали — возвращает null, и вызывающий код просто не
     * показывает цену, а не гадает по случайной формуле.
     *
     * addresses — точки по порядку: А, заезды (если есть), Б. Маршрут строится
     * через все точки, и тариф применяется к полному пробегу и времени.
     */
    suspend fun calculate(
        addresses: List<String>,
        tariffName: String,
        /** Свой ключ водителя; пустой — только общий геокодер сервера. */
        yandexApiKey: String,
        surgeBonus: Int = 0,
        /** Во сколько раз поездка дольше, чем по пустым дорогам (TrafficModel). */
        timeFactor: Double = 1.0,
        /** Для общего геокодера на сервере; null — только свой ключ. */
        context: Context? = null
    ): FareResult? = withContext(Dispatchers.IO) {
        Log.d("FARE_CALC", "calculate() запущен: $addresses")
        if (addresses.size < 2) return@withContext null
        try {
            // Геокодируем все адреса ПАРАЛЛЕЛЬНО — время не складывается.
            val points = coroutineScope {
                addresses.map { address ->
                    async { withTimeoutOrNull(8000) { geocode(address, yandexApiKey, context) } }
                }.awaitAll()
            }

            // Если не нашлась хоть одна точка — цена будет неверной, лучше не показывать.
            points.forEachIndexed { i, p ->
                if (p == null) {
                    Log.e("FARE_CALC", "Геокодирование не удалось: \"${addresses[i]}\"")
                    return@withContext null
                }
                Log.d("FARE_CALC", "Точка ${i + 1}: ${addresses[i]} -> ${p.lat},${p.lon}")
            }

            val route = getRouteWithZoneSplit(points.filterNotNull())
            if (route == null) {
                Log.e("FARE_CALC", "OSRM не вернул маршрут (сервер недоступен или code != Ok)")
                return@withContext null
            }

            // Тариф тот же — уточняем только минуты: OSRM не знает про пробки.
            val minutes = route.durationMin * timeFactor
            val finalPrice = price(tariffName, route.cityKm, route.outOfCityKm, minutes, surgeBonus)

            Log.d(
                "FARE_CALC",
                "Итог: город=${route.cityKm}км загород=${route.outOfCityKm}км " +
                        "время=${route.durationMin}мин ×$timeFactor=${minutes}мин цена=$finalPrice"
            )

            FareResult(
                price = finalPrice,
                distanceKm = Math.round((route.cityKm + route.outOfCityKm) * 10) / 10.0,
                durationMin = Math.round(minutes).toInt(),
                osrmMin = route.durationMin,
                cityKm = route.cityKm,
                outOfCityKm = route.outOfCityKm
            )
        } catch (e: Exception) {
            Log.e("FARE_CALC", "Исключение при расчёте цены: ${e.message}", e)
            null
        }
    }

    /**
     * Цена по официальному тарифу: минималка (в неё входят первые FREE_KM км)
     * + км по городу и за городом + минуты + надбавка. Первые бесплатные км
     * списываем сначала с городской части, остаток — с загородной.
     */
    fun price(tariffName: String, cityKm: Double, outOfCityKm: Double, minutes: Double, surgeBonus: Int): Int {
        val tariff = tariffs[tariffName.lowercase()] ?: tariffs["эконом"]!!
        val freeKmUsedInCity = minOf(FREE_KM, cityKm)
        val billableCityKm = cityKm - freeKmUsedInCity
        val freeKmRemaining = FREE_KM - freeKmUsedInCity
        val billableOutOfCityKm = maxOf(0.0, outOfCityKm - freeKmRemaining)
        val rawPrice = tariff.baseFare +
                tariff.perKmCity * billableCityKm +
                tariff.perKmOutOfCity * billableOutOfCityKm +
                tariff.perMinute * minutes
        return Math.round(rawPrice).toInt() + surgeBonus
    }

    private data class LatLng(val lat: Double, val lon: Double)
    private data class RouteZoneSplit(val cityKm: Double, val outOfCityKm: Double, val durationMin: Double)

    /**
     * Маршрут с полной геометрией — идём по точкам последовательно, каждый
     * отрезок между соседними точками относим к городу или загороду по
     * середине отрезка (через cityZonePolygon).
     */
    private fun getRouteWithZoneSplit(points: List<LatLng>): RouteZoneSplit? {
        val from = points.first()
        val to = points.last()
        val url = "$OSRM_SERVER_URL/route/v1/driving/" +
                points.joinToString(";") { "${it.lon},${it.lat}" } +
                "?overview=full&geometries=geojson"

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            if (json.getString("code") != "Ok") return null

            val route = json.getJSONArray("routes").getJSONObject(0)
            val durationMin = route.getDouble("duration") / 60.0
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")

            if (coords.length() < 2) {
                val distanceKm = route.getDouble("distance") / 1000.0
                val midLat = (from.lat + to.lat) / 2.0
                val midLon = (from.lon + to.lon) / 2.0
                return if (isInsideCity(midLat, midLon)) {
                    RouteZoneSplit(cityKm = distanceKm, outOfCityKm = 0.0, durationMin = durationMin)
                } else {
                    RouteZoneSplit(cityKm = 0.0, outOfCityKm = distanceKm, durationMin = durationMin)
                }
            }

            var cityKm = 0.0
            var outOfCityKm = 0.0

            var prevLon = coords.getJSONArray(0).getDouble(0)
            var prevLat = coords.getJSONArray(0).getDouble(1)

            for (i in 1 until coords.length()) {
                val lon = coords.getJSONArray(i).getDouble(0)
                val lat = coords.getJSONArray(i).getDouble(1)

                val segmentKm = haversineKm(prevLat, prevLon, lat, lon)
                val midLat = (prevLat + lat) / 2.0
                val midLon = (prevLon + lon) / 2.0

                if (isInsideCity(midLat, midLon)) {
                    cityKm += segmentKm
                } else {
                    outOfCityKm += segmentKm
                }

                prevLat = lat
                prevLon = lon
            }

            return RouteZoneSplit(cityKm = cityKm, outOfCityKm = outOfCityKm, durationMin = durationMin)
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadiusKm * c
    }

    /**
     * Геокодирование через публичный Nominatim (OpenStreetMap).
     */
    // OpenStreetMap/Nominatim знает пригороды Кишинёва под их официальными
    // румынскими названиями, а не кириллической транслитерацией — если адрес
    // содержит одно из этих слов, пробуем ещё и с заменой.
    private val localityTranslations = mapOf(
        "бубуечь" to "Bubuieci",
        "ватра" to "Vatra",
        "дурлешть" to "Durlesti",
        "кодру" to "Codru",
        "крикова" to "Cricova",
        "ставчены" to "Stauceni",
        "гидигич" to "Ghidighici",
        "колоница" to "Colonita",
        "тогатин" to "Tohatin",
        "сынжера" to "Sangera",
        "чорешть" to "Ciorescu",
        "чореску" to "Ciorescu",
        "крузешты" to "Cruzesti",
        "бэчой" to "Bacioi",
        "бачой" to "Bacioi",
        "трушены" to "Truseni",
        "тружень" to "Truseni",
        "гратиешть" to "Gratiesti",
        "вадул-луй-водэ" to "Vadul lui Voda",
        "вадул луй водэ" to "Vadul lui Voda",
        "кишинёв" to "Chisinau",
        "кишинев" to "Chisinau"
    )

    private val localityPrefixesToStrip = listOf("село ", "с. ", "мун. ", "муниципий ", "г. ", "город ")

    /** Известные названия улиц, которые официально пишутся иначе, чем даёт
     *  обычная транслитерация (частые имена — герои/советские названия,
     *  повторяются во многих городах Молдовы). Пополняется по мере находок. */
    private val streetTranslations = mapOf(
        "сергей лазо" to "Serghei Lazo",
        "сергея лазо" to "Serghei Lazo"
    )

    /** Применяет оба словаря (сёла + улицы) к адресу разом — иначе если
     *  заменить только село, а улица останется кириллицей, получится
     *  "гибридный" запрос вроде "Sangera, улица Сергей Лазо", который
     *  Nominatim не может разобрать. */
    /** Служебные слова типа улицы — Nominatim ищет по румынским/международным
     *  обозначениям (strada, bulevardul), а не по русским "улица"/"бульвар". */
    private val streetTypeWords = mapOf(
        "улица" to "strada",
        "ул\\." to "strada",
        "бульвар" to "bulevardul",
        "переулок" to "stradela",
        "проспект" to "bulevardul",
        "шоссе" to "sos",
        "каля" to "calea",
        "скаля" to "calea", // частая опечатка/ослышка "с каля" -> "скаля"
        "дромул" to "drumul",
        "алея" to "aleea",
        "интраря" to "intrarea",
        "интраре" to "intrarea",
        "фундэтура" to "fundatura",
        "фундак" to "fundatura",
        "пасажул" to "pasajul",
        "пиаца" to "piata",
        "пьяца" to "piata",
        "стрэдела" to "stradela",
        "тупик" to "fundatura"
    )

    private fun translitFull(address: String): String? {
        var result = address
        var changed = false
        for ((cyr, lat) in streetTypeWords) {
            val regex = Regex(cyr, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, lat)
                changed = true
            }
        }
        for ((cyr, lat) in localityTranslations) {
            val regex = Regex(cyr, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, lat)
                changed = true
            }
        }
        for ((cyr, lat) in streetTranslations) {
            val regex = Regex(cyr, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, lat)
                changed = true
            }
        }
        if (!changed) return null
        for (prefix in localityPrefixesToStrip) {
            result = result.replace(prefix, "", ignoreCase = true)
        }
        return result.trim()
    }

    private fun translitLocality(address: String): String? {
        var result = address
        var changed = false
        for ((cyr, lat) in localityTranslations) {
            val regex = Regex(cyr, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(result)) {
                result = regex.replace(result, lat)
                changed = true
            }
        }
        if (!changed) return null
        for (prefix in localityPrefixesToStrip) {
            result = result.replace(prefix, "", ignoreCase = true)
        }
        return result.trim()
    }

    /**
     * Геокодирование через публичный Nominatim (OpenStreetMap).
     * Пробуем несколько вариантов запроса по очереди — просто дописывать
     * ", Кишинёв, Молдова" ко всему подряд ломает поиск для сёл в составе
     * муниципия (Бубуечь, Крикова, Ставчены и т.п.), у которых в базе
     * Nominatim собственное название, а не "Кишинёв". Плюс пробуем замену
     * кириллических названий сёл на официальные румынские.
     */
    private val cyrillicToLatin = linkedMapOf(
        "щ" to "sc", "ю" to "iu", "я" to "ia", "ё" to "io", "ж" to "j", "ц" to "t",
        "ч" to "c", "ш" to "s", "х" to "h", "й" to "i", "ъ" to "", "ь" to "",
        "а" to "a", "б" to "b", "в" to "v", "г" to "g", "д" to "d", "е" to "e",
        "з" to "z", "и" to "i", "к" to "c", "л" to "l", "м" to "m", "н" to "n",
        "о" to "o", "п" to "p", "р" to "r", "с" to "s", "т" to "t", "у" to "u",
        "ф" to "f", "ы" to "i", "э" to "e"
    )

    /** Грубая транслитерация кириллицы в латиницу — без румынских диакритиков (ș/ț),
     *  но часто этого достаточно, чтобы Nominatim нашёл улицу по нечёткому совпадению,
     *  когда точного кириллического названия у него в базе нет. */
    private fun transliterate(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val lower = ch.lowercaseChar().toString()
            val mapped = cyrillicToLatin[lower]
            if (mapped != null) {
                sb.append(if (ch.isUpperCase() && mapped.isNotEmpty()) mapped.replaceFirstChar { it.uppercase() } else mapped)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Убирает номер дома из адреса вида "улица Х, 5" -> "улица Х", чтобы
     *  попробовать найти хотя бы саму улицу, если Nominatim не знает дом. */
    private fun stripHouseNumber(address: String): String? {
        val parts = address.split(",")
        if (parts.size < 2) return null
        val withoutLast = parts.dropLast(1).joinToString(",").trim()
        return withoutLast.ifBlank { null }
    }

    /**
     * Основной способ геокодирования — Яндекс Геокодер (бесплатный тариф,
     * лимит ~1000 запросов/сутки). Понимает кириллические адреса нативно —
     * та же база, что использует само приложение Taximeter, так что почти
     * все проблемы с транслитерацией/сёлами/типами улиц отпадают сами собой.
     */
    private fun geocodeYandex(address: String, apiKey: String, context: Context?): LatLng? {
        // Сначала общий геокодер сервера: он помнит адреса всех водителей и не
        // тратит лимит своего ключа. Свой ключ — только если сервер недоступен.
        if (context != null && AppConfig.load(context).sharedGeocoder) {
            val r = CommunityApi.postBlocking(context, "/api/geocode", JSONObject().put("q", address))
            if (r != null && r.optBoolean("ok")) {
                return if (r.optBoolean("found")) LatLng(lat = r.getDouble("lat"), lon = r.getDouble("lon")) else null
            }
            Log.e("FARE_CALC", "Общий геокодер недоступен: ${r?.optString("message")}")
        }
        if (apiKey.isBlank()) return null
        try {
            val encoded = URLEncoder.encode(address, "UTF-8")
            // bbox + rspn=0 — мягкая геопривязка к району Кишинёва, как и раньше
            // с viewbox у Nominatim: не запрещает дальние адреса, только приоритизирует ближние.
            val bbox = "28.60,46.85~29.10,47.20"
            val url = "$YANDEX_GEOCODER_URL?apikey=${URLEncoder.encode(apiKey, "UTF-8")}&geocode=$encoded" +
                    "&format=json&results=5&lang=ru_RU&bbox=$bbox&rspn=0"

            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("FARE_CALC", "Яндекс Геокодер вернул ошибку HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val members = json.getJSONObject("response")
                    .getJSONObject("GeoObjectCollection")
                    .getJSONArray("featureMember")

                if (members.length() == 0) return null

                var best: LatLng? = null
                var bestDist = Double.MAX_VALUE
                for (i in 0 until members.length()) {
                    val point = members.getJSONObject(i)
                        .getJSONObject("GeoObject")
                        .getJSONObject("Point")
                        .getString("pos") // формат Яндекса: "долгота широта"
                    val parts = point.split(" ")
                    val lon = parts[0].toDouble()
                    val lat = parts[1].toDouble()
                    val dist = haversineKm(lat, lon, CITY_CENTER_LAT, CITY_CENTER_LON)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = LatLng(lat = lat, lon = lon)
                    }
                }
                return best
            }
        } catch (e: Exception) {
            Log.e("FARE_CALC", "Яндекс Геокодер: исключение ${e.message}")
            return null
        }
    }

    // Одна и та же карточка заказа приходит десятки раз, пока висит на экране,
    // а бесплатный лимит Геокодера — 1000 запросов в сутки. Кэшируем только
    // найденные адреса: ненайденный пусть пробует ещё раз.
    private val geocodeCache = object : LinkedHashMap<String, LatLng>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LatLng>?) = size > 300
    }

    // Подъезд Геокодеру только мешает: «strada Independenței, 42/2, entrance 1»
    // он уводил в Ленинградскую область, а без хвоста находит точно.
    private val entranceRegex =
        Regex("""(?i)[,\s]*(entrance|scara|scară|подъезд|подъ\.)\s*\S+""")

    // Точка дальше этого от центра Кишинёва — почти наверняка Геокодер ошибся
    // («Gara de Nord» он находит в Бухаресте). Лучше без цены, чем с неверной.
    private const val MAX_DISTANCE_KM = 150.0

    private val reportedMisses = LinkedHashSet<String>()

    private val airportRegex =
        Regex("""(?i)(аэропорт|aeroport|airport|\bRMO\b|зона прил[её]та|зона выл[её]та)""")
    private val otherCityRegex =
        Regex("""(?i)(бельц|b[aă]l[tț]i|одесс|ясс|ia[sș]i|бухарест|bucure|киев|kyiv|стамбул)""")

    /** Где адрес: у нас на сервере или у Яндекса (lat to lon); null — не нашёлся. Не из главного потока. */
    fun locate(context: Context, address: String): Pair<Double, Double>? =
        geocode(address, YandexApiKey.get(context).orEmpty(), context)?.let { it.lat to it.lon }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) = haversineKm(lat1, lon1, lat2, lon2)

    /** Адрес без подъезда — так он хранится в общей базе адресов на сервере. */
    fun addressKey(address: String): String = address.replace(entranceRegex, "").trim().trimEnd(',')

    private fun geocode(address: String, apiKey: String, context: Context?): LatLng? {
        val cacheKey = address.trim().lowercase()
        synchronized(geocodeCache) { geocodeCache[cacheKey] }?.let { return it }
        val clean = addressKey(address)
        // Аэропорт Кишинёва («…, Зона прилёта») Геокодер по тексту карточки не находит.
        if (airportRegex.containsMatchIn(clean) && !otherCityRegex.containsMatchIn(clean)) {
            return LatLng(lat = 46.9350, lon = 28.9330)
        }
        val found = geocodeNear(clean, apiKey, context)
            ?: if (Regex("(?i)chi[șs]in|кишин").containsMatchIn(clean)) null
            else geocodeNear("Chișinău, $clean", apiKey, context)
        if (found == null) {
            Log.e("FARE_CALC", "Яндекс Геокодер не нашёл адрес рядом с Кишинёвом: \"$address\"")
            // В список для админа: он поставит точку вручную, или её принесёт поездка.
            // Карточка перерисовывается десятки раз — сообщаем об адресе один раз.
            val first = synchronized(reportedMisses) {
                reportedMisses.add(clean.lowercase()).also { if (reportedMisses.size > 200) reportedMisses.remove(reportedMisses.first()) }
            }
            if (first && context != null && AppConfig.load(context).sharedGeocoder) {
                CommunityApi.postBlocking(context, "/api/geocode/miss", JSONObject().put("q", clean))
            }
        } else {
            synchronized(geocodeCache) { geocodeCache[cacheKey] = found }
        }
        return found
    }

    private fun geocodeNear(address: String, apiKey: String, context: Context?): LatLng? {
        val p = geocodeYandex(address, apiKey, context) ?: return null
        val km = haversineKm(p.lat, p.lon, CITY_CENTER_LAT, CITY_CENTER_LON)
        if (km > MAX_DISTANCE_KM) {
            Log.e("FARE_CALC", "«$address» найден в ${km.toInt()} км от Кишинёва — не верим")
            return null
        }
        return p
    }

    enum class KeyCheck { OK, REJECTED, NETWORK_ERROR }

    /**
     * Проверяет ключ пользователя настоящим запросом к Геокодеру. Яндекс
     * отвечает 403 и на неверный ключ, и на только что созданный (новый ключ
     * активируется в течение ~15 минут) — различить их по ответу нельзя,
     * поэтому оба случая — REJECTED.
     */
    suspend fun checkYandexKey(apiKey: String): KeyCheck = withContext(Dispatchers.IO) {
        try {
            val url = "$YANDEX_GEOCODER_URL?apikey=${URLEncoder.encode(apiKey, "UTF-8")}" +
                    "&geocode=${URLEncoder.encode("Кишинёв", "UTF-8")}&format=json&results=1"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("FARE_CALC", "Проверка ключа Яндекса: HTTP ${response.code}")
                    return@withContext KeyCheck.REJECTED
                }
                val members = JSONObject(response.body?.string() ?: "")
                    .getJSONObject("response")
                    .getJSONObject("GeoObjectCollection")
                    .getJSONArray("featureMember")
                if (members.length() > 0) KeyCheck.OK else KeyCheck.REJECTED
            }
        } catch (e: java.io.IOException) {
            Log.e("FARE_CALC", "Проверка ключа Яндекса: нет сети ${e.message}")
            KeyCheck.NETWORK_ERROR
        } catch (e: Exception) {
            Log.e("FARE_CALC", "Проверка ключа Яндекса: ${e.message}")
            KeyCheck.REJECTED
        }
    }

    private fun geocodeViaNominatim(address: String): LatLng? {
        val translit = translitLocality(address)
        val full = translitFull(address)
        val streetOnly = stripHouseNumber(address)
        val genericTranslit = transliterate(address)

        val attempts = listOfNotNull(
            "$address, Молдова",
            address,
            full?.let { "$it, Moldova" },
            translit?.let { "$it, Молдова" },
            "$address, Кишинёв, Молдова",
            translit,
            full,
            streetOnly?.let { "$it, Кишинёв, Молдова" },
            "$genericTranslit, Chisinau, Moldova",
            streetOnly?.let { "${transliterate(it)}, Chisinau, Moldova" }
        )
        for ((index, query) in attempts.withIndex()) {
            if (index > 0) Thread.sleep(400) // уважаем лимит Nominatim 1 запрос/сек
            val result = geocodeAttempt(query)
            if (result != null) {
                Log.d("FARE_CALC", "Геокодирование (резерв) сработало на попытке #$index: \"$query\"")
                return result
            }
        }
        return null
    }

    private fun geocodeAttempt(query: String): LatLng? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // Мягкая геопривязка к району Кишинёва (viewbox без bounded=1) — Nominatim
        // предпочитает результаты внутри рамки, но всё равно вернёт результат
        // снаружи, если внутри ничего нет (например, реальный заказ в другой город).
        // Рамка: запад-Трушены, север-Ставчены, восток-Вадул-луй-Водэ, юг-Бэчой/Сынжера.
        val viewbox = "28.60,47.20,29.10,46.85"
        // limit=5 — берём НЕСКОЛЬКО кандидатов и сами выбираем ближайший к Кишинёву,
        // а не слепо доверяем первому результату Nominatim (он может оказаться
        // одноимённой улицей в другом районе Молдовы — уже ловили такой баг).
        val url = "$NOMINATIM_URL/search?q=$encoded&format=json&limit=5&viewbox=$viewbox&bounded=0"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NOMINATIM_USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val results = JSONArray(body)
            if (results.length() == 0) return null

            var best: LatLng? = null
            var bestDist = Double.MAX_VALUE
            for (i in 0 until results.length()) {
                val obj = results.getJSONObject(i)
                val lat = obj.getString("lat").toDouble()
                val lon = obj.getString("lon").toDouble()
                val dist = haversineKm(lat, lon, CITY_CENTER_LAT, CITY_CENTER_LON)
                if (dist < bestDist) {
                    bestDist = dist
                    best = LatLng(lat = lat, lon = lon)
                }
            }
            return best
        }
    }
}
