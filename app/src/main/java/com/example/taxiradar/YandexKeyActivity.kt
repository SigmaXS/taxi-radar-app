package com.example.taxiradar

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Кабинет Яндекса прямо в приложении. Водитель сам входит в свой Яндекс ID и
 * создаёт ключ, а мы только читаем со страницы строки вида ключа (UUID) —
 * без копирования и вставки. Пароль и прочее со страницы не читаем.
 *
 * Ключ проверяем настоящим запросом к Геокодеру. Только что созданный ключ
 * Яндекс включает через 15–30 минут — такие откладываем в «ожидающие»,
 * и главный экран подключит их сам, когда заработают.
 */
class YandexKeyActivity : AppCompatActivity() {

    companion object {
        // Сразу вкладка «Ключи»: на главной кабинета самих ключей не видно.
        const val CONSOLE_URL = "https://yandex.ru/maps-api/console/keys"
        private const val POLL_MS = 1500L

        /**
         * Геокодер у аккаунта подключён, а ключа для него нет — создаём сами:
         * «Новый ключ» → продукт «API Геокодера» → название → «Создать ключ».
         * Ход сообщаем в приложение через TRKey.onStep. Если Геокодер ещё не
         * подключён (в списке продуктов его нет) — закрываем окно и просим
         * водителя подключить его: там соглашение с Яндексом, это его решение.
         */
        private const val CREATE_KEY_JS = """
            (async function () {
              var sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
              var report = function (s) { try { TRKey.onStep(s); } catch (e) {} };
              var find = function (sel, test) {
                var els = document.querySelectorAll(sel);
                for (var i = 0; i < els.length; i++) { if (test((els[i].innerText || '').trim())) return els[i]; }
                return null;
              };
              var newBtn = find('button', function (t) { return t === 'Новый ключ'; });
              if (!newBtn) { report('no_button'); return; }
              newBtn.click();
              await sleep(1500);
              var combo = find('[role=combobox]', function (t) { return t.indexOf('Выберите продукт') >= 0; });
              if (!combo) { report('no_combo'); return; }
              combo.click();
              await sleep(1000);
              var opt = find('[role=option]', function (t) { return t.indexOf('Геокодер') >= 0; });
              if (!opt) {
                var cancel = find('button', function (t) { return t === 'Отмена'; });
                if (cancel) cancel.click();
                report('no_geocoder');
                return;
              }
              opt.click();
              await sleep(600);
              var name = document.querySelector('input[placeholder="Введите название ключа"]');
              if (name && !name.value) {
                var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                setter.call(name, 'Taxi Radar');
                name.dispatchEvent(new Event('input', { bubbles: true }));
                await sleep(400);
              }
              var create = find('button', function (t) { return t === 'Создать ключ'; });
              if (!create) { report('no_create'); return; }
              create.click();
              report('created');
            })();
        """

        // «Версия для ПК»: Яндекс отдаёт полную страницу кабинета, как на компьютере.
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        // Пункт меню «Ключи» — не ссылка, а элемент с обработчиком; кликаем по тексту.
        private const val CLICK_KEYS_JS = """
            (function () {
              var names = ['Ключи', 'Keys', 'Chei'];
              var els = document.querySelectorAll('#root *');
              for (var i = 0; i < els.length; i++) {
                var e = els[i];
                if (e.children.length === 0 && names.indexOf((e.innerText || '').trim()) >= 0) {
                  e.click();
                  return true;
                }
              }
              return false;
            })();
        """

        private val keyRegex =
            Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

        // Текст страницы плюс значения полей ввода (ключ бывает показан в поле
        // «только для чтения»). Заодно перехватываем кнопку «Копировать»:
        // в WebView запись в буфер часто молча не работает.
        private const val SCAN_JS = """
            (function () {
              if (!window.__trHooked) {
                window.__trHooked = true;
                try {
                  var cb = navigator.clipboard;
                  if (cb && cb.writeText) {
                    var orig = cb.writeText.bind(cb);
                    cb.writeText = function (t) {
                      try { TRKey.onText(String(t)); } catch (e) {}
                      return orig(t).catch(function () {});
                    };
                  }
                } catch (e) {}
                document.addEventListener('copy', function () {
                  try { TRKey.onText(String(window.getSelection())); } catch (e) {}
                }, true);
              }
              var parts = [document.body ? document.body.innerText : ''];
              var fields = document.querySelectorAll('input, textarea');
              for (var i = 0; i < fields.length; i++) {
                if (fields[i].type !== 'password') parts.push(fields[i].value);
              }
              return parts.join('\n');
            })();
        """
    }

    private lateinit var web: WebView
    private lateinit var tvStatus: TextView
    private lateinit var ivStatus: ImageView
    private lateinit var progress: LinearProgressIndicator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    // Ключи в порядке появления на странице: только что созданный — последний.
    private val seen = LinkedHashSet<String>()
    private val rejected = LinkedHashSet<String>()
    private var checking = false
    private var done = false
    private var desktopMode = false
    private lateinit var mobileUserAgent: String

    private val poll = object : Runnable {
        override fun run() {
            scan()
            handler.postDelayed(this, POLL_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yandex_key)

        web = findViewById(R.id.webKey)
        tvStatus = findViewById(R.id.tvKeyStatus)
        ivStatus = findViewById(R.id.ivKeyStatus)
        progress = findViewById(R.id.progressKey)

        findViewById<View>(R.id.btnKeyBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnKeyBrowser).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web.url ?: CONSOLE_URL)))
            } catch (_: Exception) {
            }
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Без пометки «wv» вход в Яндекс ведёт себя как в обычном браузере.
            userAgentString = userAgentString.replace("; wv", "")
            // Кабинет Яндекса свёрстан под компьютер — даём масштабировать пальцами.
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        mobileUserAgent = web.settings.userAgentString

        val btnDesktop = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnKeyDesktop)
        btnDesktop.setOnClickListener {
            desktopMode = !desktopMode
            web.settings.userAgentString = if (desktopMode) DESKTOP_UA else mobileUserAgent
            btnDesktop.setText(if (desktopMode) R.string.keyweb_mobile else R.string.keyweb_desktop)
            web.reload()
        }
        web.addJavascriptInterface(Bridge(), "TRKey")
        // Только в отладочной сборке: страницу можно смотреть через chrome://inspect.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Ссылки вида intent:// (открыть приложение Яндекса) не нужны — остаёмся на странице.
                val scheme = request.url.scheme
                return scheme != "http" && scheme != "https"
            }

            // Полоску загрузки не прячем здесь: кабинет — тяжёлое приложение и после
            // «загрузки» ещё 10–20 секунд рисуется. Прячем, когда на странице появился текст.
            override fun onPageFinished(view: WebView, url: String?) {
                scan()
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!done) progress.visibility = View.VISIBLE
            }
        }
        web.loadUrl(CONSOLE_URL)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        handler.postDelayed(poll, POLL_MS)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        web.removeJavascriptInterface("TRKey")
        web.destroy()
        super.onDestroy()
    }

    /** Ключи ищем только на страницах Яндекса. */
    private fun onYandexPage(): Boolean {
        val host = Uri.parse(web.url ?: return false).host ?: return false
        return listOf("yandex.ru", "yandex.com", "yandex.md", "ya.ru").any {
            host == it || host.endsWith(".$it")
        }
    }

    private fun scan() {
        if (done || !onYandexPage()) return
        web.evaluateJavascript(SCAN_JS) { result ->
            val text = result.orEmpty()
            // Кабинет отрисовался (не только кнопка чата) — загрузка закончена.
            if (text.length > 120) progress.visibility = View.GONE
            maybeOpenKeysTab(text)
            handleText(text)
            maybeCreateKey(text)
        }
    }

    private var noGeocoderScans = 0
    private var createTried = false
    private var geocoderNotConnected = false

    /**
     * На вкладке «Ключи» таблица загрузилась, а ключа Геокодера в ней нет —
     * создаём. Ждём три проверки подряд (~4,5 с): пока таблица грузится,
     * её строки пустые, и спешка создала бы лишний ключ.
     */
    private fun maybeCreateKey(text: String) {
        if (done || createTried || geocoderNotConnected || checking) return
        val onKeysPage = web.url?.contains("/maps-api/console/keys") == true && text.contains("Значение ключа")
        val hasGeocoderKey = text.contains("API Геокодера") || seen.isNotEmpty() ||
                (YandexApiKey.get(this) != null && keyRegex.containsMatchIn(text))
        if (!onKeysPage || hasGeocoderKey) {
            noGeocoderScans = 0
            return
        }
        if (++noGeocoderScans < 3) return
        createTried = true
        setStatus(getString(R.string.keyweb_creating), R.drawable.ic_help, R.color.tr_accent)
        web.evaluateJavascript(CREATE_KEY_JS, null)
    }

    private fun onCreateStep(step: String) {
        when (step) {
            "created" -> setStatus(getString(R.string.keyweb_created), R.drawable.ic_check_circle, R.color.tr_accent)
            "no_geocoder" -> {
                // Геокодер ещё не подключён: это делает водитель сам (там условия
                // Яндекса). Когда подключит, на главной появится «API Геокодера» —
                // тогда вернёмся на «Ключи» и попробуем снова.
                geocoderNotConnected = true
                createTried = false
                keysTabOpened = false
                setStatus(getString(R.string.keyweb_connect_api), R.drawable.ic_help, R.color.tr_warning)
            }
            // Кабинет изменился — не мешаем, водитель создаст ключ сам.
            else -> setStatus(getString(R.string.keyweb_hint), R.drawable.ic_help, R.color.tr_accent)
        }
    }

    private var keysTabOpened = false

    /**
     * На главной кабинета ключей не видно — они на вкладке «Ключи». Если
     * Геокодер уже подключён, а ключа на странице нет, открываем вкладку сами
     * (один раз, чтобы не мешать водителю, если он ушёл на другую вкладку).
     */
    private fun maybeOpenKeysTab(text: String) {
        if (keysTabOpened || keyRegex.containsMatchIn(text)) return
        if (!text.contains("API Геокодера") && !text.contains("Geocoder API")) return
        keysTabOpened = true
        geocoderNotConnected = false
        web.evaluateJavascript(CLICK_KEYS_JS, null)
    }

    private fun handleText(text: String) {
        if (done || !onYandexPage()) return
        // Уже подключённый ключ не берём: сюда пришли за другим (кнопка «Изменить»),
        // значит, водителю нужно войти в другой аккаунт Яндекса.
        val current = YandexApiKey.get(this)?.lowercase()
        val matches = keyRegex.findAll(text).toList()
        matches.forEachIndexed { i, match ->
            val key = match.value.lowercase()
            // В таблице «Ключи» после ключа идёт его продукт. Ключи других
            // продуктов (Tiles API и т.п.) адреса не ищут — пропускаем.
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val after = text.substring(match.range.last + 1, minOf(end, match.range.last + 1 + 80))
            if (after.contains("API") && !after.contains("Геокод") && !after.contains("Geocod")) return@forEachIndexed
            if (key == current) {
                if (seen.none { it != current }) {
                    setStatus(getString(R.string.keyweb_same_key), R.drawable.ic_help, R.color.tr_accent)
                }
            } else {
                seen.add(key)
            }
        }
        checkCandidates()
    }

    private fun checkCandidates() {
        if (checking || done) return
        val candidate = seen.firstOrNull { it !in rejected } ?: return
        checking = true
        setStatus(getString(R.string.keyweb_checking), R.drawable.ic_help, R.color.tr_accent)
        scope.launch {
            val result = RouteFareCalculator.checkYandexKey(candidate)
            checking = false
            when (result) {
                RouteFareCalculator.KeyCheck.OK -> onKeyWorks(candidate)
                RouteFareCalculator.KeyCheck.REJECTED -> {
                    rejected += candidate
                    onKeysNotActiveYet()
                    checkCandidates()
                }
                RouteFareCalculator.KeyCheck.NETWORK_ERROR ->
                    setStatus(getString(R.string.keyweb_no_network), R.drawable.ic_error, R.color.tr_danger)
            }
        }
    }

    private fun onKeyWorks(key: String) {
        done = true
        handler.removeCallbacks(poll)
        YandexApiKey.save(this, key)
        setStatus(getString(R.string.keyweb_ok), R.drawable.ic_check_circle, R.color.tr_success)
        setResult(RESULT_OK)
        handler.postDelayed({ finish() }, 1200)
    }

    /**
     * Ни один найденный ключ пока не работает. Скорее всего, его только что
     * создали — запоминаем все (новые — первыми) и ждём включения.
     */
    private fun onKeysNotActiveYet() {
        if (seen.any { it !in rejected }) return
        YandexApiKey.savePending(this, seen.reversed())
        setStatus(getString(R.string.keyweb_pending), R.drawable.ic_check_circle, R.color.tr_warning)
    }

    private fun setStatus(text: String, icon: Int, color: Int) {
        tvStatus.text = text
        ivStatus.setImageResource(icon)
        ivStatus.imageTintList = ColorStateList.valueOf(getColor(color))
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onText(text: String) {
            runOnUiThread { handleText(text) }
        }

        @JavascriptInterface
        fun onStep(step: String) {
            runOnUiThread { if (onYandexPage()) onCreateStep(step) }
        }
    }
}
