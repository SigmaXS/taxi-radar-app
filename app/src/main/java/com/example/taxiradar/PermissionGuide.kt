package com.example.taxiradar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Collapsible brand guides. Paths vary across One UI / MIUI / HyperOS releases. */
object PermissionGuide {
    fun add(activity: Activity, parent: LinearLayout) {
        fun t(ru: String, ro: String) = DriverUi.t(activity, ru, ro)
        val box = DriverUi.card(activity, parent, t("Как включить разрешения", "Cum activați permisiunile"), t("Выберите свой телефон и раскройте инструкцию. Названия пунктов зависят от версии Android.", "Alegeți telefonul și deschideți ghidul. Denumirile diferă în funcție de versiunea Android."), R.drawable.ic_settings)
        DriverUi.expandable(activity, box, "Samsung · One UI", t(
            "1. Настройки → Специальные возможности → Установленные приложения → Taxi Radar → включить.\n\n2. Если написано «Ограниченная настройка»: Настройки → Приложения → Taxi Radar → меню ⋮ → Разрешить ограниченные настройки. Подтвердите PIN и вернитесь к пункту 1.\n\n3. Если ⋮ нет: сначала попробуйте включить службу и закройте сообщение об ограничении, затем снова откройте «О приложении». Если пункт всё равно отсутствует, названия и доступность зависят от One UI — воспользуйтесь поиском настроек или поддержкой Samsung.\n\n4. Приложения → Taxi Radar → Батарея → Без ограничений. При наличии списка «Приложения, которые никогда не переходят в спящий режим», добавьте Taxi Radar.\n\nПосле обновления APK выключите и включите службу Taxi Radar. Затем вернитесь сюда и проверьте статус.",
            "1. Setări → Accesibilitate → Aplicații instalate → Taxi Radar → activați.\n\n2. Dacă apare «Setare restricționată»: Setări → Aplicații → Taxi Radar → ⋮ → Permiteți setările restricționate. Confirmați PIN-ul și reveniți la pasul 1.\n\n3. Dacă ⋮ lipsește: încercați întâi activarea serviciului și închideți mesajul, apoi redeschideți informațiile aplicației. Disponibilitatea diferă în One UI; folosiți căutarea setărilor sau suportul Samsung.\n\n4. Aplicații → Taxi Radar → Baterie → Fără restricții. Dacă există lista aplicațiilor care nu intră niciodată în repaus, adăugați Taxi Radar.\n\nDupă actualizarea APK opriți și porniți serviciul Taxi Radar, apoi verificați starea aici."
        ))
        DriverUi.expandable(activity, box, "Xiaomi / Redmi / POCO · MIUI / HyperOS", t(
            "1. Настройки → Расширенные настройки → Специальные возможности → Скачанные приложения → Taxi Radar → включить. Можно найти «Специальные возможности» поиском в настройках.\n\n2. Если переключатель серый: Настройки → Приложения → Все приложения → Taxi Radar. Найдите «Разрешить ограниченные / запрещённые настройки»: в меню ⋮ либо внизу страницы. Подтвердите и вернитесь к пункту 1.\n\n3. Если пункта нет, сначала попытайтесь включить службу и закройте сообщение об ограничении. Вернитесь на страницу приложения.\n\n4. На странице Taxi Radar включите «Автозапуск» / «Фоновый автозапуск». Батарея / Контроль активности → Нет ограничений. В дополнительных разрешениях разрешите всплывающие окна и показ в фоне, если такие пункты есть.\n\n5. Если прошивка предлагает замок в списке недавних приложений, закрепите Taxi Radar. После обновления выключите и включите службу.",
            "1. Setări → Setări suplimentare → Accesibilitate → Aplicații descărcate → Taxi Radar → activați. Puteți căuta «Accesibilitate» în setări.\n\n2. Dacă comutatorul este gri: Setări → Aplicații → Gestionare aplicații → Taxi Radar. Căutați «Permiteți setările restricționate»: în meniul ⋮ sau la sfârșitul paginii. Confirmați și reveniți la pasul 1.\n\n3. Dacă opțiunea lipsește, încercați întâi activarea serviciului și închideți mesajul. Redeschideți pagina aplicației.\n\n4. Activați pornirea automată / în fundal. Baterie → Fără restricții. În permisiunile suplimentare permiteți ferestrele pop-up și afișarea în fundal, dacă există.\n\n5. Dacă există lacătul în aplicațiile recente, fixați Taxi Radar. După actualizare opriți și porniți serviciul."
        ))
        DriverUi.expandable(activity, box, t("Другой телефон / не получается", "Alt telefon / nu funcționează"), t(
            "1. Откройте Специальные возможности и включите Taxi Radar.\n2. При ограничении откройте «О приложении» → ⋮ → Разрешить ограниченные настройки.\n3. Разрешите «Поверх других приложений», геолокацию и уведомления.\n4. Если система пишет, что другое окно мешает, остановите плавающий виджет и попробуйте снова.\n5. Если тумблер включён, но служба не работает: выключите и включите его.\n6. Если настройки вылетают на магнитоле, используйте кнопку обходных способов в проверке настроек.\n\nНе требуется выдавать разрешения чужим приложениям. Если нужного пункта нет, уточните модель и версию Android у поддержки.",
            "1. Deschideți Accesibilitate și activați Taxi Radar.\n2. Pentru restricții: informațiile aplicației → ⋮ → Permiteți setările restricționate.\n3. Permiteți afișarea peste alte aplicații, localizarea și notificările.\n4. Dacă o altă fereastră blochează setarea, opriți widgetul și încercați din nou.\n5. Dacă serviciul este activat, dar nu funcționează, opriți și reporniți comutatorul.\n6. Pentru unități auto unde setările se închid, folosiți metodele alternative din verificarea setărilor.\n\nNu trebuie să acordați permisiuni altor aplicații. Dacă opțiunea lipsește, comunicați modelul și versiunea Android suportului."
        ))
        DriverUi.button(activity, box, t("Открыть страницу Taxi Radar в настройках", "Deschide setările Taxi Radar")) {
            try { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))) } catch (_: Exception) {}
        }
        DriverUi.button(activity, box, t("Открыть Специальные возможности", "Deschide Accesibilitate")) { AccessibilityAccess.open(activity) }
        val links = DriverUi.card(activity, parent, t("Инструкции и видео", "Ghiduri și video"), t("Видео на английском; расположение пунктов зависит от прошивки. Для HyperOS используйте актуальную инструкцию Xiaomi, а не старый экран MIUI.", "Videouri în engleză; meniurile diferă după sistem. Pentru HyperOS folosiți ghidul actual Xiaomi, nu vechiul ecran MIUI."), R.drawable.ic_play)
        fun link(label: String, url: String) = DriverUi.button(activity, links, label) {
            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(activity, t("Установите браузер для открытия ссылки", "Instalați un browser pentru link"), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        link(t("Видео: Samsung — ограниченные настройки", "Video: Samsung — setări restricționate"), "https://www.youtube.com/watch?v=6ilFOvyYg8w")
        link(t("Видео: Xiaomi MIUI 12 — автозапуск", "Video: Xiaomi MIUI 12 — pornire automată"), "https://www.youtube.com/watch?v=00ybP-fuEJo")
        link(t("Xiaomi HyperOS — инструкция производителя", "Xiaomi HyperOS — ghidul producătorului"), "https://www.mi.com/global/support/faq/details/KA-507611/")
        link(t("Samsung — инструкция производителя", "Samsung — ghidul producătorului"), "https://www.samsung.com/us/support/answer/ANS10001906/")
    }
    fun show(activity: Activity) {
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(DriverUi.dp(activity, 12), 0, DriverUi.dp(activity, 12), 0) }
        add(activity, body)
        MaterialAlertDialogBuilder(activity).setTitle(DriverUi.t(activity, "Разрешения", "Permisiuni"))
            .setView(ScrollView(activity).apply { addView(body) }).setPositiveButton(R.string.got_it, null).show()
    }
}
