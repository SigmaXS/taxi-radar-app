package com.example.taxiradar

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

/**
 * «Как это работает»: коротко про каждую функцию радара. Нажатие на пункт
 * раскрывает объяснение.
 */
class HelpActivity : AppCompatActivity() {

    private data class Topic(val icon: Int, val title: Int, val text: Int)

    private val topics = listOf(
        Topic(R.drawable.ic_nav_radar, R.string.help_radar_title, R.string.help_radar_text),
        Topic(R.drawable.ic_map, R.string.help_surge_title, R.string.help_surge_text),
        Topic(R.drawable.ic_check_circle, R.string.help_net_title, R.string.help_net_text),
        Topic(R.drawable.ic_nav_radar, R.string.help_traffic_title, R.string.help_traffic_text),
        Topic(R.drawable.ic_map, R.string.help_map_title, R.string.help_map_text),
        Topic(R.drawable.ic_person_search, R.string.help_clients_title, R.string.help_clients_text),
        Topic(R.drawable.ic_flight, R.string.help_airport_title, R.string.help_airport_text),
        Topic(R.drawable.ic_forum, R.string.help_chat_title, R.string.help_chat_text),
        Topic(R.drawable.ic_my_location, R.string.help_addresses_title, R.string.help_addresses_text),
        Topic(R.drawable.ic_share, R.string.help_referral_title, R.string.help_referral_text),
        Topic(R.drawable.ic_open_in_new, R.string.help_key_title, R.string.help_key_text),
        Topic(R.drawable.ic_checklist, R.string.help_setup_title, R.string.help_setup_text),
        Topic(R.drawable.ic_group, R.string.help_group_title, R.string.help_group_text)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        findViewById<View>(R.id.btnHelpBack).setOnClickListener { finish() }
        val list = findViewById<LinearLayout>(R.id.layoutHelpList)
        val dp = resources.displayMetrics.density
        for (t in topics) {
            val text = TextView(this).apply {
                setText(t.text)
                setTextColor(getColor(R.color.tr_text_secondary))
                textSize = 14.5f
                setLineSpacing(3 * dp, 1f)
                setPadding(0, (10 * dp).toInt(), 0, 0)
                visibility = View.GONE
            }
            val arrow = ImageView(this).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(getColor(R.color.tr_text_muted))
                rotation = 90f
            }
            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(this@HelpActivity).apply {
                    setImageResource(t.icon)
                    setBackgroundResource(R.drawable.bg_status_dot)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.tr_accent_container))
                    setColorFilter(getColor(R.color.tr_accent))
                    val p = (8 * dp).toInt()
                    setPadding(p, p, p, p)
                }, LinearLayout.LayoutParams((38 * dp).toInt(), (38 * dp).toInt()))
                addView(TextView(this@HelpActivity).apply {
                    setText(t.title)
                    setTextColor(getColor(R.color.tr_text))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding((12 * dp).toInt(), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(arrow)
            }
            val body = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * dp).toInt()
                setPadding(p, p, p, p)
                addView(header)
                addView(text)
            }
            val card = MaterialCardView(this).apply {
                setCardBackgroundColor(getColor(R.color.tr_surface))
                radius = 18 * dp
                strokeColor = getColor(R.color.tr_stroke)
                strokeWidth = dp.toInt()
                cardElevation = 0f
                addView(body)
                setOnClickListener {
                    val open = text.visibility != View.VISIBLE
                    text.visibility = if (open) View.VISIBLE else View.GONE
                    arrow.rotation = if (open) 270f else 90f
                }
            }
            list.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).toInt() })
        }
    }
}
