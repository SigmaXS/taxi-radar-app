package com.example.taxiradar

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Телефон-разведчик между кругами: чёрный экран с минимальной подсветкой.
 * Go уходит в фон и не рисует карту — телефон меньше греется. Нажатие закрывает экран.
 */
class ScoutRestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        window.attributes = window.attributes.apply { screenBrightness = 0.01f }
        setContentView(TextView(this).apply {
            text = "🛰 Разведчик ждёт точки"
            setTextColor(Color.rgb(60, 60, 60)); setBackgroundColor(Color.BLACK); textSize = 14f
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        })
    }
}
