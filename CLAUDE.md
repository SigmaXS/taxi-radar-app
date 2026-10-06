# Taxi Radar — Android

Читает карточки заказов Yandex Pro через AccessibilityService и показывает в оверлее цену поездки
по тарифам Кишинёва, надбавку (surge) и карту. Пользователь общается по-русски — отвечать по-русски.

## Папки и ветки
- `AndroidStudioProjects/TaxiRadar` — **только ветка `main`** (Android, Kotlin). Не переключать здесь на `flutter`.
- `AndroidStudioProjects/taxi_radar_app` — iOS/Flutter, ветка `flutter` того же репозитория. Работу по iOS вести там.
- `AndroidStudioProjects/taxi-radar-license` — сервер (Node/Express + Postgres), Railway деплоит сам при push в `main`.
- `AndroidStudioProjects/TaxiRadar-keys` — ключ подписи, keystore.properties, готовые APK. **Никогда не коммитить, не читать пароли, не выводить ключи.**

## Сборка
- JDK: `C:\Program Files\Android\Android Studio\jbr`. Сборка: `./gradlew.bat assembleRelease -q`.
- applicationId `md.taxiradar.app`, namespace Kotlin — `com.example.taxiradar` (не менять).
- Debug и release подписываются release-ключом; сертификат SHA-256 начинается на `6d955dae`, кончается на `2f80da70`.
- Выпуск версии — навык `/release`.

## Правила
- Версию (versionCode/versionName) поднимать **только для настоящего релиза**, не на мелких правках.
- Надбавка = цена «de la N» с routestats Яндекса минус старт 30/45/65 (Econom/Comfort/Comfort+); шаги +15/+35/+55.
  Без геолокации показываем 📍, а не надбавку другой точки.
- Доставка удалена полностью: карточки доставки пропускаем, не возвращать.
- Час пик +10 мин (будни 7–9, 13–18) — только если время не от водителей (`fromDrivers`).
- Не советовать и не описывать отключение Play Protect.
- Ключи API (Яндекс, AirLabs, Telegram и т.д.) пользователь сам кладёт в переменные Railway — не вписывать в код.
- Телефон пользователя: ничего не ставить через adb без спроса (он может быть на заказе).
- Уведомление об обновлении (LATEST_VERSION_* на сервере) — только по прямой команде пользователя.
- `activity_main.xml` править напрямую.
