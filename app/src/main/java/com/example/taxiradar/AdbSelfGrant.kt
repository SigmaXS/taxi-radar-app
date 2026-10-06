package com.example.taxiradar

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Включение без компьютера на мультимедиа машин (BYD DiLink и похожие): если в
 * «Для разработчиков» включена отладка по сети, adbd слушает 127.0.0.1:5555 на
 * самом устройстве. Подключаемся к нему своим ключом (водитель один раз нажимает
 * «Разрешить отладку»), выдаём себе WRITE_SECURE_SETTINGS — и дальше радар сам
 * включает свою службу (AccessibilityAccess.enableSelf).
 *
 * Выполняется только одна жёстко заданная команда и только для нашего пакета.
 */
object AdbSelfGrant {

    private const val TAG = "ADB_SELF"
    private const val PORT = 5555

    enum class Result { GRANTED, NO_ADB, NOT_ALLOWED, FAILED }

    private fun keyPair(context: Context): AdbKeyPair {
        val dir = File(context.filesDir, "adb_keys").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /** Ждёт до 60 с, пока водитель нажмёт «Разрешить» на экране устройства. */
    suspend fun grant(context: Context): Result = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val keys = try {
            keyPair(context)
        } catch (e: Exception) {
            Log.e(TAG, "ключи: ${e.message}")
            return@withContext Result.FAILED
        }
        val outcome = withTimeoutOrNull(75_000) {
            try {
                Dadb.create("127.0.0.1", PORT, keys).use { adb ->
                    val r = adb.shell("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
                    Log.d(TAG, "pm grant: exit=${r.exitCode} ${r.allOutput.take(200)}")
                    if (r.exitCode == 0) Result.GRANTED else Result.FAILED
                }
            } catch (e: java.net.ConnectException) {
                Result.NO_ADB
            } catch (e: Exception) {
                Log.e(TAG, "adb: ${e.javaClass.simpleName} ${e.message}")
                // Порт ответил, но ключ не приняли (нажали «Отмена» или не успели).
                if (e is java.io.IOException && e.message?.contains("refused", true) == true) Result.NO_ADB else Result.NOT_ALLOWED
            }
        } ?: Result.NOT_ALLOWED
        if (outcome == Result.GRANTED && !AccessibilityAccess.canEnableSelf(context)) Result.FAILED else outcome
    }
}
