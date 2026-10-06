package com.example.taxiradar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build

/**
 * Короткий отчёт о геолокации для «Проверки настроек»: на мультимедиа машин
 * место бывает только у навигатора, а радару — нет. Водитель присылает фото
 * этой строки, и видно, на каком шаге теряется точка.
 */
object LocationDiag {

    @SuppressLint("MissingPermission")
    fun describe(context: Context): String {
        fun has(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val fine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = has(Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val lm = context.getSystemService(LocationManager::class.java)
        val systemOn = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm?.isLocationEnabled else null
        } catch (e: Exception) {
            null
        }
        val now = System.currentTimeMillis()
        val providers = try {
            lm?.allProviders.orEmpty().joinToString(", ") { p ->
                val on = runCatching { lm!!.isProviderEnabled(p) }.getOrDefault(false)
                val last = if (fine || coarse) runCatching { lm!!.getLastKnownLocation(p) }.getOrNull() else null
                val age = last?.let { ((now - it.time) / 60_000).toString() + "мин" } ?: "—"
                "$p:${if (on) "вкл" else "выкл"}/$age"
            }
        } catch (e: Exception) {
            "ошибка ${e.javaClass.simpleName}"
        }
        return "Android ${Build.VERSION.RELEASE} · точное=${yn(fine)} прибл=${yn(coarse)} фон=${yn(background)} · " +
                "в системе=${systemOn?.let { yn(it) } ?: "?"}\n$providers"
    }

    private fun yn(b: Boolean) = if (b) "да" else "нет"
}
