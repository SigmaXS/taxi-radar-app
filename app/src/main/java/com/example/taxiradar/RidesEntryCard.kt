package com.example.taxiradar

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/** Плитка «Попутчики» на вкладке «Полезное»: открывает ленту заявок. Сама вешает обработчик нажатия. */
class RidesEntryCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {
    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { context.startActivity(Intent(context, RidesActivity::class.java)) }
    }
}
