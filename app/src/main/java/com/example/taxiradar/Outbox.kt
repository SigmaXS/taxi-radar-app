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
    /** Один отправитель: проверка и установка — одной операцией. */
    private val flushing = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Ошибка сервера (5xx) столько раз подряд — событие выбрасываем, чтобы не заклинило очередь. */
    private const val MAX_SERVER_ERRORS = 30

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
        if (!flushing.compareAndSet(false, true)) return
        Thread {
            try {
                while (true) {
                    val head = synchronized(lock) { load(c).optJSONObject(0) } ?: break
                    val headText = head.toString()
                    val body = JSONObject(head.getJSONObject("body").toString())
                    val code = CommunityApi.postStatus(c, head.getString("path"), body) ?: break
                    // 2xx — принято; 4xx — сервер отказал (кончилась подписка, неверные данные), повтор не поможет.
                    // 5xx — сбой сервера: оставляем и пробуем позже.
                    val done = code in 200..499
                    synchronized(lock) {
                        val a = load(c)
                        // Убираем именно это событие, а не «первое, что лежит».
                        if (a.length() > 0 && a.optJSONObject(0)?.toString() == headText) {
                            if (done) a.remove(0)
                            else {
                                val tries = head.optInt("errors") + 1
                                if (tries >= MAX_SERVER_ERRORS) a.remove(0) else a.put(0, JSONObject(headText).put("errors", tries))
                            }
                            save(c, a)
                        }
                    }
                    if (!done) break
                }
            } finally {
                flushing.set(false)
            }
        }.start()
    }
}
