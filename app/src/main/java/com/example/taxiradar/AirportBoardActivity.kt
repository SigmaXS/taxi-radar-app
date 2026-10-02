package com.example.taxiradar

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Официальное табло аэропорта внутри приложения — обычная страница сайта,
 * как в браузере. Нет связи — заглушка с «Повторить».
 */
class AirportBoardActivity : AppCompatActivity() {

    companion object {
        // Чистый адрес табло прилётов: без «?checkin-warn», из-за которого сайт
        // показывает всплывающее предупреждение поверх табло.
        private const val URL = "https://airport.md/ru/passenger/online-panel?today-flights=1#arrivals"
    }

    private lateinit var web: WebView
    private lateinit var errorBox: LinearLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_airport_board)
        web = findViewById(R.id.webBoard)
        errorBox = findViewById(R.id.layoutBoardError)
        val progress = findViewById<LinearProgressIndicator>(R.id.progressBoard)

        findViewById<View>(R.id.btnBoardBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnBoardRetry).setOnClickListener {
            errorBox.visibility = View.GONE
            web.visibility = View.VISIBLE
            web.reload()
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // Ошибка самой страницы (а не картинки или счётчика) — показываем заглушку.
                if (request.isForMainFrame) {
                    web.visibility = View.GONE
                    errorBox.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                }
            }
        }
        web.loadUrl(URL)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
