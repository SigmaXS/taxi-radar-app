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
    fun t(c: Context, ru: String, ro: String) = if (c.resources.configuration.locales[0].language == "ro") ro else ru
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun text(c: Context, label: String, size: Float = 15f, muted: Boolean = false) = TextView(c).apply {
        text = label; textSize = size; setTextColor(c.getColor(if (muted) R.color.tr_text_secondary else R.color.tr_text))
        setLineSpacing(dp(c, 3).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 8) }
    }
    fun card(c: Context, parent: LinearLayout, title: String, explanation: String, icon: Int = R.drawable.ic_help): LinearLayout {
        val card = MaterialCardView(c).apply {
            radius = dp(c, 22).toFloat(); strokeWidth = dp(c, 1); strokeColor = c.getColor(R.color.tr_stroke)
            setCardBackgroundColor(c.getColor(R.color.tr_surface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 14) }
        }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 14)) }
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(c).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_accent)); contentDescription = null }, LinearLayout.LayoutParams(dp(c, 26), dp(c, 26)).apply { marginEnd = dp(c, 10) })
        row.addView(text(c, title, 18f).apply { setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        row.addView(info(c, title, explanation))
        box.addView(row)
        box.addView(text(c, explanation.substringBefore('\n'), 13f, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
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
        text = label; isAllCaps = false; minHeight = dp(c, 52); cornerRadius = dp(c, 14)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setOnClickListener { action() }; parent.addView(this)
    }
    fun toggle(c: Context, parent: LinearLayout, title: String, explanation: String, checked: Boolean, change: (Boolean) -> Unit) {
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL }
        val sw = MaterialSwitch(c).apply { text = title; textSize = 15f; minHeight = dp(c, 52); isChecked = checked; setOnCheckedChangeListener { _, value -> change(value) } }
        row.addView(sw, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(info(c, title, explanation)); parent.addView(row)
    }
    fun field(c: Context, parent: LinearLayout, title: String, value: String, decimal: Boolean = true): EditText {
        parent.addView(text(c, title, 13f, true))
        return EditText(c).apply {
            setText(value); textSize = 16f; minHeight = dp(c, 48); isSingleLine = true
            inputType = if (decimal) android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(c, 12) }
            parent.addView(this)
        }
    }
    fun expandable(c: Context, parent: LinearLayout, title: String, body: String, icon: Int = R.drawable.ic_help) {
        val row = LinearLayout(c).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(c, 52); setBackgroundResource(android.R.drawable.list_selector_background); isFocusable = true; isClickable = true }
        row.addView(ImageView(c).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(c.getColor(R.color.tr_accent)); contentDescription = null }, LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)).apply { marginEnd = dp(c, 12) })
        val label = text(c, "$title  ▾").apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        row.addView(label)
        val details = text(c, body, 14f, true).apply { visibility = View.GONE }
        row.setOnClickListener { val open = details.visibility != View.VISIBLE; details.visibility = if (open) View.VISIBLE else View.GONE; label.text = "$title  ${if (open) "▴" else "▾"}" }
        parent.addView(row); parent.addView(details)
    }
}
