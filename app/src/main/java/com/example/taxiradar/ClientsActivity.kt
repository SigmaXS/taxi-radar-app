package com.example.taxiradar

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * «Клиенты»: проверить любой номер и отметить клиента. Номер сам попадает в
 * «Последние клиенты», когда водитель звонит клиенту из Яндекс Про.
 */
class ClientsActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var tvResult: TextView
    private lateinit var btnTag: MaterialButton
    private var checkedNumber: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clients)
        etPhone = findViewById(R.id.etClientPhone)
        tvResult = findViewById(R.id.tvClientResult)
        btnTag = findViewById(R.id.btnClientTag)

        findViewById<View>(R.id.btnClientsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnClientCheck).setOnClickListener { checkTyped() }
        btnTag.setOnClickListener { checkedNumber?.let { showTagDialog(it) } }

        // Открыли из уведомления о звонке — сразу этот клиент.
        intent.getStringExtra("number")?.let {
            etPhone.setText(PhoneNumbers.pretty(it))
            checkTyped()
        }
    }

    override fun onResume() {
        super.onResume()
        renderRecent()
    }

    private fun checkTyped() {
        val number = PhoneNumbers.normalize(etPhone.text.toString())
        if (number == null) {
            Toast.makeText(this, getString(R.string.clients_bad_number), Toast.LENGTH_SHORT).show()
            return
        }
        checkedNumber = number
        tvResult.visibility = View.VISIBLE
        tvResult.text = getString(R.string.setup_checking)
        lifecycleScope.launch {
            val s = ClientsManager.check(this@ClientsActivity, number)
            tvResult.text = buildString {
                append(PhoneNumbers.pretty(number)).append("\n").append(ClientsManager.describe(this@ClientsActivity, s))
                if (s != null && s.reviews.isNotEmpty()) append("\n\n").append(ClientsManager.describeReviews(this@ClientsActivity, s))
            }
            // Жалобы — красным, «всё ок» — зелёным, пусто — нейтрально.
            val bad = s?.tags?.keys?.any { it in ClientsManager.NEGATIVE } == true
            val good = s != null && !bad && s.tags.isNotEmpty()
            tvResult.setBackgroundResource(R.drawable.bg_bubble)
            tvResult.backgroundTintList = android.content.res.ColorStateList.valueOf(
                getColor(if (bad) R.color.tr_danger_container else if (good) R.color.tr_success_container else R.color.tr_surface_high)
            )
            val pad = (14 * resources.displayMetrics.density).toInt()
            tvResult.setPadding(pad, pad, pad, pad)
            btnTag.visibility = if (s != null) View.VISIBLE else View.GONE
        }
    }

    private fun renderRecent() {
        val container = findViewById<LinearLayout>(R.id.layoutRecentClients)
        container.removeAllViews()
        val recent = ClientsManager.recent(this)
        findViewById<View>(R.id.tvNoRecent).visibility = if (recent.isEmpty()) View.VISIBLE else View.GONE
        val pad = (14 * resources.displayMetrics.density).toInt()
        for (r in recent) {
            val card = MaterialCardView(this, null, com.google.android.material.R.attr.materialCardViewFilledStyle).apply {
                setCardBackgroundColor(getColor(R.color.tr_surface))
                radius = 16 * resources.displayMetrics.density
                strokeColor = getColor(R.color.tr_stroke)
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = pad / 2 }
                setOnClickListener { showTagDialog(r.number) }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
            }
            row.addView(TextView(this).apply {
                text = PhoneNumbers.pretty(r.number)
                setTextColor(getColor(R.color.tr_text))
                textSize = 16f
            })
            row.addView(TextView(this).apply {
                text = DateUtils.getRelativeTimeSpanString(r.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                setTextColor(getColor(R.color.tr_text_secondary))
                textSize = 13f
            })
            card.addView(row)
            container.addView(card)
        }
    }

    /** Что знают о клиенте другие водители + мои отметки галочками. */
    private fun showTagDialog(number: String) {
        lifecycleScope.launch {
            val summary = ClientsManager.check(this@ClientsActivity, number)
            if (summary == null) {
                Toast.makeText(this@ClientsActivity, getString(R.string.clients_no_connection), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val pad = (22 * resources.displayMetrics.density).toInt()
            val box = LinearLayout(this@ClientsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad / 3, pad, 0)
            }
            box.addView(TextView(this@ClientsActivity).apply {
                text = ClientsManager.describe(this@ClientsActivity, summary) +
                        (if (summary.reviews.isNotEmpty()) "\n\n" + ClientsManager.describeReviews(this@ClientsActivity, summary) else "")
                setTextColor(getColor(R.color.tr_text_secondary))
                textSize = 14f
                setPadding(0, 0, 0, pad / 2)
            })
            val boxes = ClientsManager.TAGS.map { (tag, label) ->
                tag to CheckBox(this@ClientsActivity).apply {
                    text = getString(label)
                    isChecked = tag in summary.mine
                    box.addView(this)
                }
            }
            // Свой отзыв — своими словами, до 200 символов.
            val etReview = EditText(this@ClientsActivity).apply {
                hint = getString(R.string.clients_review_hint)
                setText(summary.myReview.orEmpty())
                filters = arrayOf(android.text.InputFilter.LengthFilter(200))
                minLines = 2
                maxLines = 5
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            box.addView(TextView(this@ClientsActivity).apply {
                setText(R.string.clients_review_title)
                setTextColor(getColor(R.color.tr_text))
                textSize = 15f
                setPadding(0, pad / 2, 0, 0)
            })
            box.addView(etReview)
            box.addView(TextView(this@ClientsActivity).apply {
                setText(R.string.clients_review_rules)
                setTextColor(getColor(R.color.tr_text_muted))
                textSize = 12f
            })
            MaterialAlertDialogBuilder(this@ClientsActivity)
                .setTitle(PhoneNumbers.pretty(number))
                .setView(android.widget.ScrollView(this@ClientsActivity).apply { addView(box) })
                .setPositiveButton(R.string.clients_save) { _, _ ->
                    lifecycleScope.launch {
                        var ok = true
                        for ((tag, cb) in boxes) {
                            val was = tag in summary.mine
                            if (cb.isChecked != was) ok = ClientsManager.tag(this@ClientsActivity, number, tag, cb.isChecked) != null && ok
                        }
                        val review = etReview.text.toString().trim()
                        if (review != summary.myReview.orEmpty()) {
                            ok = ClientsManager.review(this@ClientsActivity, number, review) != null && ok
                        }
                        Toast.makeText(
                            this@ClientsActivity,
                            getString(if (ok) R.string.clients_saved else R.string.clients_no_connection),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (number == checkedNumber) checkTyped()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
