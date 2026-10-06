package com.example.taxiradar

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Включение «Специальных возможностей» для радара. На части устройств
 * (мультимедиа BYD и другие урезанные прошивки) общий экран «Спец. возможности»
 * падает сразу при открытии — водитель возвращается в приложение и не может
 * включить службу. Тогда предлагаем обходные пути: страница самой службы,
 * все настройки или один раз через компьютер (после этого радар включает себя сам).
 */
object AccessibilityAccess {

    private const val PREFS = "taxi_radar_prefs"
    private const val KEY_OPENED_AT = "acc_settings_opened_at"

    fun component(context: Context) = ComponentName(context, OrderAccessibilityService::class.java)

    /** Разрешение выдали через компьютер — можем включить службу сами, без экрана настроек. */
    fun canEnableSelf(context: Context) =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = component(context)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Включает службу сам (нужно разрешение WRITE_SECURE_SETTINGS). */
    fun enableSelf(context: Context): Boolean = try {
        val resolver = context.contentResolver
        val me = component(context).flattenToString()
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val list = current.split(':').filter { it.isNotBlank() && it != me } + me
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Кнопка «Включить»: обычный экран настроек. Если водитель уже нажимал её
     * меньше минуты назад, а служба так и не включилась — экран, видимо, падает:
     * показываем обходные пути.
     */
    fun open(activity: Activity) {
        if (isEnabled(activity)) {
            openSystemList(activity)
            return
        }
        if (canEnableSelf(activity)) {
            val ok = enableSelf(activity)
            Toast.makeText(activity, if (ok) R.string.acc_self_enabled else R.string.acc_self_failed, Toast.LENGTH_LONG).show()
            if (ok) return
        }
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_OPENED_AT, 0L)
        // Мультимедиа BYD: экран настроек там не открывается — сразу обходные пути.
        if (isCarHeadUnit() || System.currentTimeMillis() - last < 60_000) {
            showWorkarounds(activity)
            return
        }
        prefs.edit().putLong(KEY_OPENED_AT, System.currentTimeMillis()).apply()
        openSystemList(activity)
    }

    private fun openSystemList(activity: Activity) {
        if (!start(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) openServicePage(activity)
    }

    /** Страница самой службы — на многих прошивках открывается, даже когда общий список падает. */
    private fun openServicePage(activity: Activity): Boolean {
        val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component(activity).flattenToString())
        return start(activity, intent) || start(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** Команда для компьютера: один раз выдать разрешение — дальше радар включается сам. */
    fun adbCommand(context: Context) =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun isCarHeadUnit(): Boolean {
        val b = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND + " " + android.os.Build.MODEL).lowercase()
        return b.contains("byd") || b.contains("dilink")
    }

    fun showWorkarounds(activity: Activity) {
        val items = arrayOf(
            activity.getString(R.string.acc_way_network_adb),
            activity.getString(R.string.acc_way_service_page),
            activity.getString(R.string.acc_way_all_settings),
            activity.getString(R.string.acc_way_computer)
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.acc_way_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showNetworkAdbWay(activity)
                    1 -> openServicePage(activity)
                    2 -> start(activity, Intent(Settings.ACTION_SETTINGS))
                    else -> showComputerWay(activity)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /** Без компьютера: отладка по сети на самом устройстве (как делает BYDMate). */
    private fun showNetworkAdbWay(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.acc_way_network_adb)
            .setMessage(R.string.acc_network_adb_text)
            .setPositiveButton(R.string.acc_network_adb_go) { _, _ -> runNetworkAdb(activity) }
            .setNeutralButton(R.string.acc_open_dev_options) { _, _ ->
                if (!start(activity, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))) start(activity, Intent(Settings.ACTION_SETTINGS))
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun runNetworkAdb(activity: Activity) {
        // Ключи отладки делаются средствами Android 8+; на старых — только через компьютер.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            showComputerWay(activity)
            return
        }
        Toast.makeText(activity, R.string.acc_network_adb_wait, Toast.LENGTH_LONG).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val r = AdbSelfGrant.grant(activity)
            if (activity.isFinishing) return@launch
            val msg = when (r) {
                AdbSelfGrant.Result.GRANTED ->
                    if (enableSelf(activity)) R.string.acc_self_enabled else R.string.acc_self_failed
                AdbSelfGrant.Result.NO_ADB -> R.string.acc_network_adb_off
                AdbSelfGrant.Result.NOT_ALLOWED -> R.string.acc_network_adb_denied
                AdbSelfGrant.Result.FAILED -> R.string.acc_network_adb_failed
            }
            MaterialAlertDialogBuilder(activity).setMessage(msg).setPositiveButton(R.string.got_it, null).show()
        }
    }

    private fun showComputerWay(activity: Activity) {
        val cmd = adbCommand(activity)
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.acc_way_computer)
            .setMessage(activity.getString(R.string.acc_computer_text, cmd))
            .setPositiveButton(R.string.acc_copy_command) { _, _ ->
                activity.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("adb", cmd))
                Toast.makeText(activity, R.string.acc_command_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }
}
