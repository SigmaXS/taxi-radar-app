package com.example.taxiradar

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File

/**
 * Приложение упало — записываем, где именно, в файл; при следующем запуске
 * отправляем на сервер (/api/crash → админка «Ошибки приложения»). Без личных
 * данных: версия, модель, Android и стек ошибки.
 */
object CrashReporter {
    @Volatile private var installed = false
    private fun file(c: Context) = File(c.filesDir, "last_crash.json")

    /** Вызывать в onCreate активити и служб: процесс может стартовать с любой из них. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
                file(app).writeText(JSONObject()
                    .put("version", version)
                    .put("model", "${Build.MANUFACTURER} ${Build.MODEL}".take(80))
                    .put("android", Build.VERSION.RELEASE)
                    .put("stack", e.stackTraceToString().take(6000))
                    .toString())
            } catch (_: Exception) {}
            previous?.uncaughtException(thread, e)
        }
    }

    /** Есть отчёт с прошлого раза — отправить и удалить. */
    fun sendPending(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        Thread {
            try {
                val body = JSONObject(f.readText())
                val r = CommunityApi.postBlocking(context.applicationContext, "/api/crash", body)
                // Сервер принял или отчёт битый — больше не шлём.
                if (r?.optBoolean("ok") == true || !body.has("stack")) f.delete()
            } catch (_: Exception) {
                f.delete()
            }
        }.start()
    }
}
