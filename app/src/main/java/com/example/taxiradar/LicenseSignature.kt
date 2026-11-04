package com.example.taxiradar

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Проверка подписи ответов сервера о лицензии (ECDSA P-256, SHA-256).
 *
 * Без подписи «доступ открыт» мог бы сказать любой подставной сервер (прокси на телефоне).
 * Подпись покрывает одноразовый nonce из нашего запроса (старый ответ повторить нельзя),
 * ID устройства, решение, срок и время сервера. Публичный ключ вшит в приложение, приватный
 * лежит только в Railway (LICENSE_SIGN_KEY).
 */
object LicenseSignature {

    private const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhXXoP006/9fneqSPspDpdtd+RYvU1pQgKxj4W2WCUcI5Pr84VVivGsQyDw+UWc2aULS/HwMkgw2F+TjSmWB88w=="

    private val random = SecureRandom()
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    /** Новый одноразовый код на каждый запрос лицензии. */
    fun newNonce(): String = (1..24).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    class Verified(val ts: Long, val expires: String)

    /**
     * kind — "radar" или "cancel"; valid — то решение, которое приложение собирается принять.
     * Возвращает время сервера и срок из подписанного ответа или null, если подпись не сошлась.
     */
    fun verify(json: JSONObject, kind: String, deviceId: String, nonce: String, valid: Boolean): Verified? {
        return try {
            if (json.optString("nonce") != nonce) return null
            val ts = json.optLong("ts", 0L)
            val sig = json.optString("sig")
            if (ts <= 0L || sig.isEmpty()) return null
            val expires = json.optString("expires", "")
            val message = listOf("v1", kind, nonce, deviceId, if (valid) 1 else 0, expires, ts).joinToString("|")
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)))
            val ok = Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(message.toByteArray(Charsets.UTF_8))
                verify(Base64.decode(sig, Base64.DEFAULT))
            }
            if (ok) Verified(ts, expires) else null
        } catch (e: Exception) {
            null
        }
    }
}
