package com.example.taxiradar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Обновление прямо из приложения: скачиваем APK с сервера (/download/taxiradar.apk),
 * проверяем, что файл целый (sha256) и подписан тем же ключом, что и установленное
 * приложение, и отдаём системному установщику. Чужой или битый файл не ставим.
 */
object AppUpdater {
    fun currentVersionCode(c: Context): Int = try {
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (e: Exception) { 0 }

    /** Есть файл новее установленной версии. */
    fun available(c: Context, cfg: AppConfig = AppConfig.load(c)) =
        cfg.apkUrl.isNotBlank() && cfg.latestVersionCode > currentVersionCode(c)

    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    sealed class Result {
        class Ok(val file: File) : Result()
        class Failed(val reason: String) : Result()
    }

    suspend fun download(c: Context, cfg: AppConfig, progress: (Int) -> Unit): Result = withContext(Dispatchers.IO) {
        fun t(ru: String, ro: String) = DriverUi.t(c, ru, ro)
        val dir = File(c.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "TaxiRadar-${cfg.latestVersionName}.apk")
        try {
            client.newCall(Request.Builder().url(cfg.apkUrl).build()).execute().use { r ->
                val body = r.body
                if (!r.isSuccessful || body == null) return@withContext Result.Failed(t("Сервер не отдал файл", "Serverul nu a trimis fișierul"))
                val total = body.contentLength().takeIf { it > 0 } ?: cfg.apkSize.toLong()
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                var last = -1
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                            val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                            if (pct != last) { last = pct; withContext(Dispatchers.Main) { progress(pct) } }
                        }
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (cfg.apkSha256.isNotBlank() && !sha.equals(cfg.apkSha256, true)) {
                    file.delete(); return@withContext Result.Failed(t("Файл скачался с ошибкой — попробуйте ещё раз", "Fișierul s-a descărcat greșit — încercați din nou"))
                }
            }
            if (!sameSigner(c, file)) {
                file.delete(); return@withContext Result.Failed(t("Файл подписан не нашим ключом — не устанавливаем", "Fișierul nu e semnat de noi — nu se instalează"))
            }
            Result.Ok(file)
        } catch (e: Exception) {
            file.delete()
            Result.Failed(t("Нет связи — попробуйте ещё раз", "Fără conexiune — încercați din nou"))
        }
    }

    /** Подпись скачанного APK совпадает с подписью установленного приложения. */
    @Suppress("DEPRECATION")
    private fun sameSigner(c: Context, file: File): Boolean = try {
        val pm = c.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val mine = pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            val theirs = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            if (theirs?.packageName != c.packageName) false
            else {
                val signers = theirs.signingInfo?.apkContentsSigners
                !mine.isNullOrEmpty() && !signers.isNullOrEmpty() && signers.toSet() == mine.toSet()
            }
        } else {
            val mine = pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES).signatures
            val theirs = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES)
            theirs?.packageName == c.packageName && !mine.isNullOrEmpty() && theirs.signatures?.toSet() == mine.toSet()
        }
    } catch (e: Exception) { false }

    /** Нужно ли сначала разрешить Taxi Radar устанавливать приложения. */
    fun needsInstallPermission(c: Context) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !c.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(a: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))) } catch (_: Exception) {}
        }
    }

    fun install(a: Activity, file: File) {
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.updates", file)
        a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
