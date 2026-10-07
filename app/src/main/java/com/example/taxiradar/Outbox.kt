package com.example.taxiradar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Очередь отправки на сервер: начало и конец поездки сначала сохраняются на телефоне,
 * потом уходят по порядку. Нет связи — ждут и досылаются позже (при следующей поездке,
 * обновлении надбавки или открытии приложения). Сервер узнаёт повтор по client_id и не дублирует.
 */
object Outbox {
    private const val PREFS = "outbox"
    private const val KEY = "events"
    private const val MAX = 300
    private val lock = Any()
    @Volatile private var flushing = false

    private fun load(c: Context): JSONArray =
        try { JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }

    private fun save(c: Context, a: JSONArray) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).commit()
    }

    fun size(c: Context) = synchronized(lock) { load(c).length() }

    /** Сохранить событие и сразу попробовать отправить. */
    fun send(context: Context, path: String, body: JSONObject) {
        val c = context.applicationContext
        synchronized(lock) {
            val a = load(c)
            a.put(JSONObject().put("path", path).put("body", body))
            // Переполнилось (неделями без связи) — старые события выбрасываем.
            val trimmed = if (a.length() > MAX) JSONArray().apply { for (i in a.length() - MAX until a.length()) put(a.get(i)) } else a
            save(c, trimmed)
        }
        flush(c)
    }

    /** Отправить накопленное по порядку; на первом же обрыве связи — остановиться до следующего раза. */
    fun flush(context: Context) {
        val c = context.applicationContext
        if (flushing) return
        flushing = true
        Thread {
            try {
                while (true) {
                    val head = synchronized(lock) { load(c).optJSONObject(0) } ?: break
                    val body = JSONObject(head.getJSONObject("body").toString())
                    val reply = CommunityApi.postBlocking(c, head.getString("path"), body) ?: break
                    // Сервер ответил (даже отказом — например, кончилась подписка): событие обработано.
                    if (reply.has("ok") || reply.has("message")) synchronized(lock) {
                        val a = load(c)
                        if (a.length() > 0) { a.remove(0); save(c, a) }
                    } else break
                }
            } finally {
                flushing = false
            }
        }.start()
    }
}
