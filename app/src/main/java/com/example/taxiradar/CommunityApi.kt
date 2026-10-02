package com.example.taxiradar

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Сервер «сообщества» (тот же, что и лицензии): чат, отметки о клиентах,
 * метки на дороге, аэропорт. Все запросы — от имени устройства; сервер
 * пускает только водителей с активной подпиской.
 */
object CommunityApi {

    private const val BASE_URL = "https://taxi-radar-license-production.up.railway.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Ответ сервера или null, если нет связи. */
    suspend fun post(context: Context, path: String, body: JSONObject = JSONObject()): JSONObject? =
        withContext(Dispatchers.IO) { postBlocking(context, path, body) }

    /** То же без корутин — для кода, который уже работает в фоновом потоке. */
    fun postBlocking(context: Context, path: String, body: JSONObject = JSONObject()): JSONObject? =
        try {
            body.put("device_id", LicenseManager(context).deviceId)
            val request = Request.Builder()
                .url(BASE_URL + path)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                response.body?.string()?.let { JSONObject(it) }
            }
        } catch (e: Exception) {
            Log.e("COMMUNITY", "$path: ${e.message}")
            null
        }
}
