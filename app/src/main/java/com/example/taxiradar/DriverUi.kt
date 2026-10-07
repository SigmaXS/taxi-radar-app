package com.example.taxiradar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** Native, scrollable cards, generous touch targets and progressive explanations. */
object DriverUi {
    fun wide(c: Context) = c.resources.configuration.screenWidthDp >= 600

    /** Two reading columns on wide displays, larger targets on tablets/car units. */
    fun arrangeCards(c: Context, parent: LinearLayout) {
        if (c.resources.configuration.screenWidthDp < 840) return
        val cards = (0 until parent.childCount).map { parent.getChildAt(it) }.filterIsInstance<MaterialCardView>()
        if (cards.size < 2) return
        val first = parent.indexOfChild(cards.first())
        cards.forEach { parent.removeView(it) }
        cards.chunked(2).forEachIndexed { index, pair ->
            val row = LinearLayout(c).apply { gravity = Gravity.TOP }
            pair.forEachIndexed { column, card ->
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    bottomMargin = dp(c, 16)
                    if (column == 0) marginEnd = dp(c, 16)
                })
            }
            if (pair.size == 1) row.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
            parent.addView(row, first + index)
        }
    }

    fun adaptDashboard(c: Context, root: View) {
        if (!wide(c)) return
        fun walk(v: View) {
            if (v is MaterialButton) { v.minHeight = dp(c, 64); v.textSize = 18f }
            if (v is MaterialCardView) v.setContentPadding(dp(c, 24), dp(c, 20), dp(c, 24), dp(c, 20))
            if (v is android.view.ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
        }
        walk(root)
        // Main actions side by side; descriptive header stays full width.
        val settings = root.findViewById<MaterialButton>(R.id.btnDriverSettings)
        val shift = root.findViewById<MaterialButton>(R.id.btnDriverShift)
        val box = settings.parent as LinearLayout
        box.removeView(settings); box.removeView(shift)
        box.addView(LinearLayout(c).apply {
            addView(settings, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(c, 12) })
            addView(shift, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }
    fun t(c: Context, ru: String, ro: String) = if (c.resources.configuration.locales[0].language == "ro") ro else ru
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun text(c: Context, label: String, size: Float = 15f, muted: Boolean = false) = TextView(c).apply {
        text = label; textSize = size; setTextColor(c.getColor(if (muted) R.color.tr_text_secondary else R.color.tr_text))
        // Всё по центру: экраны помощника читаются как карточки, а не как список настроек.
        gravity = Gravity.CENTER_HORIZONTAL; textAlignment = View.TEXT_ALIGNMENT_CENTER
        if (size >= 22f) setTypeface(null, Typeface.BOLD)
        setLineSpacing(dp(c, 3).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 8) }
    }
    fun card(c: Context, parent: LinearLayout, title: String, explanation: String, icon: Int = R.drawable.ic_help): LinearLayout {
        val card = MaterialCardView(c).apply {
            radius = dp(c, 22).toFloat(); strokeWidth = dp(c, 1); strokeColor = c.getColor(R.color.tr_console_border)
            setCardBackgroundColor(c.getColor(R.color.tr_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 14) }
        }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, if (wide(c)) 24 else 18), dp(c, 16), dp(c, if (wide(c)) 24 else 18), dp(c, 14)) }
        // Заголовок по центру: слева пустое место той же ширины, что кнопка «?» справа.
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(View(c), LinearLayout.LayoutParams(dp(c, 48), 1))
        val head = LinearLayout(c).apply { gravity = Gravity.CENTER }
        head.addView(ImageView(c).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_cyan)); contentDescription = null }, LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)).apply { marginEnd = dp(c, 8) })
        head.addView(text(c, title, 18f).apply { setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(-2, -2) })
        row.addView(head, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(info(c, title, explanation))
        box.addView(row)
        box.addView(text(c, explanation.substringBefore('\n'), 13f, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(c, 12) })
        box.addView(View(c).apply { setBackgroundColor(c.getColor(R.color.tr_console_border)) }, LinearLayout.LayoutParams(dp(c, 56), dp(c, 2)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(c, 14) })
        card.addView(box); parent.addView(card)
        return box
    }
    fun info(c: Context, title: String, explanation: String) = ImageButton(c).apply {
        setImageResource(R.drawable.ic_help); imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_text_secondary))
        setBackgroundResource(android.R.drawable.list_selector_background)
        contentDescription = t(c, "Объяснение: ", "Explicație: ") + title
        layoutParams = LinearLayout.LayoutParams(dp(c, 48), dp(c, 48))
        setOnClickListener { MaterialAlertDialogBuilder(c).setTitle(title).setMessage(explanation).setPositiveButton(R.string.got_it, null).show() }
    }
    fun button(c: Context, parent: LinearLayout, label: String, action: () -> Unit) = MaterialButton(c).apply {
        text = label; isAllCaps = false; gravity = Gravity.CENTER; minHeight = dp(c, if (wide(c)) 64 else 52); cornerRadius = dp(c, 14)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setOnClickListener { action() }; parent.addView(this)
    }
    fun toggle(c: Context, parent: LinearLayout, title: String, explanation: String, checked: Boolean, change: (Boolean) -> Unit) {
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        val sw = MaterialSwitch(c).apply { text = title; textSize = if (wide(c)) 17f else 15f; minHeight = dp(c, if (wide(c)) 64 else 52); isChecked = checked; setOnCheckedChangeListener { _, value -> change(value) } }
        row.addView(sw, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(info(c, title, explanation)); parent.addView(row)
    }
    fun field(c: Context, parent: LinearLayout, title: String, value: String, decimal: Boolean = true): EditText {
        val outline = com.google.android.material.textfield.TextInputLayout(c).apply {
            hint = title
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(c, 14).toFloat(), dp(c, 14).toFloat(), dp(c, 14).toFloat(), dp(c, 14).toFloat())
            boxStrokeColor = c.getColor(R.color.tr_cyan)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(c, 8); bottomMargin = dp(c, 12) }
        }
        val field = com.google.android.material.textfield.TextInputEditText(outline.context).apply {
            setText(value); textSize = if (wide(c)) 18f else 16f; minHeight = dp(c, if (wide(c)) 64 else 56); isSingleLine = true
            inputType = if (decimal) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        outline.addView(field); parent.addView(outline)
        return field
    }
    /**
     * Свёрнутая инструкция «Как это работает»: по нажатию раскрываются пронумерованные шаги.
     * Шаги — строки «кружок с номером + текст», так их легче читать, чем один абзац.
     */
    fun guide(c: Context, parent: LinearLayout, title: String, steps: List<String>, footer: String? = null) {
        val card = MaterialCardView(c).apply {
            radius = dp(c, 22).toFloat(); strokeWidth = dp(c, 1); strokeColor = c.getColor(R.color.tr_accent)
            setCardBackgroundColor(c.getColor(R.color.tr_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 14) }
        }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6)) }
        val head = LinearLayout(c).apply { gravity = Gravity.CENTER; minimumHeight = dp(c, 52); setBackgroundResource(android.R.drawable.list_selector_background); isClickable = true; isFocusable = true }
        head.addView(ImageView(c).apply { setImageResource(R.drawable.ic_help); imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_accent)); contentDescription = null }, LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)).apply { marginEnd = dp(c, 10) })
        val label = text(c, "$title  ▾", 16f).apply { setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(-2, -2) }
        head.addView(label)
        box.addView(head)
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(0, dp(c, 4), 0, dp(c, 10)) }
        steps.forEachIndexed { i, s ->
            val row = LinearLayout(c).apply { setPadding(0, dp(c, 6), 0, dp(c, 6)) }
            row.addView(TextView(c).apply {
                text = "${i + 1}"; textSize = 14f; gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
                setTextColor(c.getColor(R.color.tr_on_accent))
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c.getColor(R.color.tr_accent)) }
            }, LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)).apply { marginEnd = dp(c, 12) })
            row.addView(TextView(c).apply {
                text = s; textSize = 15f; setTextColor(c.getColor(R.color.tr_text)); setLineSpacing(dp(c, 2).toFloat(), 1f)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row)
        }
        if (footer != null) list.addView(text(c, footer, 13f, true).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(c, 8) })
        box.addView(list)
        head.setOnClickListener { val open = list.visibility != View.VISIBLE; list.visibility = if (open) View.VISIBLE else View.GONE; label.text = "$title  ${if (open) "▴" else "▾"}" }
        card.addView(box); parent.addView(card)
    }
    fun expandable(c: Context, parent: LinearLayout, title: String, body: String, icon: Int = R.drawable.ic_help) {
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER; minimumHeight = dp(c, 52); setBackgroundResource(android.R.drawable.list_selector_background); isFocusable = true; isClickable = true }
        row.addView(ImageView(c).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_accent)); contentDescription = null }, LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)).apply { marginEnd = dp(c, 10) })
        val label = text(c, "$title  ▾").apply { layoutParams = LinearLayout.LayoutParams(-2, -2) }
        row.addView(label)
        val details = text(c, body, 14f, true).apply { visibility = View.GONE; setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 6)) }
        row.setOnClickListener { val open = details.visibility != View.VISIBLE; details.visibility = if (open) View.VISIBLE else View.GONE; label.text = "$title  ${if (open) "▴" else "▾"}" }
        parent.addView(row); parent.addView(details)
    }
}
