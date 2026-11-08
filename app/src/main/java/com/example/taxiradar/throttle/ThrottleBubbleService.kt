package com.example.taxiradar.throttle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.taxiradar.MainActivity
import com.example.taxiradar.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Плавающая овальная кнопка «Отмена»: тёмная с бирюзовой обводкой,
 * во время отмены — фиолетовая с крутилкой и «ОТМЕНЯЮ»,
 * в паузе — зелёная «ПАУЗА». Размер настраивается ползунком на вкладке.
 */
class ThrottleBubbleService : Service() {

    companion object {
        private const val CHANNEL_ID = "throttle_bubble"
        private const val LICENSE_CHANNEL_ID = "throttle_license"
        private const val NOTIF_ID = 2
        private const val LICENSE_NOTIF_ID = 3
        // Во время отмены «Стоп» / крестик — пауза на минуту, потом скрипт включается сам.
        private const val PAUSE_ORDER_MS = 60_000L
        // Заказа нет: «Стоп» держится, пока водитель сам не нажмёт «Старт» (страховка — сутки).
        private const val PAUSE_IDLE_MS = 24 * 3_600_000L
        private const val CHECK_MS = 60_000L
        // Без заказов столько — «Отмена» выключается сама.
        private const val AUTO_OFF_MS = 3 * 3_600_000L

        const val UI_PREFS = "throttle_ui"
        const val KEY_SCALE = "bubble_scale"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun scale(context: Context): Float =
            context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE).getFloat(KEY_SCALE, 1f)

        fun setScale(context: Context, value: Float) {
            context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_SCALE, value).apply()
        }

        fun start(context: Context) {
            context.startService(Intent(context, ThrottleBubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ThrottleBubbleService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var spinner: ProgressBar? = null
    private var label: TextView? = null
    private var icon: ImageView? = null
    private var cancelCross: TextView? = null
    private var lastDrawnState = -1
    private var background: GradientDrawable? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scale = 1f
    private var warnedSoon = false
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private val ticker = object : Runnable {
        override fun run() {
            if (ThrottleAuto.pausedUntil > 0L && !ThrottleAuto.isPaused()) {
                ThrottleAuto.resume(this@ThrottleBubbleService)
            }
            updateBubble()
            handler.postDelayed(this, 500)
        }
    }

    private val licenseCheck = object : Runnable {
        override fun run() {
            checkLicense()
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        ThrottleState.ensureLoaded(applicationContext)
        ThrottleAuto.ensureLoaded(applicationContext)
        scale = scale(applicationContext).coerceIn(0.6f, 1.4f)
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
        showBubble()
        handler.post(ticker)
        handler.postDelayed(licenseCheck, CHECK_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Систему перезапустила службу после убийства процесса (intent == null): «Отмена» выключена, кнопка не нужна.
        if (intent == null && !ThrottleAuto.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * scale * resources.displayMetrics.density).roundToInt()

    private fun showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(18), 0)
        }

        val bg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            Color.parseColor("#1B3049"),
            Color.parseColor("#0E1926"),
        )).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(30).toFloat()
            setStroke(dp(2), Color.parseColor("#2DD4BF"))
        }
        background = bg
        pill.background = bg

        val iconView = ImageView(this).apply {
            setImageResource(R.drawable.ic_throttle_bolt)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#2DD4BF"))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) }
        }

        val text = TextView(this).apply {
            text = "СТОП"
            setTextColor(Color.parseColor("#E6F7F5"))
            textSize = 13f * scale
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }

        pill.addView(iconView)
        pill.addView(progress)
        pill.addView(text)

        // Маленький крестик под кнопкой — только пока идёт отмена: «отменить отмену» и взять заказ.
        val cross = TextView(this).apply {
            this.text = "✕"
            gravity = Gravity.CENTER
            textSize = 13f * scale
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DC2626"))
                setStroke(dp(2), Color.WHITE)
            }
            visibility = View.GONE
            setOnClickListener {
                // Взяли заказ: интернет возвращается, через минуту скрипт включится сам.
                ThrottleAuto.pause(this@ThrottleBubbleService, PAUSE_ORDER_MS, fromOrder = true)
                visibility = View.GONE
                updateBubble()
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(60)))
            addView(cross, LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = -dp(17) })
        }
        cancelCross = cross

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(190)
            y = resources.displayMetrics.heightPixels / 2
        }

        pill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    downAt = SystemClock.elapsedRealtime()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { windowManager?.updateViewLayout(root, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        if (SystemClock.elapsedRealtime() - downAt > 700) {
                            // Долгое нажатие — приложение на вкладке «Отмена».
                            startActivity(
                                Intent(this@ThrottleBubbleService, MainActivity::class.java)
                                    .putExtra("open_tab", "throttle")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        } else {
                            onBubbleTap()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        lastDrawnState = -1
        runCatching { windowManager?.addView(root, lp) }
        bubble = root
        spinner = progress
        label = text
        icon = iconView
        params = lp
        updateBubble()
    }

    /**
     * Тап. Идёт отмена (пришёл заказ) — пауза на минуту, потом скрипт включается сам.
     * Заказа нет — «Стоп» держится до нажатия «Старт». На паузе тап возвращает скрипт сразу.
     */
    private fun onBubbleTap() {
        if (ThrottleAuto.isPaused()) {
            ThrottleAuto.resume(this)
        } else {
            val burst = ThrottleState.isRunning.value && ThrottleState.endsAtElapsedRealtime.value > SystemClock.elapsedRealtime()
            ThrottleAuto.pause(this, if (burst) PAUSE_ORDER_MS else PAUSE_IDLE_MS, fromOrder = burst)
        }
        updateBubble()
    }

    /** Раз в минуту проверяем подписку «Отмена»: закончилась — выключаем всё. */
    private fun checkLicense() {
        scope.launch {
            // Спрашиваем сервер (обновит срок по подписанному ответу). Нет связи — решает серверное время,
            // которое мы уже знаем: пока подписка по нему идёт, работаем, закончилась — выключаем.
            CancelLicense.status(applicationContext, running = ThrottleAuto.enabled, runs = ThrottleAuto.runsTotal(applicationContext))
            val left = CancelLicense.msLeft(applicationContext)
            if (left > 0) {
                if (!warnedSoon && left in 1..30 * 60_000L) {
                    warnedSoon = true
                    handler.post { notifyLicenseSoon() }
                }
                // Долго нет заказов — выключаем, чтобы не работала вхолостую; водителю скажем уведомлением.
                if (ThrottleAuto.enabled && ThrottleAuto.idleMs() >= AUTO_OFF_MS) {
                    handler.post {
                        ThrottleAuto.setEnabled(applicationContext, false)
                        CancelNotices.autoOff(applicationContext)
                        stopSelf()
                    }
                }
                return@launch
            }
            handler.post {
                if (!ThrottleAuto.enabled) return@post
                ThrottleAuto.setEnabled(applicationContext, false)
                notifyLicenseExpired()
                stopSelf()
            }
        }
    }

    /** «Месяц сейчас — 33 лея в день» — продажа в момент, когда «Отмена» нужна (цена самого длинного тарифа с сервера). */
    private fun offerText(): String {
        val best = com.example.taxiradar.AppConfig.load(this).cancelTariffs.maxByOrNull { it.days }
        val perDay = if (best != null) Math.round(best.price.toDouble() / best.days).toInt() else 33
        return getString(R.string.throttle_license_offer, perDay)
    }

    private fun notifyLicenseSoon() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.example.taxiradar.SubscriptionActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, LICENSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_throttle)
            .setContentTitle(getString(R.string.throttle_license_soon_title))
            .setContentText(offerText())
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(offerText()))
            .addAction(0, getString(R.string.throttle_license_buy), open)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        (getSystemService(NotificationManager::class.java)).notify(LICENSE_NOTIF_ID, notification)
    }

    private fun notifyLicenseExpired() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, com.example.taxiradar.SubscriptionActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, LICENSE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_throttle)
            .setContentTitle(getString(R.string.throttle_license_expired_title))
            .setContentText(offerText())
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(offerText()))
            .addAction(0, getString(R.string.throttle_license_buy), open)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        (getSystemService(NotificationManager::class.java)).notify(LICENSE_NOTIF_ID, notification)
    }

    private fun updateBubble() {
        val progress = spinner ?: return
        val text = label ?: return
        val bg = background ?: return
        val iconView = icon ?: return

        val now = SystemClock.elapsedRealtime()
        val endsAt = ThrottleState.endsAtElapsedRealtime.value
        val active = ThrottleState.isRunning.value && endsAt > now
        val paused = ThrottleAuto.isPaused()

        // Перерисовываем кнопку только при смене вида: раньше каждые полсекунды заново красились фон и значок,
        // и поток отрисовки поверх Яндекса работал впустую.
        val state = if (active) 1 else if (paused) 2 else 0
        // Идёт отмена: вместо крутящегося индикатора (60 кадров в секунду) — «ОТМЕНЯЮ» и мигающая точка,
        // раз в секунду. Текст одной длины, окно не пересчитывается; секунды не показываем — лишняя перерисовка.
        val activeLabel = if (active) (if ((now / 1000) % 2L == 0L) "● ОТМЕНЯЮ" else "○ ОТМЕНЯЮ") else ""
        if (state == lastDrawnState) {
            if (active) text.text = activeLabel
            return
        }
        lastDrawnState = state

        cancelCross?.visibility = if (active) View.VISIBLE else View.GONE
        when {
            active -> {
                progress.visibility = View.GONE
                iconView.visibility = View.GONE
                text.visibility = View.VISIBLE
                text.text = activeLabel
                text.setTextColor(Color.WHITE)
                bg.colors = intArrayOf(
                    Color.parseColor("#7C3AED"),
                    Color.parseColor("#C084FC"),
                )
                bg.setStroke(dp(2), Color.parseColor("#E9D5FF"))
            }
            paused -> {
                progress.visibility = View.GONE
                iconView.visibility = View.VISIBLE
                iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#0B2B1E"))
                text.visibility = View.VISIBLE
                text.text = "СТАРТ"
                text.setTextColor(Color.parseColor("#0B2B1E"))
                bg.colors = intArrayOf(
                    Color.parseColor("#4ADE80"),
                    Color.parseColor("#22C55E"),
                )
                bg.setStroke(dp(2), Color.parseColor("#BBF7D0"))
            }
            else -> {
                progress.visibility = View.GONE
                iconView.visibility = View.VISIBLE
                iconView.imageTintList = ColorStateList.valueOf(Color.parseColor("#2DD4BF"))
                text.visibility = View.VISIBLE
                text.text = "СТОП"
                text.setTextColor(Color.parseColor("#E6F7F5"))
                bg.colors = intArrayOf(
                    Color.parseColor("#1B3049"),
                    Color.parseColor("#0E1926"),
                )
                bg.setStroke(dp(2), Color.parseColor("#2DD4BF"))
            }
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_throttle)
            .setContentTitle(getString(R.string.throttle_bubble))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.throttle_bubble), NotificationManager.IMPORTANCE_MIN),
        )
        manager.createNotificationChannel(
            NotificationChannel(LICENSE_CHANNEL_ID, getString(R.string.throttle_license_channel), NotificationManager.IMPORTANCE_HIGH),
        )
    }
}
