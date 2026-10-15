package com.example.taxiradar

import android.content.Context

/**
 * Какие сервисы слушает радар и чей экран сейчас перед водителем.
 *
 * Яндекс Про — прежнее правило 2.2 (пакет содержит «taximeter» или «yandex»), не меняется.
 * Bolt Driver — точное имя пакета `ee.mtakso.driver` (проверено по страницам приложения в Google Play /
 * RuStore / Exodus Privacy) плюс пакеты, которые водитель добавит сам в настройках, если у него другая сборка.
 * Пассажирское приложение Bolt (`ee.mtakso.client`) сюда не входит.
 */
object TaxiServices {
    enum class Source { YANDEX, BOLT }

    const val BOLT_DRIVER_PACKAGE = "ee.mtakso.driver"

    fun isYandexPackage(pkg: String) = pkg.contains("taximeter", true) || pkg.contains("yandex", true)

    /** Чистая функция: источник по имени пакета окна. */
    fun sourceOf(pkg: String?, ownPackage: String, boltPackages: Set<String>): Source? {
        val p = pkg?.trim().orEmpty()
        if (p.isEmpty() || p == ownPackage) return null
        if (p in boltPackages) return Source.BOLT
        if (isYandexPackage(p)) return Source.YANDEX
        return null
    }

    fun parsePackages(text: String): Set<String> =
        text.split(',', ';', ' ', '\n').map { it.trim() }
            .filter { Regex("""^[a-zA-Z][\w]*(\.[a-zA-Z_][\w]*)+$""").matches(it) }.toSet()

    // ---------- настройки (taxi_radar_prefs, ключи driver_*) ----------

    fun yandexEnabled(c: Context) = DriverPreferences.flag(c, "service_yandex", true)
    fun boltEnabled(c: Context) = DriverPreferences.flag(c, "service_bolt", false)

    fun boltPackages(c: Context): Set<String> =
        setOf(BOLT_DRIVER_PACKAGE) + parsePackages(DriverPreferences.text(c, "bolt_packages"))

    fun sourceOf(c: Context, pkg: String?): Source? = sourceOf(pkg, c.packageName, boltPackages(c))

    fun boltSettings(c: Context): BoltEconomics.Settings {
        val costs = if (DriverPreferences.costsReady(c)) DriverPreferences.costs(c).perKm else null
        return BoltEconomics.Settings(
            commissionPercent = if (DriverPreferences.flag(c, "bolt_price_before_commission", false))
                DriverPreferences.number(c, "bolt_commission", 0.0) else 0.0,
            costPerKm = costs,
            serviceMin = DriverPreferences.number(c, "bolt_service_min", 2.0).toInt(),
            minPerKm = DriverPreferences.number(c, "bolt_min_per_km", 0.0),
            minPerHour = DriverPreferences.number(c, "bolt_min_per_hour", 0.0),
            minPerMin = DriverPreferences.number(c, "bolt_min_per_min", 0.0),
            minRating = DriverPreferences.number(c, "bolt_min_rating", 0.0),
            shortPickupKm = DriverPreferences.number(c, "bolt_short_pickup_km", 2.0)
        )
    }

    fun boltShowMs(c: Context) = (DriverPreferences.number(c, "bolt_show_sec", 15.0).coerceIn(5.0, 60.0) * 1000).toLong()
    fun boltDiagnostics(c: Context) = DriverPreferences.flag(c, "bolt_diagnostics", false)
}
