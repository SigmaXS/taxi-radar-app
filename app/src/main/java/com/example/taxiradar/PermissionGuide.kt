package com.example.taxiradar

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * «Как включить разрешения»: на экране — короткий список марок (свёрнуто), по
 * нажатию — пошаговая инструкция с картинками-схемами экранов настроек и ссылками
 * на видео. Схемы нарисованы в приложении: работают без интернета и на русском.
 */
object PermissionGuide {

    private enum class Brand { SAMSUNG, XIAOMI, OTHER }

    private fun current(): Brand {
        val b = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            b.contains("samsung") -> Brand.SAMSUNG
            listOf("xiaomi", "redmi", "poco").any { b.contains(it) } -> Brand.XIAOMI
            else -> Brand.OTHER
        }
    }

    // Ссылки: видео показывают ровно наш случай; инструкция Касперского — на русском, со скриншотами.
    private const val VIDEO_SAMSUNG = "https://www.youtube.com/watch?v=6ilFOvyYg8w"
    private const val VIDEO_HYPEROS = "https://www.youtube.com/watch?v=91B72lEpcqc"
    private const val GUIDE_RU = "https://support.kaspersky.com/help/KSMM/4.1/ru-RU/237468.htm"
    private const val GUIDE_XIAOMI = "https://www.mi.com/global/support/faq/details/KA-507611/"

    fun add(activity: Activity, parent: LinearLayout) {
        fun t(ru: String, ro: String) = DriverUi.t(activity, ru, ro)
        val box = DriverUi.card(
            activity, parent, t("Как включить разрешения", "Cum activați permisiunile"),
            t("Нажмите на свой телефон — откроется инструкция с картинками.", "Apăsați pe telefonul dvs. — se deschide ghidul cu imagini."),
            R.drawable.ic_settings
        )
        val mine = current()
        brandRow(activity, box, "Samsung", "One UI", mine == Brand.SAMSUNG) { open(activity, Brand.SAMSUNG) }
        brandRow(activity, box, "Xiaomi · Redmi · POCO", "HyperOS / MIUI", mine == Brand.XIAOMI) { open(activity, Brand.XIAOMI) }
        brandRow(activity, box, t("Другой телефон", "Alt telefon"), t("Realme, Oppo, Honor, магнитолы…", "Realme, Oppo, Honor, unități auto…"), mine == Brand.OTHER) { open(activity, Brand.OTHER) }
    }

    /** Мастер первого запуска: та же инструкция сразу для этого телефона. */
    fun show(activity: Activity) = open(activity, current())

    /** Строка марки: явно кнопка — значок, подпись «Инструкция», стрелка. */
    private fun brandRow(activity: Activity, parent: LinearLayout, title: String, subtitle: String, mine: Boolean, onTap: () -> Unit) {
        fun t(ru: String, ro: String) = DriverUi.t(activity, ru, ro)
        val c = activity
        val row = LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = DriverUi.dp(c, 64)
            setPadding(DriverUi.dp(c, 14), DriverUi.dp(c, 10), DriverUi.dp(c, 10), DriverUi.dp(c, 10))
            background = GradientDrawable().apply {
                cornerRadius = DriverUi.dp(c, 16).toFloat()
                setColor(c.getColor(R.color.tr_surface_high))
                if (mine) setStroke(DriverUi.dp(c, 2), c.getColor(R.color.tr_accent))
            }
            isClickable = true
            isFocusable = true
            foreground = c.getDrawable(android.R.drawable.list_selector_background)
            setOnClickListener { onTap() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(c, 10) }
        }
        row.addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_accent))
        }, LinearLayout.LayoutParams(DriverUi.dp(c, 26), DriverUi.dp(c, 26)).apply { marginEnd = DriverUi.dp(c, 12) })
        val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(c).apply {
            text = title
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_text))
        })
        texts.addView(TextView(c).apply {
            text = if (mine) "$subtitle · " + t("ваш телефон", "telefonul dvs.") else subtitle
            textSize = 13f
            setTextColor(c.getColor(if (mine) R.color.tr_accent else R.color.tr_text_secondary))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(c).apply {
            text = t("Инструкция  ›", "Ghid  ›")
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_accent))
        })
        parent.addView(row)
    }

    // ---------- пошаговая инструкция ----------

    private fun open(activity: Activity, brand: Brand) {
        fun t(ru: String, ro: String) = DriverUi.t(activity, ru, ro)
        val c = activity
        val body = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(c, 18), DriverUi.dp(c, 8), DriverUi.dp(c, 18), DriverUi.dp(c, 24))
        }
        body.addView(TextView(c).apply {
            text = when (brand) {
                Brand.SAMSUNG -> "Samsung"
                Brand.XIAOMI -> "Xiaomi · Redmi · POCO"
                Brand.OTHER -> t("Другой телефон", "Alt telefon")
            }
            textSize = 24f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_text))
        })
        body.addView(TextView(c).apply {
            text = t(
                "Android не даёт сразу включить Taxi Radar, потому что он установлен из файла, а не из магазина. Это снимается один раз — по шагам ниже.",
                "Android nu permite imediat activarea Taxi Radar, fiindcă e instalat din fișier, nu din magazin. Se rezolvă o singură dată — pașii de mai jos."
            )
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(c.getColor(R.color.tr_text_secondary))
            setPadding(0, DriverUi.dp(c, 6), 0, DriverUi.dp(c, 12))
        })

        val accSamsung = t("Специальные возможности", "Accesibilitate")
        when (brand) {
            Brand.SAMSUNG -> {
                step(c, body, 1, t("Попробуйте включить", "Încercați să activați"), t(
                    "Настройки → Специальные возможности → Установленные приложения → Taxi Radar. Переключатель не нажимается и появится «Ограниченная настройка» — нажмите «ОК». Так и должно быть: это открывает следующий шаг.",
                    "Setări → Accesibilitate → Aplicații instalate → Taxi Radar. Comutatorul nu se apasă și apare «Setare restricționată» — apăsați «OK». E normal: așa se deschide pasul următor."
                ), screen(c, listOf(row(c, accSamsung), row(c, t("Установленные приложения", "Aplicații instalate")), row(c, "Taxi Radar", Trail.SWITCH_GREY)),
                    t("Ограниченная настройка", "Setare restricționată")))
                step(c, body, 2, t("Разрешите ограниченные настройки", "Permiteți setările restricționate"), t(
                    "Настройки → Приложения → Taxi Radar → три точки ⋮ вверху справа → «Разрешить ограниченные настройки». Подтвердите PIN-кодом или отпечатком.",
                    "Setări → Aplicații → Taxi Radar → trei puncte ⋮ sus în dreapta → «Permiteți setările restricționate». Confirmați cu PIN sau amprentă."
                ), menuScreen(c, "Taxi Radar", t("Разрешить ограниченные настройки", "Permiteți setările restricționate")))
                step(c, body, 3, t("Включите Taxi Radar", "Activați Taxi Radar"), t(
                    "Вернитесь: Специальные возможности → Установленные приложения → Taxi Radar → включите и нажмите «Разрешить».",
                    "Reveniți: Accesibilitate → Aplicații instalate → Taxi Radar → activați și apăsați «Permite»."
                ), screen(c, listOf(row(c, "Taxi Radar", Trail.SWITCH_ON))))
                step(c, body, 4, t("Батарея без ограничений", "Baterie fără restricții"), t(
                    "Приложения → Taxi Radar → Батарея → «Без ограничений». Иначе Samsung может «усыпить» радар на смене.",
                    "Aplicații → Taxi Radar → Baterie → «Fără restricții». Altfel Samsung poate adormi radarul în tură."
                ), screen(c, listOf(row(c, t("Батарея", "Baterie")), row(c, t("Без ограничений", "Fără restricții"), Trail.CHECK))))
            }
            Brand.XIAOMI -> {
                step(c, body, 1, t("Попробуйте включить", "Încercați să activați"), t(
                    "Настройки → Расширенные настройки → Специальные возможности → Скачанные приложения → Taxi Radar. Переключатель серый и появится сообщение об ограничении — нажмите «ОК». Это нормально: так открывается следующий шаг.",
                    "Setări → Setări suplimentare → Accesibilitate → Aplicații descărcate → Taxi Radar. Comutatorul e gri și apare un mesaj — apăsați «OK». E normal: așa se deschide pasul următor."
                ), screen(c, listOf(row(c, t("Специальные возможности", "Accesibilitate")), row(c, t("Скачанные приложения", "Aplicații descărcate")), row(c, "Taxi Radar", Trail.SWITCH_GREY)),
                    t("Доступ к настройке ограничен", "Acces restricționat")))
                step(c, body, 2, t("Разрешите ограниченные настройки", "Permiteți setările restricționate"), t(
                    "Настройки → Приложения → Все приложения → Taxi Radar. На HyperOS пролистайте страницу вниз до «Разрешить ограниченные (запрещённые) настройки». На MIUI — три точки ⋮ вверху справа. Подтвердите.",
                    "Setări → Aplicații → Gestionare aplicații → Taxi Radar. Pe HyperOS derulați jos până la «Permiteți setările restricționate». Pe MIUI — trei puncte ⋮ sus în dreapta. Confirmați."
                ), screen(c, listOf(row(c, t("Уведомления", "Notificări")), row(c, t("Разрешения приложений", "Permisiuni")), row(c, t("Разрешить ограниченные настройки", "Permiteți setările restricționate"), Trail.HIGHLIGHT))))
                step(c, body, 3, t("Включите Taxi Radar", "Activați Taxi Radar"), t(
                    "Вернитесь в Специальные возможности → Скачанные приложения → Taxi Radar → включите.",
                    "Reveniți la Accesibilitate → Aplicații descărcate → Taxi Radar → activați."
                ), screen(c, listOf(row(c, "Taxi Radar", Trail.SWITCH_ON))))
                step(c, body, 4, t("Автозапуск и батарея", "Pornire automată și baterie"), t(
                    "На той же странице Taxi Radar: «Автозапуск» — включить; «Контроль активности» (батарея) — «Нет ограничений». Иначе Xiaomi закрывает радар в фоне.",
                    "Pe aceeași pagină Taxi Radar: «Pornire automată» — activați; «Economisire baterie» — «Fără restricții». Altfel Xiaomi închide radarul în fundal."
                ), screen(c, listOf(row(c, t("Автозапуск", "Pornire automată"), Trail.SWITCH_ON), row(c, t("Контроль активности: Нет ограничений", "Baterie: Fără restricții"), Trail.CHECK))))
            }
            Brand.OTHER -> {
                step(c, body, 1, t("Попробуйте включить", "Încercați să activați"), t(
                    "Настройки → Специальные возможности (иногда в «Дополнительные настройки») → Taxi Radar → включить. Если не нажимается — нажмите «ОК» в сообщении и переходите к шагу 2.",
                    "Setări → Accesibilitate (uneori în «Setări suplimentare») → Taxi Radar → activați. Dacă nu se apasă — apăsați «OK» și treceți la pasul 2."
                ), screen(c, listOf(row(c, t("Специальные возможности", "Accesibilitate")), row(c, "Taxi Radar", Trail.SWITCH_GREY))))
                step(c, body, 2, t("Разрешите ограниченные настройки", "Permiteți setările restricționate"), t(
                    "Настройки → Приложения → Taxi Radar → три точки ⋮ (или внизу страницы) → «Разрешить ограниченные настройки». На Realme пункт может быть в «Безопасность».",
                    "Setări → Aplicații → Taxi Radar → trei puncte ⋮ (sau jos pe pagină) → «Permiteți setările restricționate». Pe Realme opțiunea poate fi în «Securitate»."
                ), menuScreen(c, "Taxi Radar", t("Разрешить ограниченные настройки", "Permiteți setările restricționate")))
                step(c, body, 3, t("Включите и не давайте усыплять", "Activați și nu lăsați să adoarmă"), t(
                    "Вернитесь и включите Taxi Radar. Затем в настройках батареи уберите для него ограничения. На магнитоле, где экран настроек вылетает, используйте «Экран „Спец. возможности“ вылетает?» в проверке настроек.",
                    "Reveniți și activați Taxi Radar. Apoi scoateți restricțiile de baterie. Pe unitatea auto unde setările se închid folosiți «Ecranul Accesibilitate se închide?» din verificare."
                ), screen(c, listOf(row(c, "Taxi Radar", Trail.SWITCH_ON))))
            }
        }

        // Кнопки — сразу в нужные места.
        DriverUi.button(c, body, t("Открыть страницу Taxi Radar", "Deschide pagina Taxi Radar")) {
            try { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))) } catch (_: Exception) {}
        }
        DriverUi.button(c, body, t("Открыть Специальные возможности", "Deschide Accesibilitate")) { AccessibilityAccess.open(c) }

        body.addView(TextView(c).apply {
            text = t("Видео и инструкции", "Video și ghiduri")
            textSize = 17f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_text))
            setPadding(0, DriverUi.dp(c, 20), 0, DriverUi.dp(c, 8))
        })
        fun link(label: String, url: String) = DriverUi.button(c, body, label) {
            try { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
        }
        when (brand) {
            Brand.SAMSUNG -> link(t("▶ Видео: Samsung (англ.)", "▶ Video: Samsung (engl.)"), VIDEO_SAMSUNG)
            Brand.XIAOMI -> {
                link(t("▶ Видео: HyperOS / MIUI 14 (англ.)", "▶ Video: HyperOS / MIUI 14 (engl.)"), VIDEO_HYPEROS)
                link(t("Инструкция Xiaomi (англ.)", "Ghidul Xiaomi (engl.)"), GUIDE_XIAOMI)
            }
            Brand.OTHER -> {}
        }
        link(t("Инструкция со скриншотами (рус.)", "Ghid cu capturi (rus.)"), GUIDE_RU)

        val dialog = BottomSheetDialog(c)
        dialog.setContentView(ScrollView(c).apply { addView(body) })
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    // ---------- шаг и картинки-схемы ----------

    private fun step(c: Activity, parent: LinearLayout, n: Int, title: String, text: String, picture: View) {
        val head = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, DriverUi.dp(c, 14), 0, DriverUi.dp(c, 6)) }
        head.addView(TextView(c).apply {
            this.text = "$n"
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_on_accent))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.getColor(R.color.tr_accent)) }
        }, LinearLayout.LayoutParams(DriverUi.dp(c, 28), DriverUi.dp(c, 28)).apply { marginEnd = DriverUi.dp(c, 10) })
        head.addView(TextView(c).apply {
            this.text = title
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_text))
        })
        parent.addView(head)
        parent.addView(TextView(c).apply {
            this.text = text
            textSize = 14f
            setLineSpacing(DriverUi.dp(c, 2).toFloat(), 1f)
            setTextColor(c.getColor(R.color.tr_text_secondary))
            setPadding(DriverUi.dp(c, 38), 0, 0, DriverUi.dp(c, 10))
        })
        parent.addView(picture, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = DriverUi.dp(c, 6) })
    }

    private enum class Trail { CHEVRON, SWITCH_GREY, SWITCH_ON, CHECK, HIGHLIGHT }

    private fun row(c: Activity, title: String, trail: Trail = Trail.CHEVRON) = Pair(title, trail)

    /** «Экран настроек»: строки с переключателями; при [dialog] — окно-сообщение поверх. */
    private fun screen(c: Activity, rows: List<Pair<String, Trail>>, dialog: String? = null): View {
        val frame = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(c, 10), DriverUi.dp(c, 10), DriverUi.dp(c, 10), DriverUi.dp(c, 10))
            background = GradientDrawable().apply {
                cornerRadius = DriverUi.dp(c, 18).toFloat()
                setColor(c.getColor(R.color.tr_bg))
                setStroke(DriverUi.dp(c, 1), c.getColor(R.color.tr_console_border))
            }
        }
        rows.forEachIndexed { i, (title, trail) ->
            val line = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(DriverUi.dp(c, 12), DriverUi.dp(c, 10), DriverUi.dp(c, 12), DriverUi.dp(c, 10))
                background = GradientDrawable().apply {
                    cornerRadius = DriverUi.dp(c, 10).toFloat()
                    setColor(c.getColor(if (trail == Trail.HIGHLIGHT) R.color.tr_accent else R.color.tr_surface))
                }
            }
            line.addView(TextView(c).apply {
                text = title
                textSize = 14f
                setTextColor(c.getColor(if (trail == Trail.HIGHLIGHT) R.color.tr_on_accent else R.color.tr_text))
                if (trail == Trail.HIGHLIGHT) setTypeface(null, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(trailView(c, trail))
            frame.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = DriverUi.dp(c, 6) })
        }
        if (dialog != null) {
            frame.addView(TextView(c).apply {
                text = "$dialog\n" + DriverUi.t(c, "ОК", "OK")
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(c.getColor(R.color.tr_text))
                setPadding(DriverUi.dp(c, 12), DriverUi.dp(c, 10), DriverUi.dp(c, 12), DriverUi.dp(c, 10))
                background = GradientDrawable().apply {
                    cornerRadius = DriverUi.dp(c, 14).toFloat()
                    setColor(c.getColor(R.color.tr_surface_high))
                    setStroke(DriverUi.dp(c, 1), c.getColor(R.color.tr_warning))
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = DriverUi.dp(c, 10) })
        }
        return frame
    }

    /** «Страница приложения» с открытым меню ⋮ и выделенным пунктом. */
    private fun menuScreen(c: Activity, app: String, item: String): View {
        val frame = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(DriverUi.dp(c, 12), DriverUi.dp(c, 10), DriverUi.dp(c, 12), DriverUi.dp(c, 12))
            background = GradientDrawable().apply {
                cornerRadius = DriverUi.dp(c, 18).toFloat()
                setColor(c.getColor(R.color.tr_bg))
                setStroke(DriverUi.dp(c, 1), c.getColor(R.color.tr_console_border))
            }
        }
        val top = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(c).apply {
            text = app
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_text))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(c).apply {
            text = "⋮"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(c.getColor(R.color.tr_on_accent))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.getColor(R.color.tr_accent)) }
        }, LinearLayout.LayoutParams(DriverUi.dp(c, 34), DriverUi.dp(c, 34)))
        frame.addView(top)
        frame.addView(TextView(c).apply {
            text = item
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_on_accent))
            setPadding(DriverUi.dp(c, 12), DriverUi.dp(c, 10), DriverUi.dp(c, 12), DriverUi.dp(c, 10))
            background = GradientDrawable().apply {
                cornerRadius = DriverUi.dp(c, 10).toFloat()
                setColor(c.getColor(R.color.tr_accent))
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = DriverUi.dp(c, 8) })
        return frame
    }

    private fun trailView(c: Activity, trail: Trail): View = when (trail) {
        Trail.CHEVRON, Trail.HIGHLIGHT -> TextView(c).apply {
            text = "›"
            textSize = 20f
            setTextColor(c.getColor(if (trail == Trail.HIGHLIGHT) R.color.tr_on_accent else R.color.tr_text_secondary))
        }
        Trail.CHECK -> TextView(c).apply {
            text = "✓"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(c.getColor(R.color.tr_success))
        }
        Trail.SWITCH_GREY, Trail.SWITCH_ON -> {
            val on = trail == Trail.SWITCH_ON
            LinearLayout(c).apply {
                gravity = if (on) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(DriverUi.dp(c, 3), DriverUi.dp(c, 3), DriverUi.dp(c, 3), DriverUi.dp(c, 3))
                background = GradientDrawable().apply {
                    cornerRadius = DriverUi.dp(c, 12).toFloat()
                    setColor(c.getColor(if (on) R.color.tr_success else R.color.tr_text_muted))
                }
                layoutParams = LinearLayout.LayoutParams(DriverUi.dp(c, 42), DriverUi.dp(c, 24))
                addView(View(c).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) }
                }, LinearLayout.LayoutParams(DriverUi.dp(c, 18), DriverUi.dp(c, 18)))
            }
        }
    }
}
