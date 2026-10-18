package com.example.taxiradar

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Журнал радара»: последние события поездок и смены (начало, конец, что было на экране),
 * чтобы водитель мог отправить его нам без компьютера — особенно с мультимедиа машины.
 */
object TripLog {
    private const val MAX = 1600
    private const val MAX_CHARS = 512_000
    private var file: File? = null
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)

    @Synchronized fun init(c: Context) {
        if (file != null) return
        file = File(c.applicationContext.filesDir, "trip_log.txt").also { f ->
            runCatching { if (f.exists()) f.readLines().takeLast(MAX).forEach { lines.addLast(it) } }
        }
    }

    @Synchronized fun d(msg: String) {
        Log.d("TRIP", msg)
        lines.addLast("${fmt.format(Date())}  $msg")
        while (lines.size > MAX || (lines.size > 1 && lines.sumOf { it.length } > MAX_CHARS)) lines.removeFirst()
        runCatching { file?.writeText(lines.joinToString("\n")) }
    }

    @Synchronized fun text(c: Context): String {
        init(c)
        return lines.joinToString("\n")
    }

    fun share(a: android.app.Activity) {
        val dir = File(a.cacheDir, "diagnostics").apply { mkdirs() }
        val snapshot = "Taxi Radar ${a.packageManager.getPackageInfo(a.packageName, 0).versionName}\n" +
            "Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
            "Accessibility=${OrderAccessibilityService.isConnected}; OCR=${ScreenReaderService.running}; widget=${FloatingWidgetService.isRunning}\n\n" + text(a)
        // One bounded export, no accumulating copies of passengers' addresses.
        val out = File(dir, "TaxiRadar-diagnostics.txt").apply { writeText(snapshot, Charsets.UTF_8) }
        val uri = androidx.core.content.FileProvider.getUriForFile(a, "${a.packageName}.updates", out)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .putExtra(android.content.Intent.EXTRA_SUBJECT, "Taxi Radar — диагностика Bolt")
            .apply { clipData = android.content.ClipData.newRawUri("diagnostics", uri) }
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        a.startActivity(android.content.Intent.createChooser(intent, DriverUi.t(a, "Отправить журнал .txt", "Trimite jurnalul .txt")))
    }
}
